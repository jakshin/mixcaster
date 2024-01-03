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
import jakshin.mixcaster.fragment.Cloudcast;
import jakshin.mixcaster.http.ServableFile;
import jakshin.mixcaster.podcast.Podcast;
import jakshin.mixcaster.podcast.PodcastEpisode;
import jakshin.mixcaster.type.CloudcastOrderByEnum;
import jakshin.mixcaster.type.PlaylistLookup;
import jakshin.mixcaster.type.UserLookup;
import jakshin.mixcaster.utils.MimeHelper;
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
import java.util.concurrent.*;

import static jakshin.mixcaster.logging.Logging.*;

/**
 * Queries Mixcloud's GraphQL API.
 */
public class MixcloudClient {
    /**
     * Creates an instance, using the given host and port when creating URLs to music files.
     * @param localHostAndPort The local host and port from which music files will be served.
     */
    public MixcloudClient(@NotNull String localHostAndPort) {
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

        logger.log(INFO, "Querying {0}''s default view", username);
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
     * @param musicSet The set of music desired (Mixcloud user, music type, playlist slug if applicable).
     * @return A Podcast object containing info about the user and their music.
     */
    @NotNull
    public Podcast query(@NotNull MusicSet musicSet)
            throws InterruptedException, IOException, MixcloudException, TimeoutException, URISyntaxException {

        long started = System.nanoTime();

        String musicType = musicSet.musicType();
        if (musicType == null) musicType = "";  // nulls aren't allowed in switches

        Podcast podcast = switch (musicType) {
            case "stream" -> this.queryStream(musicSet.username());
            case "shows" -> this.queryShows(musicSet.username());
            case "history" -> this.queryHistory(musicSet.username());
            case "favorites" -> this.queryFavorites(musicSet.username());
            case "playlist" -> {
                if (musicSet.playlist() == null || musicSet.playlist().isBlank())
                    throw new IllegalArgumentException("Playlist requested but playlist name is empty");
                yield this.queryPlaylist(musicSet.username(), musicSet.playlist());
            }
            default -> throw new IllegalArgumentException("Unexpected music type: " + musicSet.musicType());
        };

        long elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000;
        String timeSpan = TimeSpanFormatter.formatTimeSpan((int) elapsedSeconds);
        logger.log(INFO, "Finished querying {0}''s {1} in {2}",
                new String[] { musicSet.username(), musicSet.musicType(), timeSpan });

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
            throws InterruptedException, IOException, MixcloudException, TimeoutException, URISyntaxException {

        Podcast podcast = getPodcastForUser(username);
        podcast.title = String.format("%s's stream", podcast.iTunesAuthorAndOwnerName);
        podcast.link = new URI(MIXCLOUD_WEB + username + "/stream/");

        logger.log(INFO, "Querying {0}''s stream", username);
        int episodeCount = 0, maxEpisodeCount = getEpisodeMaxCountSetting();
        var context = new QueryContext();
        String cursor = null;

        while (true) {
            context.description = String.format("UserStreamPageQuery for %s (cursor: %s)", username, cursor);
            var query = new UserStreamPageQuery(20, new Input<>(cursor, true), podcast.userID);

            var data = (UserStreamPageQuery.Data) runQuery(query, context.description);
            UserStreamPageQuery.User user = data.user();
            if (user == null) {
                String msg = String.format("%s received null user", context.description);
                throw new MixcloudUserException(msg, username);
            }

            var stream = user.stream();
            for (var edge : stream.edges()) {
                Cloudcast cloudcast = edge.node().fragments().cloudcast();

                if (addEpisodeToPodcast(cloudcast, podcast, context)) {
                    episodeCount++;

                    if (episodeCount >= maxEpisodeCount) {
                        logger.log(DEBUG, "{0} reached max episode count: {1}",
                                new String[]{ context.description, String.valueOf(maxEpisodeCount) });
                        break;
                    }
                }
            }

            if (episodeCount >= maxEpisodeCount) break;
            if (! stream.pageInfo().hasNextPage()) {
                logger.log(DEBUG, "{0} has no more pages", context.description);
                break;
            }

            cursor = stream.pageInfo().endCursor();
        }

        context.shutdown();
        return removeInvalidEpisodes(podcast);
    }

    /**
     * Queries the given Mixcloud user's shows (aka uploads).
     *
     * @param username The Mixcloud user whose shows/uploads are desired.
     * @return A Podcast object containing info about the user and their shows.
     */
    @NotNull
    private Podcast queryShows(@NotNull String username)
            throws InterruptedException, IOException, MixcloudException, TimeoutException, URISyntaxException {

        Podcast podcast = getPodcastForUser(username);
        podcast.title = String.format("%s's shows", podcast.iTunesAuthorAndOwnerName);
        podcast.link = new URI(MIXCLOUD_WEB + username + "/uploads/");

        logger.log(INFO, "Querying {0}''s shows", username);
        int episodeCount = 0, maxEpisodeCount = getEpisodeMaxCountSetting();
        var context = new QueryContext();
        String cursor = null;

        while (true) {
            context.description = String.format("UserUploadsPageQuery for %s (cursor: %s)", username, cursor);
            var query = new UserUploadsPageQuery(20, new Input<>(cursor, true),
                    new Input<>(CloudcastOrderByEnum.LATEST, true), podcast.userID);

            var data = (UserUploadsPageQuery.Data) runQuery(query, context.description);
            UserUploadsPageQuery.User user = data.user();
            if (user == null) {
                String msg = String.format("%s received null user", context.description);
                throw new MixcloudUserException(msg, username);
            }

            var uploads = user.uploads();
            for (var edge : uploads.edges()) {
                Cloudcast cloudcast = edge.node().fragments().cloudcast();

                if (addEpisodeToPodcast(cloudcast, podcast, context)) {
                    episodeCount++;

                    if (episodeCount >= maxEpisodeCount) {
                        logger.log(DEBUG, "{0} reached max episode count: {1}",
                                new String[]{ context.description, String.valueOf(maxEpisodeCount) });
                        break;
                    }
                }
            }

            if (episodeCount >= maxEpisodeCount) break;
            if (! uploads.pageInfo().hasNextPage()) {
                logger.log(DEBUG, "{0} has no more pages", context.description);
                break;
            }

            cursor = uploads.pageInfo().endCursor();
        }

        context.shutdown();
        return removeInvalidEpisodes(podcast);
    }

    /**
     * Queries the given Mixcloud user's favorites.
     *
     * @param username The Mixcloud user whose favorites are desired.
     * @return A Podcast object containing info about the user and their favorites.
     */
    @NotNull
    private Podcast queryFavorites(@NotNull String username)
            throws InterruptedException, IOException, MixcloudException, TimeoutException, URISyntaxException {

        Podcast podcast = getPodcastForUser(username);
        podcast.title = String.format("%s's favorites", podcast.iTunesAuthorAndOwnerName);
        podcast.link = new URI(MIXCLOUD_WEB + username + "/favorites/");

        logger.log(INFO, "Querying {0}''s favorites", username);
        int episodeCount = 0, maxEpisodeCount = getEpisodeMaxCountSetting();
        var context = new QueryContext();
        String cursor = null;

        while (true) {
            context.description = String.format("UserFavoritesPageQuery for %s (cursor: %s)", username, cursor);
            var query = new UserFavoritesPageQuery(20, new Input<>(cursor, true), podcast.userID);

            var data = (UserFavoritesPageQuery.Data) runQuery(query, context.description);
            UserFavoritesPageQuery.User user = data.user();
            if (user == null) {
                String msg = String.format("%s received null user", context.description);
                throw new MixcloudUserException(msg, username);
            }

            var favorites = user.favorites();
            for (var edge : favorites.edges()) {
                Cloudcast cloudcast = edge.node().fragments().cloudcast();

                if (addEpisodeToPodcast(cloudcast, podcast, context)) {
                    episodeCount++;

                    if (episodeCount >= maxEpisodeCount) {
                        logger.log(DEBUG, "{0} reached max episode count: {1}",
                                new String[]{ context.description, String.valueOf(maxEpisodeCount) });
                        break;
                    }
                }
            }

            if (episodeCount >= maxEpisodeCount) break;
            if (! favorites.pageInfo().hasNextPage()) {
                logger.log(DEBUG, "{0} has no more pages", context.description);
                break;
            }

            cursor = favorites.pageInfo().endCursor();
        }

        context.shutdown();
        return removeInvalidEpisodes(podcast);
    }

    /**
     * Queries the given Mixcloud user's history (aka listens).
     *
     * @param username The Mixcloud user whose history is desired.
     * @return A Podcast object containing info about the user and their history.
     */
    @NotNull
    private Podcast queryHistory(@NotNull String username)
            throws InterruptedException, IOException, MixcloudException, TimeoutException, URISyntaxException {

        Podcast podcast = getPodcastForUser(username);
        podcast.title = String.format("%s's history", podcast.iTunesAuthorAndOwnerName);
        podcast.link = new URI(MIXCLOUD_WEB + username + "/listens/");

        logger.log(INFO, "Querying {0}''s history", username);
        int episodeCount = 0, maxEpisodeCount = getEpisodeMaxCountSetting();
        var context = new QueryContext();
        String cursor = null;

        while (true) {
            context.description = String.format("UserListensPageQuery for %s (cursor: %s)", username, cursor);
            var query = new UserListensPageQuery(20, new Input<>(cursor, true), podcast.userID);

            var data = (UserListensPageQuery.Data) runQuery(query, context.description);
            UserListensPageQuery.User user = data.user();
            if (user == null) {
                String msg = String.format("%s received null user", context.description);
                throw new MixcloudUserException(msg, username);
            }

            var history = user.listeningHistory();
            for (var edge : history.edges()) {
                Cloudcast cloudcast = edge.node().cloudcast().fragments().cloudcast();

                if (addEpisodeToPodcast(cloudcast, podcast, context)) {
                    episodeCount++;

                    if (episodeCount >= maxEpisodeCount) {
                        logger.log(DEBUG, "{0} reached max episode count: {1}",
                                new String[]{ context.description, String.valueOf(maxEpisodeCount) });
                        break;
                    }
                }
            }

            if (episodeCount >= maxEpisodeCount) break;
            if (! history.pageInfo().hasNextPage()) {
                logger.log(DEBUG, "{0} has no more pages", context.description);
                break;
            }

            cursor = history.pageInfo().endCursor();
        }

        context.shutdown();
        return removeInvalidEpisodes(podcast);
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
            throws InterruptedException, IOException, MixcloudException, TimeoutException, URISyntaxException {

        logger.log(INFO, "Querying {0}''s playlist: {1}", new String[] { username, slug });

        Podcast podcast = null;
        int episodeCount = 0, maxEpisodeCount = getEpisodeMaxCountSetting();
        var context = new QueryContext();
        String cursor = null;

        while (true) {
            context.description = String.format("UserPlaylistPageQuery for %s's %s", username, slug);
            var query = new UserPlaylistPageQuery(20, new Input<>(cursor, true),
                    PlaylistLookup.builder().username(username).slug(slug).build());
            var data = (UserPlaylistPageQuery.Data) runQuery(query, context.description);

            // handle the case where the user and/or playlist doesn't exist
            var playlist = data.playlist();
            if (playlist == null) {
                boolean userExists = true;

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

            if (podcast == null) {
                // populate the podcast's properties from our first page of results
                podcast = getPodcastForPlaylist(playlist, username, slug);
            }

            for (var edge : playlist.items().edges()) {
                Cloudcast cloudcast = edge.node().cloudcast().fragments().cloudcast();

                if (addEpisodeToPodcast(cloudcast, podcast, context)) {
                    episodeCount++;

                    if (episodeCount >= maxEpisodeCount) {
                        logger.log(DEBUG, "{0} reached max episode count: {1}",
                                new String[]{ context.description, String.valueOf(maxEpisodeCount) });
                        break;
                    }
                }
            }

            if (episodeCount >= maxEpisodeCount) break;
            if (! playlist.items().pageInfo().hasNextPage()) {
                logger.log(DEBUG, "{0} has no more pages", context.description);
                break;
            }

            cursor = playlist.items().pageInfo().endCursor();
        }

        context.shutdown();
        return removeInvalidEpisodes(podcast);
    }

    /**
     * Creates a podcast object populated with the given user's properties.
     * No episodes are included.
     */
    @NotNull
    private Podcast getPodcastForUser(@NotNull String username)
            throws InterruptedException, MixcloudException, TimeoutException, URISyntaxException {

        logger.log(INFO, "Querying {0}''s info", username);
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

        String selectPrice = null;
        Boolean isSelect = user.fragments().selectUpsellButton_user().isSelect();
        if (isSelect != null && isSelect) {
            var selectUpsell = user.fragments().selectUpsellButton_user().selectUpsell();
            if (selectUpsell != null) {
                selectPrice = selectUpsell.planInfo().displayAmount();
            }
        }

        podcast.description = getPodcastDescription(user.username(), user.displayName(), selectPrice,
                                        user.city(), user.country(), user.fragments().shareUserButton_user().biog());

        podcast.iTunesAuthorAndOwnerName = user.displayName();

        UserProfileHeaderQuery.Picture pic = user.picture();
        if (pic != null) {
            String urlRoot = pic.fragments().uGCImage_picture().urlRoot();
            if (urlRoot != null && !urlRoot.isBlank())
                podcast.iTunesImageUrl = new URI(MIXCLOUD_IMAGES + urlRoot);
        }

        return podcast;
    }

    /**
     * Creates a podcast object populated with the given playlist's properties.
     * No episodes are included.
     */
    @NotNull
    private Podcast getPodcastForPlaylist(@NotNull UserPlaylistPageQuery.Playlist playlist,
                                          @NotNull String username,
                                          @NotNull String slug) throws URISyntaxException {

        var podcast = new Podcast();
        podcast.userID = playlist.owner().id();
        podcast.title = playlist.name();  // "%s's playlist: %s" might also be nice
        podcast.link = new URI(MIXCLOUD_WEB + username + "/playlists/" + slug + "/");

        podcast.description = playlist.description();
        if (podcast.description == null || podcast.description.isBlank()) {
            var owner = playlist.owner();

            String selectPrice = null;
            Boolean isSelect = owner.isSelect();
            if (isSelect != null && isSelect) {
                var selectUpsell = owner.selectUpsell();
                if (selectUpsell != null) { //NOPMD - suppressed AvoidDeeplyNestedIfStmts
                    selectPrice = selectUpsell.planInfo().displayAmount();
                }
            }

            podcast.description = getPodcastDescription(owner.username(), owner.displayName(), selectPrice,
                    owner.city(), owner.country(), owner.biog());
        }

        podcast.iTunesAuthorAndOwnerName = playlist.owner().displayName();

        String picUrlRoot = null;
        var playlistPic = playlist.picture();
        if (playlistPic != null) picUrlRoot = playlistPic.urlRoot();

        if (picUrlRoot == null || picUrlRoot.isBlank()) {
            // the playlist doesn't have a picture, fall back to the owner's
            var ownerPic = playlist.owner().picture();
            if (ownerPic != null) picUrlRoot = ownerPic.urlRoot();
        }

        podcast.iTunesImageUrl = new URI(MIXCLOUD_IMAGES + picUrlRoot);

        return podcast;
    }

    /**
     * Builds a podcast description by piecing together bits of info about a user.
     * Returns an empty string if we can't find out anything about them.
     */
    @NotNull
    private String getPodcastDescription(
            @NotNull String username,
            @NotNull String displayName,
            @Nullable String selectPrice,
            @Nullable String city,
            @Nullable String country,
            @Nullable String biog) {

        StringBuilder desc = new StringBuilder(2000);

        if (selectPrice != null && !selectPrice.isBlank() && !getSubscribedToSetting().contains(username)) {
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
    private PodcastEpisode getPodcastEpisode(
            @NotNull String name,
            @NotNull String slug,
            @NotNull String author,
            @Nullable String description,
            @Nullable Object publishDate,
            @Nullable Integer audioLength,
            @NotNull String encodedMusicUrl,
            @Nullable String pictureUrl,
            @NotNull QueryContext context) throws URISyntaxException {

        PodcastEpisode episode = new PodcastEpisode();
        episode.description = Objects.requireNonNullElse(description, "");

        String decodedUrl = new MixcloudDecoder().decodeUrl(encodedMusicUrl);
        new URI(decodedUrl);  // ensure it's a valid URI
        var mixcloudMusicUrl = new MixcloudMusicUrl(decodedUrl);
        String localUrl = mixcloudMusicUrl.localUrl(localHostAndPort, author, slug);

        var file = new ServableFile(localUrl);
        if (file.isFile()) {
            episode.enclosureLastModified = new Date(file.lastModified());
            episode.enclosureLengthBytes = file.length();
            episode.enclosureMimeType = new MimeHelper().guessContentTypeFromName(file.getName());
        }
        else {
            // any query function that calls addEpisodeToPodcast() -> getPodcastEpisode()
            // should call the context's shutdown() to shut down the thread pool
            context.getThreadPool().submit(() -> {
                try {
                    MixcloudMusicUrl.ResponseHeaders headers = mixcloudMusicUrl.getHeaders(HTTP_TIMEOUT_MILLIS);
                    episode.enclosureLastModified = new Date(headers.lastModified.getTime());
                    episode.enclosureLengthBytes = headers.contentLength;
                    episode.enclosureMimeType = headers.contentType;
                }
                catch (Exception ex) {
                    // we'll eventually ignore the busted episode via removeInvalidEpisodes()
                    logger.log(WARNING, "Skipping item: {0}{1}\t Because: {2}",
                            new String[] { name, System.lineSeparator(), ex.toString() });
                }
            });
        }

        episode.enclosureMixcloudUrl = new URI(mixcloudMusicUrl.urlStr());
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
     * Adds an episode for the given cloudcast to the given podcast.
     * Returns a boolean indicating whether an episode was added
     * (some cloudcasts can't be added, e.g. because they're not playable),
     */
    private boolean addEpisodeToPodcast(@NotNull Cloudcast cloudcast,
                                        @NotNull Podcast podcast,
                                        @NotNull QueryContext context) throws MixcloudException {

        if (Boolean.TRUE.equals(cloudcast.isExclusive())) {
            logger.log(INFO, () -> "Skipping subscriber exclusive: " + cloudcast.name());
            return false;
        }

        if (Boolean.FALSE.equals(cloudcast.isPlayable())) {
            logger.log(INFO, () -> "Skipping unplayable cloudcast: " + cloudcast.name()
                    + " (restricted because: " + cloudcast.restrictedReason() + ")");
            return false;
        }

        Cloudcast.StreamInfo streamInfo = cloudcast.streamInfo();
        if (streamInfo == null) {
            String msg = String.format("%s didn't receive streamInfo for: %s", context.description, cloudcast.name());
            throw new MixcloudException(msg);
        }

        String encodedUrl = streamInfo.url();
        if (encodedUrl == null) {
            String msg = String.format("%s received null streamInfo.url for: %s", context.description, cloudcast.name());
            throw new MixcloudException(msg);
        }

        Cloudcast.Picture pic = cloudcast.picture();
        String pictureUrl = (pic == null || pic.urlRoot() == null) ? null : MIXCLOUD_IMAGES + pic.urlRoot();

        PodcastEpisode episode;
        try {
            episode = getPodcastEpisode(
                    cloudcast.name(),
                    cloudcast.slug(),
                    cloudcast.owner().username(),
                    cloudcast.description(),
                    cloudcast.publishDate(),
                    cloudcast.audioLength(),
                    encodedUrl,
                    pictureUrl,
                    context
            );
        }
        catch (Exception ex) {
            // we don't always know here when we can't add an episode, because it's partly async,
            // but in this case whatever went wrong happened synchronously, so we can cleanly skip
            logger.log(WARNING, "Skipping item: {0}{1}\t Because: {2}",
                    new String[] { cloudcast.name(), System.lineSeparator(), ex.toString() });
            return false;
        }

        // eliminate duplicates (which can happen in listening history, not sure about elsewhere)
        for (PodcastEpisode already : podcast.episodes) {
            if (already.enclosureUrl.equals(episode.enclosureUrl)) {
                logger.log(DEBUG, () -> "Skipping duplicate history item: " + cloudcast.name());
                return false;
            }
        }

        podcast.episodes.add(episode);
        return true;
    }

    /**
     * Removes any invalid episodes from the given podcast, and returns it for convenience.
     * This is useful to bypass errors populating episodes that occur asynchronously,
     * by skipping the affected episodes, sort of (we'll end up with fewer than
     * episode_max_count episodes attached to the podcast, unlike with sync errors).
     */
    @NotNull
    private Podcast removeInvalidEpisodes(@NotNull Podcast podcast) {
        // if an episode's last-modified date isn't populated, we must've hit an error
        // while making an HTTP request to Mixcloud's media servers for that info
        podcast.episodes.removeIf(ep -> ep.enclosureLastModified == null);
        return podcast;
    }

    /**
     * Some contextual information about a query, including a user-friendly description,
     * and maybe a thread pool if we needed to make HTTP requests related to the query.
     */
    @SuppressWarnings("PMD.CommentRequired")
    private static class QueryContext {
        String description;
        ExecutorService threadPool;

        @NotNull
        synchronized ExecutorService getThreadPool() {
            if (threadPool == null)
                threadPool = Executors.newCachedThreadPool();
            return threadPool;
        }

        synchronized void shutdown() throws InterruptedException, IOException {
            if (threadPool != null) {
                threadPool.shutdown();
                if (! threadPool.awaitTermination(HTTP_TIMEOUT_MILLIS * 2, TimeUnit.MILLISECONDS)) {
                    throw new IOException("Thread pool seems hung, what's up with that?");
                }
            }
        }
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
    QT.Data runQuery(@NotNull QT query, @NotNull String queryDescription)
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
    private int getEpisodeMaxCountSetting() {
        String countStr = System.getProperty("episode_max_count");
        return Integer.parseInt(countStr);  // already validated
    }

    /**
     * Gets the subscribed_to configuration setting.
     * @return subscribed_to, as a list of username strings.
     */
    @NotNull
    private List<String> getSubscribedToSetting() {
        String[] subscribedTo = System.getProperty("subscribed_to").split("\s+");
        return Arrays.asList(subscribedTo);
    }

    /** The local host and port from which music files will be served. */
    @NotNull
    private final String localHostAndPort;

    /** Reusing a single ApolloClient instance allows us to reuse the underlying OkHttp instance
        and the associated thread pools and connections. */
    private final ApolloClient apolloClient = ApolloClient.builder().serverUrl("https://app.mixcloud.com/graphql").build();

    /**
     * Timeout for HTTP connects and reads.
     * Not explicitly applied to our Apollo client or its OkHttp instance, but it matches OkHttp's defaults.
     */
    private static final int HTTP_TIMEOUT_MILLIS = 10_000;

    /** Mixcloud's website URL. */
    private static final String MIXCLOUD_WEB = "https://www.mixcloud.com/";

    /** Mixcloud's image server. The size can be adjusted or omitted. */
    private static final String MIXCLOUD_IMAGES = "https://thumbnailer.mixcloud.com/unsafe/1400x1400/";
}
