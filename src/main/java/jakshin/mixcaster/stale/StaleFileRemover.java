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

package jakshin.mixcaster.stale;

import jakshin.mixcaster.stale.attributes.LastUsedAttr;
import jakshin.mixcaster.stale.attributes.RssLastRequestedAttr;
import jakshin.mixcaster.stale.attributes.WatchesAttr;
import jakshin.mixcaster.watch.Watcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static jakshin.mixcaster.logging.Logging.*;

/**
 * Knows how to find "stale" music files, based on their "lastUsed" and "watches" attributes,
 * and either move them to the trash or delete them.
 *
 * Mixcaster sets "lastUsed" and "watches" attributes on *.part files when it creates them.
 * It also updates the "lastUsed" attribute whenever a file is included in any podcast's RSS
 * (it's possible for a file to be included in multiple podcasts, e.g. with playlists).
 * Lastly, it updates the attribute each time a music file is requested via HTTP, including range requests,
 * but only if the attribute already exists on the file, i.e. if it was downloaded by Mixcaster.
 * As such, the timestamp in the attribute indicates the last time a music file was "used" by Mixcaster.
 *
 * This class periodically looks for music files which haven't been used in a configurable span of time,
 * and removes them (moving them to the trash if that's supported, otherwise just deleting them).
 */
public class StaleFileRemover implements Runnable {
    /**
     * Starts periodically looking for stale music files and removing them,
     * after first waiting for an initial delay.
     */
    public boolean start(long initialDelay, long delay, @NotNull TimeUnit timeUnit) {
        if (getRemoveStaleMusicFilesAfterDaysSetting() == 0) {
            logger.log(INFO, "Feature disabled: periodically removing stale music files");
            return false;
        }

        synchronized (StaleFileRemover.class) {
            if (future != null) {
                return false;
            }

            var executor = new ScheduledThreadPoolExecutor(1);
            future = executor.scheduleWithFixedDelay(this, initialDelay, delay, timeUnit);
            return true;
        }
    }

    /**
     * Stops periodically looking for stale music files and removing them.
     */
    public void stop() {
        synchronized (StaleFileRemover.class) {
            if (future != null) {
                future.cancel(true);
                future = null;
            }
        }
    }

    /**
     * Looks for stale files and removes them.
     * This automatically gets called periodically, once start() has been called.
     */
    public void run() {
        try {
            Path musicDir = getMusicDirSetting();
            var rssLastRequestedAttr = new RssLastRequestedAttr(musicDir);

            if (! rssLastRequestedAttr.isSupported()) {
                logger.log(INFO, () -> "Can't remove stale music files, since user-defined attributes"
                                        + " aren't supported in music dir: " + musicDir);
                return;
            }

            final OffsetDateTime rssLastRequestedUtc = rssLastRequestedAttr.exists() ?
                    rssLastRequestedAttr.getValue() : null;

            int removeAfterDays = getRemoveStaleMusicFilesAfterDaysSetting();

            if (Watcher.isWatchingAnything()) {
                int minDays = (int) Math.ceil(getWatchIntervalMinutes() / 1440f);
                if (removeAfterDays < minDays) {
                    logger.log(WARNING, "Using value {0} for the remove_stale_music_files_after_days setting, " +
                            "instead of the configured value of {1}, to avoid removing stale files more often " +
                            "than we check watched Mixcloud users/playlists", new Object[] { removeAfterDays, minDays });
                    removeAfterDays = minDays;
                }
            }

            OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime staleThresholdUtc = nowUtc.minus(removeAfterDays, ChronoUnit.DAYS);
            logger.log(INFO, () -> String.format("Looking for stale music files to remove, threshold = %s",
                    DateTimeFormatter.RFC_1123_DATE_TIME.format(staleThresholdUtc)));

            if (rssLastRequestedUtc != null && rssLastRequestedUtc.isBefore(staleThresholdUtc)) {
                logger.log(INFO, () -> String.format("No podcast requests have been received since %s, " +
                        "so we'll only consider files if they're included in a watched music set",
                        DateTimeFormatter.RFC_1123_DATE_TIME.format(rssLastRequestedUtc)));
            }

            try (Stream<Path> fileStream = Files.walk(musicDir)) {
                fileStream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> this.isStale(path, staleThresholdUtc, rssLastRequestedUtc))
                        .forEach(this::removeFile);
            }
        }
        catch (Throwable ex) {
            // catch everything, because any exception thrown from run() will cause the scheduled executions to stop
            logger.log(ERROR,"Exception while removing stale music files", ex);
        }
    }

    /**
     * Determines whether the given file is "stale" (hasn't been used by Mixcaster in a while),
     * based on the values in its mixcaster.lastUsed and mixcaster.watches user-defined attributes.
     * Files without the mixcaster.lastUsed attribute are never considered stale.
     *
     * @param path                The path to the file that might or might not be stale.
     * @param staleThresholdUtc   The timestamp the file's mixcaster.lastUsed attribute must be before,
     *                            for the file to be considered stale.
     * @param rssLastRequestedUtc The timestamp of the most recent request for RSS Mixcaster has received.
     *                            If this is earlier than our staleness threshold, we won't consider files stale
     *                            unless they're included in a music set we're watching.
     */
    @SuppressWarnings("RedundantIfStatement")
    private boolean isStale(@NotNull Path path,
                            @NotNull OffsetDateTime staleThresholdUtc,
                            @Nullable OffsetDateTime rssLastRequestedUtc) {
        try {
            var lastUsedAttr = new LastUsedAttr(path);
            if (! lastUsedAttr.isSupported()) return false;

            if (! lastUsedAttr.exists()) {
                // we've purposefully only set the lastUsed attribute on files Mixcaster downloaded itself,
                // so that if files are manually added to the music dir, they won't have the attribute;
                // here, we ignore files without the attribute, so we'll only remove files we downloaded
                return false;
            }

            OffsetDateTime lastUsed = lastUsedAttr.getValue();
            if (lastUsed.isAfter(staleThresholdUtc)) {
                return false;  // the file was used recently
            }

            var watchesAttr = new WatchesAttr(path);
            if (Watcher.isWatchingAnyOf(watchesAttr.getValue())) {
                // this file was in a music set we're watching; it must have disappeared from that music set,
                // or its lastUsed attribute would have been updated recently, so let's clean it up
                return true;
            }

            if (rssLastRequestedUtc != null && rssLastRequestedUtc.isBefore(staleThresholdUtc)) { //NOPMD
                // no podcast client has been making requests to Mixcaster recently; rather than letting files age out,
                // removing them, and maybe needing to download them again later, when we do hear from a podcast client,
                // let's just leave them alone for now
                return false;
            }

            // we're still getting podcast requests from at least one podcast client recently,
            // but not for any podcast that includes this file, so we'll probably never need it again
            return true;
        }
        catch (IOException | DateTimeException | UnsupportedOperationException ex) {
            logger.log(ERROR, ex, () -> "Could not determine whether file is stale: " + path);
            return false;
        }
    }

    /**
     * Moves a file or directory to the trash.
     *
     * @param path The file or directory to trash.
     * @return Whether it succeeded (if the file doesn't exist, that's considered success).
     */
    private boolean moveFileToTrash(@NotNull Path path) {
        try {
            if (! canMoveToTrash) return false;
            if (! Desktop.getDesktop().moveToTrash(path.toFile())) return false;
            logger.log(INFO, "Moved file to the trash: {0}", path);
            return true;
        }
        catch (IllegalArgumentException ex) {
            // the specified file doesn't exist (or it might be a broken symlink, at least on OpenJDK 17.0,
            // which seems like a JDK bug, but doesn't affect us because we don't try to trash symlinks)
            return true;
        }
        catch (UnsupportedOperationException ex) {
            return false;
        }
    }

    /**
     * Removes a file or directory by first trying to move it to the trash,
     * and if that's not supported on this platform, or doesn't work, deleting it.
     * Directories can contain files when moved to the trash, but must be empty to be deleted.
     *
     * @param path The file or directory to remove.
     */
    private void removeFile(@NotNull Path path) {
        if (moveFileToTrash(path)) {
            return;
        }

        try {
            Files.delete(path);
            logger.log(INFO, "Deleted file: {0}", path);
        }
        catch (IOException ex) {
            logger.log(ERROR, "Could not delete file: {0}", path);
        }
    }

    /**
     * Gets the music_dir configuration setting.
     * @return music_dir, as an absolute Path object.
     */
    @NotNull
    private Path getMusicDirSetting() {
        String musicDir = System.getProperty("music_dir");
        if (musicDir.startsWith("~/"))
            musicDir = System.getProperty("user.home") + musicDir.substring(1);
        return Path.of(musicDir);
    }

    /**
     * Gets the remove_stale_music_files_after_days configuration setting.
     * @return remove_stale_music_files_after_days, converted to an int.
     */
    private int getRemoveStaleMusicFilesAfterDaysSetting() {
        String daysStr = System.getProperty("remove_stale_music_files_after_days");
        return Integer.parseInt(daysStr);  // already validated
    }

    /**
     * Gets the watch_interval_minutes configuration setting.
     * @return watch_interval_minutes, converted to an int.
     */
    private int getWatchIntervalMinutes() {
        String minutesStr = System.getProperty("watch_interval_minutes");
        return Integer.parseInt(minutesStr);  // already validated
    }

    /**
     * Whether we think moving files to the trash is supported on this platform.
     */
    private final boolean canMoveToTrash = Desktop.isDesktopSupported() &&
            Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);

    /**
     * The future representing scheduled execution.
     * Null until start() has been called.
     */
    private static ScheduledFuture<?> future;
}
