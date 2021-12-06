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

import jakshin.mixcaster.mixcloud.MusicSet;
import jakshin.mixcaster.stale.attributes.LastUsedAttr;
import jakshin.mixcaster.stale.attributes.RssLastRequestedAttr;
import jakshin.mixcaster.stale.attributes.WatchesAttr;
import org.jetbrains.annotations.NotNull;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static jakshin.mixcaster.logging.Logging.ERROR;
import static jakshin.mixcaster.logging.Logging.logger;

/**
 * Knows how to set user-defined attributes we use to figure out if files are stale:
 * mixcaster.rssLastRequested, mixcaster.lastUsed and mixcaster.watches.
 *
 * All of this class's methods silently do nothing if user-defined attributes
 * aren't supported on this platform.
 */
public final class Freshener {
    /**
     * Updates the given file's lastUsed attribute to the current UTC date/time.
     * @param path The file whose attribute should be updated.
     */
    public static void updateLastUsedAttr(@NotNull Path path) {
        updateLastUsedAttr(path, false);
    }

    /**
     * Updates the given file's lastUsed attribute to the current UTC date/time,
     * optionally only if it's already set.
     *
     * @param path The file whose attribute should be updated.
     * @param onlyIfAttributeAlreadyExists Whether to only update an existing attribute,
     *                                     i.e. never creating a new attribute.
     */
    public static void updateLastUsedAttr(@NotNull Path path, boolean onlyIfAttributeAlreadyExists) {
        try {
            LastUsedAttr attr = new LastUsedAttr(path);

            if (attr.isSupported() && (!onlyIfAttributeAlreadyExists || attr.exists())) {
                OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
                attr.setValue(nowUtc);
            }
        }
        catch (Exception ex) {
            logger.log(ERROR, ex, () -> String.format("Unable to update file's lastUsed attribute: %s", path));
        }
    }

    /**
     * Updates the music directory's rssLastRequested attribute to the current UTC date/time.
     */
    public static void updateRssLastRequestedAttr() {
        try {
            Path musicDir = getMusicDirSetting();
            RssLastRequestedAttr attr = new RssLastRequestedAttr(musicDir);

            if (attr.isSupported()) {
                OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
                attr.setValue(nowUtc);
            }
        }
        catch (Exception ex) {
            logger.log(ERROR, "Unable to update music directory''s rssLastRequested attribute", ex);
        }
    }

    /**
     * Updates the given file's "watches" attribute to include the given music set.
     * The attribute isn't re-written if it already includes the music set.
     *
     * @param path The file whose attribute should be updated.
     * @param watchedSet The music set to include in the attribute's value.
     */
    public static void updateWatchesAttr(@NotNull Path path, @NotNull MusicSet watchedSet) {
        try {
            WatchesAttr attr = new WatchesAttr(path);
            if (! attr.isSupported()) return;

            // synchronizing like this serializes all writes to ANY file's "watches" attribute;
            // kind of lame, but at Mixcaster's low concurrency levels, it doesn't really hurt us
            synchronized (Freshener.class) {

                // also lock at the file level so multiple processes don't overlap writes to the attribute
                // (on OpenJDK 17.0.1, this only works right on Windows/NTFS; on macOS/APFS and Ubuntu/ext4,
                // the lock stops working as soon as we read/write a user-defined attribute on the file,
                // even though its isValid() still returns true; seems like an OpenJDK bug)
                try (FileChannel chan = FileChannel.open(path, LinkOption.NOFOLLOW_LINKS, StandardOpenOption.WRITE);
                     FileLock lock = chan.lock()) {

                    List<MusicSet> watches = attr.getValue();
                    if (!watches.contains(watchedSet)) {
                        watches.add(watchedSet);
                        attr.setValue(watches);
                    }
                }
                catch (FileLockInterruptionException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        catch (Exception ex) {
            logger.log(ERROR, ex, () -> String.format("Unable to update file's watches attribute: %s", path));
        }
    }

    /**
     * Gets the music_dir configuration setting.
     * @return music_dir, as an absolute Path object.
     */
    @NotNull
    private static Path getMusicDirSetting() {
        String musicDir = System.getProperty("music_dir");
        if (musicDir.startsWith("~/"))
            musicDir = System.getProperty("user.home") + musicDir.substring(1);
        return Path.of(musicDir);
    }

    /**
     * Private constructor to prevent instantiation.
     * This class's methods are all static, and it shouldn't be instantiated.
     */
    private Freshener() {
        // nothing here
    }
}
