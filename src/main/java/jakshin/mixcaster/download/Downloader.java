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

package jakshin.mixcaster.download;

import jakshin.mixcaster.dlqueue.Download;
import jakshin.mixcaster.dlqueue.DownloadQueue;
import jakshin.mixcaster.http.ServableFile;
import jakshin.mixcaster.mixcloud.*;
import jakshin.mixcaster.podcast.Podcast;
import jakshin.mixcaster.podcast.PodcastEpisode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import static jakshin.mixcaster.logging.Logging.*;

/**
 * Downloads music files from Mixcloud, either at a command line,
 * or on a background thread for a "watched" Mixcloud user or playlist.
 */
public class Downloader {
    /**
     * Creates a new instance.
     * @param exitWhenDownloadQueueIsEmpty Whether to exit when the download queue is empty.
     */
    public Downloader(boolean exitWhenDownloadQueueIsEmpty) {
        this.exitWhenDownloadQueueIsEmpty = exitWhenDownloadQueueIsEmpty;
    }

    /**
     * Downloads the files requested by the given command-line arguments.
     *
     * @param args Command-line arguments used to make a MusicSet to download.
     * @return A code indicating success (0) or failure (1 or 2).
     */
    public int download(@NotNull String[] args)
            throws InterruptedException, IOException, MixcloudException, TimeoutException, URISyntaxException,
                   MusicSet.InvalidInputException, DownloadOptions.InvalidOptionException {

        try {
            // parse our command-line arguments
            MusicSet musicSet = MusicSet.of(Arrays.stream(args).filter(s -> !s.startsWith("-")).toList());

            DownloadOptions opts = DownloadOptions.of(Arrays.stream(args)
                    .filter(s -> s.startsWith("-") && !s.equals("-download")).toList());

            String problem = DownloadOptions.validate(opts);
            if (problem != null) {
                logger.log(ERROR, problem);
                return 1;
            }

            if (opts.limit() != null) {
                System.setProperty("episode_max_count", opts.limit());
            }

            // query Mixcloud
            String localHostAndPort = System.getProperty("http_hostname") + ":" + System.getProperty("http_port");
            MixcloudClient client = new MixcloudClient(localHostAndPort);

            if (musicSet.musicType() == null) {
                String defaultMusicType = client.queryDefaultView(musicSet.username());
                musicSet = new MusicSet(musicSet.username(), defaultMusicType, null);
            }

            Podcast podcast = client.query(musicSet);

            // do the things
            if (opts.rssPath() != null) {
                writePodcastRSS(podcast, opts.rssPath(), musicSet);
            }

            startDownloads(podcast, opts);
            return 0;
        }
        catch (MixcloudUserException ex) {
            logger.log(ERROR, "There''s no Mixcloud user with username {0}", ex.username);
            logger.log(DEBUG, "", ex);
            return 2;
        }
        catch (MixcloudPlaylistException ex) {
            logger.log(ERROR, "{0} doesn''t have a \"{1}\" playlist", new String[] {ex.username, ex.playlist});
            logger.log(DEBUG, "", ex);
            return 2;
        }
    }

    /**
     * Writes the given podcast's RSS to a file at the given path,
     * using the given MusicSet to make up a default filename if needed.
     */
    private void writePodcastRSS(@NotNull Podcast podcast,
                                 @NotNull String rssPath,
                                 @NotNull MusicSet musicSet) throws IOException {

        if (rssPath.isBlank()) {
            String playlist = (musicSet.playlist() == null) ? "" : "." + musicSet.playlist();
            rssPath = musicSet.username() + "." + musicSet.musicType() + playlist + ".rss.xml";
        }

        logger.log(INFO, "Writing podcast RSS to {0}", rssPath);
        Files.writeString(Paths.get(rssPath), podcast.createXml(), StandardCharsets.UTF_8);
    }

    /**
     * Downloads the music files referred to by the given podcast, with the given options.
     */
    private void startDownloads(@NotNull Podcast podcast, @NotNull DownloadOptions opts) {
        DownloadQueue queue = DownloadQueue.getInstance();

        for (PodcastEpisode episode : podcast.episodes) {
            String localPath = ServableFile.getLocalPath(episode.enclosureUrl.toString());

            if (opts.outDirPath() != null) {
                // use the normal filename, but in the requested output directory,
                // and without putting the file into a subdirectory based on username
                int pos = episode.enclosureUrl.getPath().lastIndexOf('/');
                String name = episode.enclosureUrl.getPath().substring(pos + 1);
                localPath = Path.of(opts.outDirPath(), name).toString();
            }

            Download download = new Download(episode.enclosureMixcloudUrl.toString(),
                    episode.enclosureLengthBytes, episode.enclosureLastModified, localPath);
            queue.enqueue(download);
        }

        int downloadCount = queue.queueSize();
        if (downloadCount == 0) {
            if (podcast.episodes.isEmpty()) {
                logger.log(INFO, "No music files to download (empty podcast)");
            }
            else {
                String logStr = "All music files have already been downloaded";
                if (queue.activeDownloadCount() > 0) logStr += " (or are being downloaded now)";
                logger.log(INFO, logStr);
            }
        }
        else {
            String filesStr = (downloadCount == 1) ? "music file" : "music files";
            logger.log(INFO, "Downloading {0} {1} ...", new Object[] { downloadCount, filesStr });
            queue.processQueue(exitWhenDownloadQueueIsEmpty);
        }
    }

    /**
     * Whether to exit when the download queue is empty, iff we add any downloads to it.
     * This is set to true for command-line downloads, and false for downloads from watches.
     */
    private final boolean exitWhenDownloadQueueIsEmpty;
}
