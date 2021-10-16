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

        String path = request.path.substring(1);  // strip the leading slash, to avoid empty string in components[0]
        String[] components = path.split("/");  // trailing empty string not included

        // strip ".xml" from the end of the last path component
        String last = components[components.length - 1];
        components[components.length - 1] = last.substring(0, last.length() - 4);

        String username, musicType;

        if (components.length == 1) {
            username = components[0];
            musicType = defaultViewCache.get(username);

            if (musicType == null) {
                var client = new MixcloudClient(request.host());
                musicType = client.queryDefaultView(username);
                defaultViewCache.put(username, musicType);
            }
        }
        else {
            musicType = components[components.length - 1];

            username = components[components.length - 2];
            if (username == null || username.isEmpty()) {
                throw new HttpException(403, "Forbidden");
            }
        }

        String thing = String.format("%s's %s", username, musicType);
        String playlist = "";

        if (username.equals("playlist") || username.equals("playlists")) {
            if (components.length < 3) {
                throw new HttpException(403, "Forbidden");
            }

            playlist = musicType;
            musicType = "playlist";
            username = components[components.length - 3];

            if (username == null || username.isEmpty()) {
                throw new HttpException(403, "Forbidden");
            }

            thing = String.format("%s's %s playlist", username, playlist);
        }

        musicType = switch (musicType) {  //NOPMD - suppressed UseStringBufferForStringAppends (WTF PMD?)
          case "stream", "shows", "favorites", "history", "playlist" -> musicType;
          case "uploads" -> "shows";
          case "listens" -> "history";
          case "playlists" -> "playlist";
          default -> throw new HttpException(403, "Forbidden");
        };

        if (musicType.equals("playlist") && playlist.isBlank()) {
            throw new HttpException(403, "Forbidden");
        }

        logger.log(INFO, "The podcast will contain {0}", thing);
        Podcast podcast = podcastCache.get(thing);

        if (podcast == null) {
            try {
                var client = new MixcloudClient(request.host());
                podcast = client.query(username, musicType, playlist);
                podcastCache.put(thing, podcast);
                podcastCache.scrub();
            }
            catch (MixcloudUserException ex) {
                logger.log(ERROR, "There''s no Mixcloud user with username {0}", ex.username);
                throw new HttpException(404, "Not Found", ex);
            }
            catch (MixcloudPlaylistException ex) {
                logger.log(ERROR, "{0} doesn''t have a \"{1}\" playlist", new String[] {ex.username, ex.playlist});
                throw new HttpException(404, "Not Found", ex);
            }
        }
        else {
            logger.log(INFO, "Podcast retrieved from cache: {0}", thing);
        }

        // handle If-Modified-Since
        HttpHeaderWriter headerWriter = new HttpHeaderWriter();
        Date lastModified = new Date(podcast.createdAt / 1000 * 1000);  // truncate milliseconds for comparison

        if (headerWriter.sendNotModifiedHeadersIfNeeded(request, writer, lastModified)) {
            logger.log(INFO, "Responding with 304 for unmodified podcast: {0}", request.path);
            return;  // the request was satisfied via not-modified response headers
        }

        // kick off any downloads from Mixcloud which are now needed
        if (podcast.episodes.isEmpty()) {
            if (! playlist.isBlank())
                logger.log(INFO, "{0} is empty", thing);
            else
                logger.log(INFO, "{0} has no {1}", new String[] { username, musicType });
        }
        else {
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
                logger.log(INFO, "We''ve already queued or downloaded {0}", thing);
            }
            else {
                String filesStr = (queued == 1) ? "music file" : "music files";
                logger.log(INFO, "Downloading {0} new {1} ...", new Object[] { queued, filesStr });
                queue.processQueue(false);
            }
        }

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
