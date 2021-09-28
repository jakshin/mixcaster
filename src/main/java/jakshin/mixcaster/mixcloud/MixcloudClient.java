/*
 * Copyright (c) 2021 Jason Jackson
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package jakshin.mixcaster.mixcloud;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Input;
import com.apollographql.apollo.api.Query;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import jakshin.mixcaster.*;
import jakshin.mixcaster.podcast.Podcast;
import jakshin.mixcaster.podcast.PodcastEpisode;
import jakshin.mixcaster.type.CloudcastOrderByEnum;
import jakshin.mixcaster.type.PlaylistLookup;
import jakshin.mixcaster.type.UserLookup;
import jakshin.mixcaster.utils.FileLocator;
import jakshin.mixcaster.utils.TimeSpanFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.*;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static jakshin.mixcaster.logging.Logging.*;

/**
 * Queries Mixcloud's GraphQL API.
 */
public class MixcloudClient {
    /**
     * Creates an instance, using configured values for host and port
     * when creating URLs to music files (defaulting to localhost:6499).
     */
    public MixcloudClient() {
        this(null);
    }

    /**
     * Creates an instance, using the given host and port when creating URLs to music files.
     * @param localHostAndPort The local host and port from which music files will be served.
     */
    public MixcloudClient(@Nullable String localHostAndPort) {
        this.localHostAndPort = localHostAndPort;
    }

    /**
     * Mixcloud users can apparently choose whether their Stream or Shows should be their default view.
     * This finds out the given user's choice, returning "shows" if the user doesn't exist.
     *
     * @param username The Mixcloud user whose default view we want to find out.
     * @return The default view: stream, shows, history, or favorites.
     */
    @NotNull
    public String queryDefaultView(@NotNull String username)
            throws InterruptedException, MixcloudException, TimeoutException {

        logger.log(INFO, "Querying user''s default view: {0}", username);
        var query = new UserDefaultQuery(UserLookup.builder().username(username).build());
        var queryDescription = String.format("UserDefaultQuery for %s", username);
        var data = (UserDefaultQuery.Data) runQuery(query, queryDescription);
        UserDefaultQuery.User user = data.user();

        if (user != null) {
            var defaultView = user.profileNavigation().defaultView();

            if (defaultView != null) {
                String typename = defaultView.__typename();

                return switch (typename) {
                    case "StreamView" -> "stream";
                    case "ListeningHistoryView" -> "history";
                    case "FavoritesView" -> "favorites";
                    default -> "shows";
                };
            }
        }

        return "shows";
    }

    /**
     * Queries the given user's stream, shows, history, favorites, or playlist.
     *
     * @param username The Mixcloud user whose music is desired.
     * @param musicType The type of music desired, i.e. which list to look in.
     * @param playlist The desired playlist's slug; ignored unless musicType is "playlist".
     * @return A Podcast object containing info about the user and their music.
     */
    @NotNull
    public Podcast query(@NotNull String username, @NotNull String musicType, @Nullable String playlist)
            throws InterruptedException, MixcloudException, TimeoutException, URISyntaxException {

        long started = System.nanoTime();
        logger.log(INFO, musicType.equals("playlist")
                ? String.format("Querying %s's playlist: %s", username, playlist)
                : String.format("Querying user's %s: %s", musicType, username));

        Podcast podcast = switch (musicType) {
            case "stream" -> this.queryStream(username);
            case "shows" -> this.queryShows(username);
            case "history" -> this.queryHistory(username);
            case "favorites" -> this.queryFavorites(username);
            case "playlist" -> {
                if (playlist == null || playlist.isBlank())
                    throw new IllegalArgumentException("Playlist requested but playlist name is empty");
                yield this.queryPlaylist(username, playlist);
            }
            default -> throw new IllegalArgumentException("Unexpected music type: " + musicType);
        };

        // we don't try to handle system clock changes or DST entry/exit here
        long elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000;
        String timeSpan = TimeSpanFormatter.formatTimeSpan((int) elapsedSeconds);
        logger.log(INFO, "Finished querying {0}''s {1} in {2}", new String[] {username, musicType, timeSpan});

        return podcast;
    }

    /**
     * Queries the given Mixcloud user's stream.
     *
     * @param username The Mixcloud user whose stream is desired.
     * @return A Podcast object containing info about the user and their stream.
     */
    @NotNull
    private Podcast queryStream(@NotNull String username)
            throws InterruptedException, MixcloudException, TimeoutException, URISyntaxException {

        Podcast podcast = queryUserInfo(username);
        podcast.title = String.format("%s's stream", podcast.iTunesAuthor);
        podcast.link = new URI(MIXCLOUD_WEB + username + "/stream/");

        int episodeCount = 0, maxEpisodeCount = getEpisodeMaxCountConfig();
        String cursor = null;

        while (true) {
            var queryDescription = String.format("UserStreamPageQuery for %s (cursor: %s)", username, cursor);
            var query = new UserStreamPageQuery(20, new Input<>(cursor, true), podcast.userID);

            var data = (UserStreamPageQuery.Data) runQuery(query, queryDescription);
            UserStreamPageQuery.User user = data.user();
            if (user == null) {
                String msg = String.format("%s received null user", queryDescription);
                throw new MixcloudUserException(msg, username);
            }

            var stream = user.fragments().userStreamPage_user_1G22uz().stream();
            for (var edge : stream.edges()) {
                var item = edge.node();

                if (Boolean.TRUE.equals(item.isExclusive())) {
                    logger.log(INFO, () -> "Skipping subscriber exclusive: " + item.name());
                    continue;
                }

                var streamInfo = item.streamInfo();
                if (streamInfo == null) {
                    String msg = String.format("%s didn't receive streamInfo for: %s", queryDescription, item.name());
                    throw new MixcloudException(msg);
                }

                String encodedUrl = streamInfo.url();
                if (encodedUrl == null) {
                    String msg = String.format("%s received null streamInfo.url for: %s", queryDescription, item.name());
                    throw new MixcloudException(msg);
                }

                var pic = item.picture();
                String pictureUrl = (pic == null || pic.urlRoot() == null) ? null : MIXCLOUD_IMAGES + pic.urlRoot();

                try {
                    podcast.episodes.add(
                            createPodcastEpisode(
                                    item.name(),
                                    item.slug(),
                                    item.owner().username(),
                                    item.description(),
                                    item.publishDate(),
                                    item.audioLength(),
                                    encodedUrl,
                                    pictureUrl
                            )
                    );
                }
                catch (Exception ex) {
                    logger.log(WARNING, "Skipping item: {0}{1}\t Because: {2}",
                            new String[] { item.name(), System.lineSeparator(), ex.toString() });
                    continue;
                }

                episodeCount++;
                if (episodeCount >= maxEpisodeCount) {
                    logger.log(DEBUG, "{0} reached max episode count: {1}",
                            new String[] { queryDescription, String.valueOf(maxEpisodeCount) });
                    break;
                }
            }

            if (episodeCount >= maxEpisodeCount) break;
            if (! stream.pageInfo().hasNextPage()) {
                logger.log(DEBUG, "{0} has no more pages", queryDescription);
                break;
            }

            cursor = stream.pageInfo().endCursor();
        }

        return podcast;
    }

    /**
     * Queries the given Mixcloud user's shows (aka uploads).
     *
     * @param username The Mixcloud user whose shows/uploads are desired.
     * @return A Podcast object containing info about the user and their shows.
     */
    @NotNull
    private Podcast queryShows(@NotNull String username)
            throws InterruptedException, MixcloudException, TimeoutException, URISyntaxException {

        Podcast podcast = queryUserInfo(username);
        podcast.title = String.format("%s's shows", podcast.iTunesAuthor);
        podcast.link = new URI(MIXCLOUD_WEB + username + "/uploads/");

        int episodeCount = 0, maxEpisodeCount = getEpisodeMaxCountConfig();
        String cursor = null;

        while (true) {
            var queryDescription = String.format("UserUploadsPageQuery for %s (cursor: %s)", username, cursor);
            var query = new UserUploadsPageQuery(20, new Input<>(cursor, true),
                    new Input<>(CloudcastOrderByEnum.LATEST, true), podcast.userID);

            var data = (UserUploadsPageQuery.Data) runQuery(query, queryDescription);
            UserUploadsPageQuery.User user = data.user();
            if (user == null) {
                String msg = String.format("%s received null user", queryDescription);
                throw new MixcloudUserException(msg, username);
            }

            var uploads = user.fragments().userUploadsPage_user_32czeo().uploads();
            for (var edge : uploads.edges()) {
                var item = edge.node();

                if (Boolean.TRUE.equals(item.isExclusive())) {
                    logger.log(INFO, () -> "Skipping subscriber exclusive: " + item.name());
                    continue;
                }

                var streamInfo = item.streamInfo();
                if (streamInfo == null) {
                    String msg = String.format("%s didn't receive streamInfo for: %s", queryDescription, item.name());
                    throw new MixcloudException(msg);
                }

                String encodedUrl = streamInfo.url();
                if (encodedUrl == null) {
                    String msg = String.format("%s received null streamInfo.url for: %s", queryDescription, item.name());
                    throw new MixcloudException(msg);
                }

                var pic = item.picture();
                String pictureUrl = (pic == null || pic.urlRoot() == null) ? null : MIXCLOUD_IMAGES + pic.urlRoot();

                try {
                    podcast.episodes.add(
                            createPodcastEpisode(
                                    item.name(),
                                    item.slug(),
                                    item.owner().username(),
                                    item.description(),
                                    item.publishDate(),
                                    item.audioLength(),
                                    encodedUrl,
                                    pictureUrl
                            )
                    );
                }
                catch (Exception ex) {
                    logger.log(WARNING, "Skipping item: {0}{1}\t Because: {2}",
                            new String[] { item.name(), System.lineSeparator(), ex.toString() });
                    continue;
                }

                episodeCount++;
                if (episodeCount >= maxEpisodeCount) {
                    logger.log(DEBUG, "{0} reached max episode count: {1}",
                            new String[] { queryDescription, String.valueOf(maxEpisodeCount) });
                    break;
                }
            }

            if (episodeCount >= maxEpisodeCount) break;
            if (! uploads.pageInfo().hasNextPage()) {
                logger.log(DEBUG, "{0} has no more pages", queryDescription);
                break;
            }

            cursor = uploads.pageInfo().endCursor();
        }

        return podcast;
    }

    /**
     * Queries the given Mixcloud user's favorites.
     *
     * @param username The Mixcloud user whose favorites are desired.
     * @return A Podcast object containing info about the user and their favorites.
     */
    @NotNull
    private Podcast queryFavorites(@NotNull String username)
            throws InterruptedException, MixcloudException, TimeoutException, URISyntaxException {

        Podcast podcast = queryUserInfo(username);
        podcast.title = String.format("%s's favorites", podcast.iTunesAuthor);
        podcast.link = new URI(MIXCLOUD_WEB + username + "/favorites/");

        int episodeCount = 0, maxEpisodeCount = getEpisodeMaxCountConfig();
        String cursor = null;

        while (true) {
            var queryDescription = String.format("UserFavoritesPageQuery for %s (cursor: %s)", username, cursor);
            var query = new UserFavoritesPageQuery(20, new Input<>(cursor, true), podcast.userID);

            var data = (UserFavoritesPageQuery.Data) runQuery(query, queryDescription);
            UserFavoritesPageQuery.User user = data.user();
            if (user == null) {
                String msg = String.format("%s received null user", queryDescription);
                throw new MixcloudUserException(msg, username);
            }

            var favorites = user.fragments().userFavoritesPage_user_1G22uz().favorites();
            for (var edge : favorites.edges()) {
                var item = edge.node();

                if (Boolean.TRUE.equals(item.isExclusive())) {
                    logger.log(INFO, () -> "Skipping subscriber exclusive: " + item.name());
                    continue;
                }

                var streamInfo = item.streamInfo();
                if (streamInfo == null) {
                    String msg = String.format("%s didn't receive streamInfo for: %s", queryDescription, item.name());
                    throw new MixcloudException(msg);
                }

                String encodedUrl = streamInfo.url();
                if (encodedUrl == null) {
                    String msg = String.format("%s received null streamInfo.url for: %s", queryDescription, item.name());
                    throw new MixcloudException(msg);
                }

                var pic = item.picture();
                String pictureUrl = (pic == null || pic.urlRoot() == null) ? null : MIXCLOUD_IMAGES + pic.urlRoot();

                try {
                    podcast.episodes.add(
                            createPodcastEpisode(
                                    item.name(),
                                    item.slug(),
                                    item.owner().username(),
                                    item.description(),
                                    item.publishDate(),
                                    item.audioLength(),
                                    encodedUrl,
                                    pictureUrl
                            )
                    );
                }
                catch (Exception ex) {
                    logger.log(WARNING, "Skipping item: {0}{1}\t Because: {2}",
                            new String[] { item.name(), System.lineSeparator(), ex.toString() });
                    continue;
                }

                episodeCount++;
                if (episodeCount >= maxEpisodeCount) {
                    logger.log(DEBUG, "{0} reached max episode count: {1}",
                            new String[] { queryDescription, String.valueOf(maxEpisodeCount) });
                    break;
                }
            }

            if (episodeCount >= maxEpisodeCount) break;
            if (! favorites.pageInfo().hasNextPage()) {
                logger.log(DEBUG, "{0} has no more pages", queryDescription);
                break;
            }

            cursor = favorites.pageInfo().endCursor();
        }

        return podcast;
    }

    /**
     * Queries the given Mixcloud user's history (aka listens).
     *
     * @param username The Mixcloud user whose history is desired.
     * @return A Podcast object containing info about the user and their history.
     */
    @NotNull
    private Podcast queryHistory(@NotNull String username)
            throws InterruptedException, MixcloudException, TimeoutException, URISyntaxException {

        Podcast podcast = queryUserInfo(username);
        podcast.title = String.format("%s's history", podcast.iTunesAuthor);
        podcast.link = new URI(MIXCLOUD_WEB + username + "/listens/");

        int episodeCount = 0, maxEpisodeCount = getEpisodeMaxCountConfig();
        String cursor = null;

        while (true) {
            var queryDescription = String.format("UserListensPageQuery for %s (cursor: %s)", username, cursor);
            var query = new UserListensPageQuery(20, new Input<>(cursor, true), podcast.userID);

            var data = (UserListensPageQuery.Data) runQuery(query, queryDescription);
            UserListensPageQuery.User user = data.user();
            if (user == null) {
                String msg = String.format("%s received null user", queryDescription);
                throw new MixcloudUserException(msg, username);
            }

            var history = user.fragments().userListensPage_user_1G22uz().listeningHistory();
            for (var edge : history.edges()) {
                var item = edge.node().cloudcast();

                if (Boolean.TRUE.equals(item.isExclusive())) {
                    logger.log(INFO, () -> "Skipping subscriber exclusive: " + item.name());
                    continue;
                }

                var streamInfo = item.streamInfo();
                if (streamInfo == null) {
                    String msg = String.format("%s didn't receive streamInfo for: %s", queryDescription, item.name());
                    throw new MixcloudException(msg);
                }

                String encodedUrl = streamInfo.url();
                if (encodedUrl == null) {
                    String msg = String.format("%s received null streamInfo.url for: %s", queryDescription, item.name());
                    throw new MixcloudException(msg);
                }

                var pic = item.picture();
                String pictureUrl = (pic == null || pic.urlRoot() == null) ? null : MIXCLOUD_IMAGES + pic.urlRoot();

                PodcastEpisode episode;
                try {
                    episode = createPodcastEpisode(
                            item.name(),
                            item.slug(),
                            item.owner().username(),
                            item.description(),
                            item.publishDate(),
                            item.audioLength(),
                            encodedUrl,
                            pictureUrl
                    );
                }
                catch (Exception ex) {
                    logger.log(WARNING, "Skipping item: {0}{1}\t Because: {2}",
                            new String[] { item.name(), System.lineSeparator(), ex.toString() });
                    continue;
                }

                // history can contain duplicates, which we eliminate
                boolean dupe = false;
                for (PodcastEpisode ep : podcast.episodes) {
                    if (ep.enclosureUrl.equals(episode.enclosureUrl)) {
                        logger.log(DEBUG, () -> "Skipping duplicate history item: " + item.name());
                        dupe = true;
                        break;
                    }
                }

                if (dupe) continue;
                podcast.episodes.add(episode);

                episodeCount++;
                if (episodeCount >= maxEpisodeCount) {
                    logger.log(DEBUG, "{0} reached max episode count: {1}",
                            new String[] { queryDescription, String.valueOf(maxEpisodeCount) });
                    break;
                }
            }

            if (episodeCount >= maxEpisodeCount) break;
            if (! history.pageInfo().hasNextPage()) {
                logger.log(DEBUG, "{0} has no more pages", queryDescription);
                break;
            }

            cursor = history.pageInfo().endCursor();
        }

        return podcast;
    }

    /**
     * Queries the given Mixcloud user's playlist of the given name.
     *
     * @param username The Mixcloud user whose playlist is desired.
     * @param slug The "slug" of the desired playlist, i.e. the last path element of the URL
     *                 when viewing the playlist's page in a browser.
     * @return A Podcast object containing info about the user and their playlist.
     */
    @NotNull
    private Podcast queryPlaylist(@NotNull String username, @NotNull String slug)
            throws InterruptedException, MixcloudException, TimeoutException, URISyntaxException {

        var podcast = new Podcast();
        int episodeCount = 0, maxEpisodeCount = getEpisodeMaxCountConfig();
        String cursor = null;

        while (true) {
            var query = new UserPlaylistPageQuery(20, new Input<>(cursor, true),
                    PlaylistLookup.builder().username(username).slug(slug).build());
            var queryDescription = String.format("UserPlaylistPageQuery for %s's %s", username, slug);
            var data = (UserPlaylistPageQuery.Data) runQuery(query, queryDescription);

            // handle the case where the user and/or playlist doesn't exist
            var fragmentHolder = data.playlist();
            if (fragmentHolder == null) {
                boolean userExists = false;

                try {
                    var userQuery = new UserDefaultQuery(UserLookup.builder().username(username).build());
                    var userQueryDescription = String.format("UserDefaultQuery for %s", username);
                    var userData = (UserDefaultQuery.Data) runQuery(userQuery, userQueryDescription);
                    userExists = (userData.user() != null);
                }
                catch (Exception ignored) {
                    // bummer, but carry on reporting the playlist as non-existent
                }

                if (! userExists) {
                    throw new MixcloudUserException("Mixcloud user not found", username);
                }
                else {
                    String msg = "Mixcloud playlist not found";
                    throw new MixcloudPlaylistException(msg, username, slug);
                }
            }

            // the actual playlist object we want is in a fragment
            var playlist = fragmentHolder.fragments().userPlaylistPage_playlist_1G22uz();

            if (podcast.userID == null) {
                // populate the podcast's properties on our first time through the loop
                podcast.userID = playlist.owner().id();
                podcast.title = playlist.name();  // "%s's playlist: %s" might also be nice
                podcast.link = new URI(MIXCLOUD_WEB + username + "/playlists/" + slug + "/");
                podcast.language = "en_US";

                podcast.description = playlist.description();
                if (podcast.description == null || podcast.description.isBlank()) {
                    var owner = playlist.owner();

                    String selectPrice = null;
                    Boolean isSelect = owner.isSelect();
                    if (isSelect != null && isSelect) {
                        var selectUpsell = owner.selectUpsell();
                        if (selectUpsell != null) {
                            selectPrice = selectUpsell.planInfo().displayAmount();
                        }
                    }

                    podcast.description = buildPodcastDescription(owner.username(), owner.displayName(), selectPrice,
                                                                    owner.city(), owner.country(), owner.biog());
                }

                podcast.iTunesAuthor = playlist.owner().displayName();
                podcast.iTunesCategory = "Music";
                podcast.iTunesExplicit = false;

                String picUrlRoot = null;
                var playlistPic = playlist.picture();
                if (playlistPic != null) picUrlRoot = playlistPic.urlRoot();

                if (picUrlRoot == null || picUrlRoot.isBlank()) {
                    // the playlist doesn't have a picture, fall back to the owner's
                    var ownerPic = playlist.owner().picture();
                    if (ownerPic != null) picUrlRoot = ownerPic.urlRoot();
                }

                podcast.iTunesImageUrl = new URI(MIXCLOUD_IMAGES + picUrlRoot);
                podcast.iTunesOwnerName = playlist.owner().displayName();
                podcast.iTunesOwnerEmail = "nobody@example.com";
            }

            for (var edge : playlist.items().edges()) {
                var item = edge.node().cloudcast();

                if (Boolean.TRUE.equals(item.isExclusive())) {
                    logger.log(INFO, () -> "Skipping subscriber exclusive: " + item.name());
                    continue;
                }

                var streamInfo = item.streamInfo();
                if (streamInfo == null) {
                    String msg = String.format("%s didn't receive streamInfo for: %s", queryDescription, item.name());
                    throw new MixcloudException(msg);
                }

                String encodedUrl = streamInfo.url();
                if (encodedUrl == null) {
                    String msg = String.format("%s received null streamInfo.url for: %s", queryDescription, item.name());
                    throw new MixcloudException(msg);
                }

                var pic = item.picture();
                String pictureUrl = (pic == null || pic.urlRoot() == null) ? null : MIXCLOUD_IMAGES + pic.urlRoot();

                try {
                    podcast.episodes.add(
                            createPodcastEpisode(
                                    item.name(),
                                    item.slug(),
                                    item.owner().username(),
                                    item.description(),
                                    item.publishDate(),
                                    item.audioLength(),
                                    encodedUrl,
                                    pictureUrl
                            )
                    );
                }
                catch (Exception ex) {
                    logger.log(WARNING, "Skipping item: {0}{1}\t Because: {2}",
                            new String[] { item.name(), System.lineSeparator(), ex.toString() });
                    continue;
                }

                episodeCount++;
                if (episodeCount >= maxEpisodeCount) {
                    logger.log(DEBUG, "{0} reached max episode count: {1}",
                            new String[] { queryDescription, String.valueOf(maxEpisodeCount) });
                    break;
                }
            }

            if (episodeCount >= maxEpisodeCount) break;
            if (! playlist.items().pageInfo().hasNextPage()) {
                logger.log(DEBUG, "{0} has no more pages", queryDescription);
                break;
            }

            cursor = playlist.items().pageInfo().endCursor();
        }

        return podcast;
    }

    /**
     * Queries the given Mixcloud user.
     *
     * @param username The Mixcloud user whose info is desired, e.g. "NTSRadio".
     * @return A Podcast object containing the user's info, but no episodes.
     */
    @NotNull
    private Podcast queryUserInfo(@NotNull String username)
            throws InterruptedException, MixcloudException, TimeoutException, URISyntaxException {

        logger.log(INFO, "Querying user''s info: {0}", username);
        var query = new UserProfileHeaderQuery(UserLookup.builder().username(username).build());
        var queryDescription = String.format("UserProfileHeaderQuery for %s", username);
        var data = (UserProfileHeaderQuery.Data) runQuery(query, queryDescription);

        UserProfileHeaderQuery.User user = data.user();
        if (user == null) {
            String msg = String.format("%s received null user", queryDescription);
            throw new MixcloudUserException(msg, username);  // the user doesn't exist
        }

        var podcast = new Podcast();
        podcast.userID = user.id();
        podcast.title = user.displayName();
        podcast.link = new URI(MIXCLOUD_WEB + username + "/");
        podcast.language = "en_US";

        String selectPrice = null;
        Boolean isSelect = user.fragments().selectUpsellButton_user().isSelect();
        if (isSelect != null && isSelect) {
            var selectUpsell = user.fragments().selectUpsellButton_user().selectUpsell();
            if (selectUpsell != null) {
                selectPrice = selectUpsell.planInfo().displayAmount();
            }
        }

        podcast.description = buildPodcastDescription(user.username(), user.displayName(), selectPrice,
                                        user.city(), user.country(), user.fragments().shareUserButton_user().biog());

        podcast.iTunesAuthor = user.displayName();
        podcast.iTunesCategory = "Music";
        podcast.iTunesExplicit = false;

        UserProfileHeaderQuery.Picture pic = user.picture();
        if (pic != null) {
            String urlRoot = pic.fragments().uGCImage_picture().urlRoot();
            if (urlRoot != null && !urlRoot.isBlank())
                podcast.iTunesImageUrl = new URI(MIXCLOUD_IMAGES + urlRoot);
        }

        podcast.iTunesOwnerName = user.displayName();
        podcast.iTunesOwnerEmail = "nobody@example.com";
        return podcast;
    }

    /**
     * Builds a podcast description by piecing together bits of info about a user.
     * Returns an empty string if we can't find out anything about them.
     */
    @NotNull
    private String buildPodcastDescription(
            @NotNull String username,
            @NotNull String displayName,
            @Nullable String selectPrice,
            @Nullable String city,
            @Nullable String country,
            @Nullable String biog) {

        StringBuilder desc = new StringBuilder(2000);

        if (selectPrice != null && !selectPrice.isBlank() && !getSubscribedToConfig().contains(username)) {
            desc.append(String.format("\uD83E\uDD29 Support %s! Subscribe for %s/month", displayName, selectPrice));
        }

        String location = country;
        if (location != null && !location.isBlank()) {
            if (city != null && !city.isBlank()) location = city + ", " + location;

            if (desc.length() > 0) desc.append('\n');
            desc.append("\uD83C\uDF0E ");
            desc.append(location);
        }

        if (biog != null && !biog.isBlank()) {
            if (desc.length() > 0) desc.append('\n');
            desc.append(biog.trim());
        }

        return desc.toString();
    }

    /**
     * Creates a populated podcast episode, given the information needed to do so.
     */
    @NotNull
    private PodcastEpisode createPodcastEpisode(
            @NotNull String name,
            @NotNull String slug,
            @NotNull String author,
            @Nullable String description,
            @Nullable Object publishDate,
            @Nullable Integer audioLength,
            @NotNull String encodedMusicUrl,
            @Nullable String pictureUrl) throws IOException, MixcloudException, URISyntaxException {

        PodcastEpisode episode = new PodcastEpisode();
        episode.description = Objects.requireNonNullElse(description, "");

        String mixcloudUrl = new MixcloudDecoder().decodeUrl(encodedMusicUrl);
        String localUrl = FileLocator.makeLocalUrl(localHostAndPort, author, slug, mixcloudUrl);
        // String localPath = FileLocator.getLocalPath(localUrl);

        ResponseHeaders headers = getMusicUrlHeaders(mixcloudUrl);
        episode.enclosureLastModified = new Date(headers.lastModified.getTime());
        episode.enclosureLengthBytes = headers.contentLength;
        episode.enclosureMimeType = headers.contentType;

        episode.enclosureMixcloudUrl = new URI(mixcloudUrl);
        episode.enclosureUrl = new URI(localUrl);
        episode.link = new URI(MIXCLOUD_WEB + author + "/" + slug);
        if (publishDate != null)
            episode.pubDate = Date.from(ZonedDateTime.parse(publishDate.toString()).toInstant());
        episode.title = name;

        episode.iTunesAuthor = author;
        episode.iTunesDuration = audioLength;
        if (pictureUrl != null && !pictureUrl.isBlank())
            episode.iTunesImageUrl = new URI(pictureUrl);

        return episode;
    }

    /**
     * Runs the given GraphQL query and returns its data.
     * Throws on any problem, including if the response has errors or its data is null.
     *
     * @param query The query to run.
     * @param queryDescription A description of the query, for logging.
     * @return The data returned for the query (as an instance of a query-specific auto-generated class).
     */
    @NotNull
    @SuppressWarnings({"rawtypes", "unchecked"})
    private <QT extends Query>
    QT.Data runQuery(QT query, String queryDescription)
            throws InterruptedException, MixcloudException, TimeoutException {

        // a synchronous API would be better for our purposes, but Apollo doesn't offer one,
        // so we poll a thread-safe object in this thread, waiting for async results from another thread
        var queue = new LinkedBlockingQueue<QueryResult<Response<QT.Data>, ApolloException>>();

        logger.log(DEBUG, "Enqueueing query: {0}", queryDescription);
        apolloClient.query(query).enqueue(new ApolloCall.Callback<QT.Data>() {
            // these callback methods get invoked on a different thread than runQuery() is running in

            @Override
            public void onResponse(@NotNull Response response) {
                logger.log(DEBUG, "{0} got a response", queryDescription);
                if (response.hasErrors()) {
                    logger.log(DEBUG, "{0} has errors", queryDescription);
                }

                var result = new QueryResult<Response<QT.Data>, ApolloException>(response, null);
                queue.offer(result);
            }

            @Override
            public void onFailure(@NotNull ApolloException ex) {
                // request parsing failed, request cancelled, network error, etc.
                logger.log(DEBUG, "{0} failed: {1}", new String[] { queryDescription, ex.getMessage() });
                var result = new QueryResult<Response<QT.Data>, ApolloException>(null, ex);
                queue.offer(result);
            }
        });

        QueryResult<Response<QT.Data>, ApolloException> result = queue.poll(30, TimeUnit.SECONDS);
        if (result == null) {
            String msg = String.format("No response to %s within 30 seconds", queryDescription);
            throw new TimeoutException(msg);
        }
        else if (result.exception != null) {
            throw result.exception;
        }

        List<com.apollographql.apollo.api.Error> errors = result.response.getErrors();
        if (errors != null && !errors.isEmpty()) {
            String msg = String.format("Error from %s: %s", queryDescription, errors.get(0).getMessage());
            throw new MixcloudException(msg);
        }

        QT.Data data = result.response.getData();
        if (data == null) {
            String msg = String.format("Null data from %s", queryDescription);
            throw new MixcloudException(msg);
        }

        return data;
    }

    /** A simple pair/2-tuple container for the two things than can result from a query. */
    @SuppressWarnings("rawtypes")
    private record QueryResult<R extends Response, E extends Exception>(R response, E exception) {}

    /**
     * Gets the episode_max_count configuration setting.
     * @return episode_max_count, converted to an int.
     */
    private int getEpisodeMaxCountConfig() {
        String countStr = Main.config.getProperty("episode_max_count");
        return Integer.parseInt(countStr);  // already validated
    }

    /**
     * Gets the subscribed_to configuration setting.
     * @return subscribed_to, as a list of username strings.
     */
    @NotNull
    private List<String> getSubscribedToConfig() {
        String[] subscribedTo = Main.config.getProperty("subscribed_to").split("\s+");
        return Arrays.asList(subscribedTo);
    }

    /**
     * Gets some HTTP response headers from a music URL, using a HEAD request.
     *
     * @param urlStr The music URL. Should not be or need to be URL-encoded.
     * @return Some HTTP response headers from the URL.
     */
    @NotNull
    private ResponseHeaders getMusicUrlHeaders(@NotNull String urlStr) throws IOException, MixcloudException {
        HttpURLConnection conn = null;

        try {
            logger.log(DEBUG, "Getting HEAD of URL: {0}", urlStr);

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("User-Agent", Main.config.getProperty("user_agent"));
            conn.setRequestProperty("Referer", urlStr);
            conn.connect();

            String contentType = conn.getContentType();
            if (!contentType.startsWith("audio/") && !contentType.startsWith("video/")) {
                String msg = String.format("Unexpected Content-Type header: %s", contentType);
                throw new MixcloudException(msg, urlStr);
            }

            long length = conn.getContentLengthLong();
            if (length < 0) {
                throw new MixcloudException("The content length is not known", urlStr);
            }

            long lastModified = conn.getLastModified();
            if (lastModified == 0) {
                throw new MixcloudException("The last-modified date/time is not known", urlStr);
            }

            var headers = new ResponseHeaders();
            headers.contentType = contentType;
            headers.contentLength = length;
            headers.lastModified = new Date(lastModified);
            return headers;
        }
        finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** A container for a few HTTP response headers. */
    @SuppressWarnings("PMD.CommentRequired")
    private static class ResponseHeaders {
        long contentLength;
        String contentType;
        Date lastModified;
    }

    /**
     * The local host and port from which music files will be served.
     * Configured values are used if no value is specified here.
     */
    @Nullable
    private final String localHostAndPort;

    /** Reusing a single ApolloClient instance allows us to reuse the underlying OkHttp instance
        and the associated thread pools and connections. */
    private final ApolloClient apolloClient = ApolloClient.builder().serverUrl("https://www.mixcloud.com/graphql").build();

    /** Mixcloud's website URL. */
    private static final String MIXCLOUD_WEB = "https://www.mixcloud.com/";

    /** Mixcloud's image server. The size can be adjusted or omitted. */
    private static final String MIXCLOUD_IMAGES = "https://thumbnailer.mixcloud.com/unsafe/1400x1400/";
}
