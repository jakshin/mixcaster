/*
 * Copyright (c) 2022 Jason Jackson
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

package jakshin.mixcaster;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Utility functions for use in tests.
 */
public class TestUtilities {
    /**
     * Creates a temporary directory with some fake files in it,
     * and puts its path into the music_dir system property.
     */
    @NotNull
    public static Path createMockMusicDir(String prefix) throws IOException {
        Path mockMusicDir = Files.createTempDirectory(prefix);
        Files.createDirectory(Path.of(mockMusicDir.toString(), "dir"));

        Path subdir = Path.of(mockMusicDir.toString(), "dir/subdir");
        Files.createDirectory(subdir);

        Path file = Path.of(mockMusicDir.toString(), "dir/file.m4a");
        Files.writeString(file, "fake file data");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(5, ChronoUnit.MINUTES)));

        Files.createSymbolicLink(Path.of(mockMusicDir.toString(), "dir/file-link"), file);
        Files.createSymbolicLink(Path.of(mockMusicDir.toString(), "dir/subdir-link"), subdir);

        System.setProperty("music_dir", mockMusicDir.toString());
        System.setProperty("http_cache_time_seconds", "3600");
        System.setProperty("episode_max_count", "25");
        System.setProperty("download_threads", "auto");

        return mockMusicDir;
    }

    /**
     * Recursively removes a temporary directory.
     * Intended for use in afterAll/tearDown methods.
     * Fails the test if anything goes wrong.
     */
    public static void removeTempDirectory(Path tempDir) {
        if (tempDir == null || !Files.exists(tempDir)) {
            return;
        }

        try {
            String systemTempDir = Path.of(System.getProperty("java.io.tmpdir")).toRealPath().toString();
            if (! tempDir.toRealPath().toString().startsWith(systemTempDir)) {
                fail("That's not a temp directory! " + tempDir);
            }

            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                }
                catch (IOException e) {
                    e.printStackTrace();
                    fail("Couldn't remove temp directory: " + tempDir);
                }
            });
        }
        catch (IOException ex) {
            ex.printStackTrace();
            fail("Couldn't remove temp directory: " + tempDir);
        }
    }
}