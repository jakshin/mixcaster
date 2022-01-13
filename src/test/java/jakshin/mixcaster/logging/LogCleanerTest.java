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

package jakshin.mixcaster.logging;

import jakshin.mixcaster.TestUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the LogCleaner class.
 */
class LogCleanerTest {
    private LogCleaner cleaner;
    private Path mockLogDir;

    private void createMockLogDir() throws IOException, InterruptedException {
        mockLogDir = Files.createTempDirectory("mix-logc-");

        for (int i = 5; i >= 1; i--) {
            createMockLogFile(String.format("service.%d.log", i));
            createMockLogFile(String.format("download.%d.log", i));
            Thread.sleep(1);
        }
    }

    private void createMockLogFile(String filename) throws IOException {
        Files.createFile(Path.of(mockLogDir.toString(), filename));
    }

    @BeforeAll
    static void beforeAll() {
        // don't put a goofy Java icon in macOS's dock while tests run
        System.setProperty("apple.awt.UIElement", "true");
    }

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        cleaner = new LogCleaner();
        createMockLogDir();

        System.setProperty("log_dir", mockLogDir.toString());
        System.setProperty("log_max_count", "4");
    }

    @AfterEach
    void tearDown() {
        if (mockLogDir != null) {
            TestUtilities.removeTempDirectory(mockLogDir);
            mockLogDir = null;
        }

        System.clearProperty("log_dir");
        System.clearProperty("log_max_count");
    }

    @Test
    void deletesOnlyItsOwnLogFiles() throws IOException {
        Path path1 = Path.of(mockLogDir.toString(), "not-a-log-file.txt");
        Path path2 = Path.of(mockLogDir.toString(), "some-other-app.log");
        Files.createFile(path1);
        Files.createFile(path2);

        cleaner.run();

        assertThat(path1).exists();
        assertThat(path2).exists();
    }

    @Test
    void keepsUpToMaxLogFilesNewestLogs() {
        cleaner.run();

        List<String> expected = List.of("download.1.log", "download.2.log", "service.1.log", "service.2.log");
        checkRemainingFiles(expected);
    }

    @Test
    void stopsDeletingLogsWhenOneIsLocked() throws IOException {
        Files.createFile(Path.of(mockLogDir.toString(), "download.3.log.lck"));
        Files.createFile(Path.of(mockLogDir.toString(), "service.4.log.lck"));

        cleaner.run();

        List<String> expected = List.of("download.1.log", "download.2.log", "download.3.log", "download.3.log.lck",
                            "service.1.log", "service.2.log", "service.3.log", "service.4.log", "service.4.log.lck");
        checkRemainingFiles(expected);
    }

    private void checkRemainingFiles(List<String> expected) {
        String[] files = mockLogDir.toFile().list();
        assert files != null;

        List<String> actual = Arrays.asList(files);
        Collections.sort(actual);

        assertThat(actual).isEqualTo(expected);
    }
}
