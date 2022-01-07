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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Utility functions for use in tests.
 */
public class TestUtilities {
    /**
     * Recursively removes a temporary directory.
     * Intended for use in afterAll/tearDown methods.
     * Fails the test if anything goes wrong.
     */
    public static void removeTempDirectory(Path tempDir) {
        if (tempDir == null || !Files.exists(tempDir)) {
            return;
        }

        String systemTempDir = System.getProperty("java.io.tmpdir");
        if (! tempDir.toString().startsWith(systemTempDir)) {
            fail("That's not a temp directory! " + tempDir);
        }

        try {
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
