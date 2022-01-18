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

package jakshin.mixcaster.watch;

import jakshin.mixcaster.download.Downloader;
import jakshin.mixcaster.mixcloud.MixcloudException;
import jakshin.mixcaster.mixcloud.MusicSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static jakshin.mixcaster.logging.Logging.*;

/**
 * Knows how to check mixcaster-watches.conf for user/playlists,
 * check each one found for new music, and start downloading.
 */
public class Watcher implements Runnable {

    /**
     * Are we watching any music sets?
     */
    public static synchronized boolean isWatchingAnything() throws IOException {
        if (watchedMusicSets == null) {
            loadOrRefreshSettings();
        }

        return (watchedMusicSets.size() != 0);
    }

    /**
     * Are we watching any of the given music sets?
     * @param musicSets A list of music sets we might be watching.
     */
    public static synchronized boolean isWatchingAnyOf(@NotNull final List<MusicSet> musicSets) throws IOException {
        if (watchedMusicSets == null) {
            loadOrRefreshSettings();
        }

        for (MusicSet musicSet : musicSets) {
            if (watchedMusicSets.contains(musicSet)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Starts checking watches in mixcaster-watches.conf every watch_interval_minutes,
     * after first waiting for an initial delay (measured in seconds).
     */
    public static synchronized void start(long initialDelaySeconds) {
        if (future == null) {
            long delaySeconds = getWatchIntervalMinutes() * 60L;
            var executor = new ScheduledThreadPoolExecutor(1);
            future = executor.scheduleWithFixedDelay(new Watcher(),
                    initialDelaySeconds, delaySeconds, TimeUnit.SECONDS);
        }
    }

    /**
     * Stops checking any watches configured in mixcaster-watches.conf.
     */
    public static synchronized void stop() {
        if (future != null) {
            future.cancel(true);
            future = null;
        }

        watchedMusicSets = null;
        settings = null;
    }

    /**
     * Checks each watch configured in mixcaster-watches.conf for new music.
     * This gets called every watch_interval_minutes.
     */
    public void run() {
        try {
            synchronized (Watcher.class) {
                loadOrRefreshSettings();
                if (watchedMusicSets.isEmpty()) {
                    return;  // we already logged in loadIfChanged(), so just quietly bail here
                }

                var downloader = new Downloader(null);
                for (var musicSet : watchedMusicSets) {
                    String[] parts;

                    if (musicSet.playlist() != null) {
                        logger.log(INFO, "Checking {0}''s playlist {1} for new music",
                                new String[] { musicSet.username(), musicSet.playlist() });
                        parts = new String[] { musicSet.username(), musicSet.musicType(), musicSet.playlist() };
                    }
                    else if (musicSet.musicType() != null) {
                        logger.log(INFO, "Checking {0}''s {1} for new music",
                                new String[] { musicSet.username(), musicSet.musicType() });
                        parts = new String[] { musicSet.username(), musicSet.musicType() };
                    }
                    else {
                        logger.log(INFO, "Checking {0}''s default view for new music",
                                new String[] { musicSet.username() });
                        parts = new String[] { musicSet.username() };
                    }

                    try {
                        // some exceptions thrown by download() are potentially watch-specific;
                        // we catch those below, so we can carry on checking any other watches
                        downloader.download(parts, true);
                    }
                    catch (IOException | MixcloudException | TimeoutException | URISyntaxException ex) {
                        logger.log(ERROR, "Failed to check watched user/playlist for new music: {0}",
                                new String[] { ex.getMessage() });
                        logger.log(DEBUG, "", ex);
                    }
                }
            }
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        catch (Throwable ex) {
            // catch everything, because any exception thrown from run() will cause the scheduled executions to stop
            logger.log(ERROR,"Exception while checking watched users/playlists for new music", ex);
        }
    }

    /**
     * Returns the full path to mixcaster-watches.conf.
     */
    @VisibleForTesting
    @NotNull
    static Path getConfigFilePath() {
        Path path = Paths.get(Watcher.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        return Paths.get(path.getParent().toString(), "mixcaster-watches.conf");
    }

    /**
     * Loads or refreshes settings from mixcaster-watches.conf...
     * probably not at all surprising, given the method's name.
     */
    @VisibleForTesting
    static synchronized void loadOrRefreshSettings() throws IOException {
        if (settings == null) {
            Path configFilePath = getConfigFilePath();
            settings = new WatchSettings(configFilePath);
        }

        watchedMusicSets = settings.loadIfChanged();
    }

    /**
     * Gets the watch_interval_minutes configuration setting.
     * @return watch_interval_minutes, converted to an int.
     */
    @VisibleForTesting
    static int getWatchIntervalMinutes() {
        String minutesStr = System.getProperty("watch_interval_minutes");
        return Integer.parseInt(minutesStr);  // already validated
    }

    /**
     * The future representing scheduled execution.
     * Null until start() has been called, or if stop() has been called.
     */
    private static ScheduledFuture<?> future;

    /**
     * Settings, stored in mixcaster-watches.conf.
     */
    private static WatchSettings settings;

    /**
     * Music sets we're watching, loaded from our settings file via our "settings" property.
     * This is refreshed from disk, if needed, every watch_interval_minutes.
     */
    private static List<MusicSet> watchedMusicSets;
}
