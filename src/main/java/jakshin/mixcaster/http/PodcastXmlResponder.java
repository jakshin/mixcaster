/*
 * Copyright (C) 2016 Jason Jackson
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

package jakshin.mixcaster.http;

import jakshin.mixcaster.dlqueue.Download;
import jakshin.mixcaster.dlqueue.DownloadQueue;
import jakshin.mixcaster.mixcloud.*;
import jakshin.mixcaster.podcast.Podcast;
import jakshin.mixcaster.podcast.PodcastEpisode;
import jakshin.mixcaster.utils.MemoryCache;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static jakshin.mixcaster.logging.Logging.*;

/**
 * Responds to an HTTP request for RSS XML.
 */
class PodcastXmlResponder extends Responder {
    /**
     * Responds to the RSS XML request.
     * Expected RSS URLs are like /username.xml (e.g. /NTSRadio.xml), /username/musicType.xml (e.g. /NTSRadio/shows.xml),
     * or /username/playlist/slug.xml (e.g. /Wicked_Glitch_Podcast/playlist/wicked-glitch-podcast-archive.xml).
     *
     * @param request The incoming HTTP request.
     * @param writer A writer which can be used to output the response.
     * @param out An output stream which can be used to output the response.
     */
    void respond(@NotNull HttpRequest request, @NotNull Writer writer, @NotNull OutputStream out)
            throws HttpException, InterruptedException, IOException, MixcloudException, TimeoutException, URISyntaxException {

        logger.log(INFO, "Responding to request for podcast: {0}", request.path);

        String path = request.path.substring(1);  // strip the leading slash, to avoid empty string in pathParts[0]
        String[] pathParts = path.split("/");  // trailing empty string not included

        if (pathParts[pathParts.length - 1].endsWith(".xml")) {
            // strip ".xml" from the end of the last path component
            String last = pathParts[pathParts.length - 1];
            pathParts[pathParts.length - 1] = last.substring(0, last.length() - 4);
        }

        MusicSet musicSet;
        try {
            musicSet = MusicSet.of(List.of(pathParts));
        }
        catch (MusicSet.InvalidInputException ex) {
            throw new PodcastHttpException("Invalid podcast URL", ex);
        }

        var client = new MixcloudClient(request.host());

        if (musicSet.musicType() == null) {
            String defaultMusicType = defaultViewCache.get(musicSet.username());

            if (defaultMusicType == null) {
                defaultMusicType = client.queryDefaultView(musicSet.username());
                defaultViewCache.put(musicSet.username(), defaultMusicType);
            }

            musicSet = new MusicSet(musicSet.username(), defaultMusicType, null);
        }

        String description = (musicSet.playlist() == null)
                ? String.format("%s's %s", musicSet.username(), musicSet.musicType())
                : String.format("%s's %s playlist", musicSet.username(), musicSet.playlist());

        logger.log(INFO, "The podcast will contain {0}", description);
        Podcast podcast = getOrMakePodcast(description, client, musicSet);

        // handle If-Modified-Since
        HttpHeaderWriter headerWriter = new HttpHeaderWriter();
        Date lastModified = new Date(podcast.createdAt / 1000 * 1000);  // truncate milliseconds for comparison

        if (headerWriter.sendNotModifiedHeadersIfNeeded(request, writer, lastModified)) {
            logger.log(INFO, "Responding with 304 for unmodified podcast: {0}", request.path);
            return;  // the request was satisfied via not-modified response headers
        }

        // kick off any downloads from Mixcloud which are now needed
        startDownloads(description, podcast, musicSet);

        // respond; note that if we send the XML body, we always send the whole thing,
        // as we don't expect to receive a Range header for this type of request
        String rssXml = podcast.createXml();
        byte[] rssXmlBytes = rssXml.getBytes(StandardCharsets.UTF_8);
        headerWriter.sendSuccessHeaders(writer, lastModified, "text/xml; charset=UTF-8", rssXmlBytes.length);

        if (request.isHead()) {
            logger.log(INFO, "Done responding to HEAD request for podcast: {0}", request.path);
            return;
        }

        out.write(rssXmlBytes);
        out.flush();
        logger.log(INFO, "Done responding to GET request for podcast: {0}", request.path);
    }

    /**
     * Gets the described podcast from our cache,
     * creating and adding it to the cache if needed.
     */
    @NotNull
    private Podcast getOrMakePodcast(@NotNull String description,
                                     @NotNull MixcloudClient client,
                                     @NotNull MusicSet musicSet)
            throws HttpException, InterruptedException, IOException, MixcloudException, TimeoutException, URISyntaxException {

        Podcast podcast = podcastCache.get(description);

        if (podcast == null) {
            try {
                podcast = client.query(musicSet);
                podcastCache.put(description, podcast);
                podcastCache.scrub();
            }
            catch (MixcloudUserException ex) {
                String msg = String.format("There's no Mixcloud user with username %s", ex.username);
                logger.log(ERROR, msg);
                throw new PodcastHttpException(msg, ex);
            }
            catch (MixcloudPlaylistException ex) {
                String msg = String.format("%s doesn't have a \"%s\" playlist", ex.username, ex.playlist);
                logger.log(ERROR, msg);
                throw new PodcastHttpException(msg, ex);
            }
        }
        else {
            logger.log(INFO, "Podcast retrieved from cache: {0}", description);
        }

        // Apple Podcasts chokes on empty podcasts, so return a 404 in that case,
        // to hopefully make the problem easier to see and understand
        if (podcast.episodes.isEmpty()) {
            String msg = String.format("%s has no %s", musicSet.username(), musicSet.musicType());
            if ("playlist".equals(musicSet.musicType()))
                msg = String.format("%s's \"%s\" playlist is empty", musicSet.username(), musicSet.playlist());
            else if ("stream".equals(musicSet.musicType()))
                msg = String.format("%s's stream is empty", musicSet.username());

            logger.log(ERROR, msg);
            throw new PodcastHttpException(msg);
        }

        return podcast;
    }

    /**
     * Queues and starts downloading any of the given podcast's music files
     * that don't yet exist locally.
     */
    private void startDownloads(@NotNull String description,
                                @NotNull Podcast podcast,
                                @NotNull MusicSet musicSet) {

        if (podcast.episodes.isEmpty()) {
            if (musicSet.playlist() != null)
                logger.log(INFO, "{0} is empty", description);
            else
                logger.log(INFO, "{0} has no {1}", new String[] { musicSet.username(), musicSet.musicType() });
            return;
        }

        DownloadQueue queue = DownloadQueue.getInstance();
        int queued = 0;

        for (PodcastEpisode episode : podcast.episodes) {
            String localPath = ServableFile.getLocalPath(episode.enclosureUrl.toString());
            Download download = new Download(episode.enclosureMixcloudUrl.toString(),
                    episode.enclosureLengthBytes, episode.enclosureLastModified, localPath);

            if (queue.enqueue(download)) {
                queued++;
            }
        }

        if (queued == 0) {
            logger.log(INFO, "We''ve already queued or downloaded {0}", description);
        }
        else {
            String filesStr = (queued == 1) ? "music file" : "music files";
            logger.log(INFO, "Downloading {0} new {1} ...", new Object[] { queued, filesStr });
            queue.processQueue(false);
        }
    }

    /** A cache of Podcast objects we've already built. */
    private static final MemoryCache<String,Podcast> podcastCache;

    /** A cache of Mixcloud users' default views. */
    private static final MemoryCache<String,String> defaultViewCache;

    static {
        int cacheTimeSeconds = Integer.parseInt(System.getProperty("http_cache_time_seconds"));
        podcastCache = new MemoryCache<>(cacheTimeSeconds);
        defaultViewCache = new MemoryCache<>(cacheTimeSeconds);
    }
}
