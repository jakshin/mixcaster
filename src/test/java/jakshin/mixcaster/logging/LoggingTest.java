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
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.logging.*;

import static jakshin.mixcaster.logging.Logging.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for the Logging class.
 */
class LoggingTest {
    private static Path logDir;
    private static final Random rand = new Random();

    @BeforeAll
    static void beforeAll() throws IOException {
        logDir = Files.createTempDirectory("mix-log-");

        System.setProperty("log_dir", logDir.toString());
        System.setProperty("log_level", "ERROR");
        System.setProperty("log_max_count", "1");

        LogManager.getLogManager().reset();
        logger.setLevel(Level.OFF);
    }

    @AfterAll
    static void afterAll() {
        TestUtilities.removeTempDirectory(logDir);

        System.clearProperty("log_dir");
        System.clearProperty("log_level");
        System.clearProperty("log_max_count");

        LogManager.getLogManager().reset();
        logger.setLevel(Level.OFF);
    }

    @BeforeEach
    void setUp() {
        Logging.resetLogging();
        logger.setLevel(Level.OFF);
    }

    private FileHandler findFileHandler(Handler[] handlers) {
        for (Handler handler : handlers) {
            if (handler instanceof FileHandler) {
                return (FileHandler) handler;
            }
        }
        return null;
    }

    private SystemOutHandler findSystemOutHandler(Handler[] handlers) {
        for (Handler handler : handlers) {
            if (handler instanceof SystemOutHandler) {
                return (SystemOutHandler) handler;
            }
        }
        return null;
    }

    @Test
    void initializesOnlyOnce() throws IOException {
        Logging.initLogging(rand.nextBoolean());

        FileHandler fileHandler1 = findFileHandler(logger.getHandlers());
        SystemOutHandler systemOutHandler1 = findSystemOutHandler(logger.getHandlers());

        Logging.initLogging(rand.nextBoolean());

        FileHandler fileHandler2 = findFileHandler(logger.getHandlers());
        SystemOutHandler systemOutHandler2 = findSystemOutHandler(logger.getHandlers());

        assertThat(fileHandler2).isSameAs(fileHandler1);
        assertThat(systemOutHandler2).isSameAs(systemOutHandler1);
    }

    @Test
    void initializesSystemOutLogging() throws IOException {
        Logging.initLogging(rand.nextBoolean());

        SystemOutHandler systemOutHandler = findSystemOutHandler(logger.getHandlers());
        if (systemOutHandler == null) {
            fail("No SystemOutHandler was configured");
        }

        assertThat(systemOutHandler.getFormatter()).isInstanceOf(SystemOutFormatter.class);
        assertThat(systemOutHandler.getLevel()).isEqualTo(INFO);
    }

    @Test
    void initializesFileLogging() throws IOException {
        Logging.initLogging(rand.nextBoolean());

        FileHandler fileHandler = findFileHandler(logger.getHandlers());
        if (fileHandler == null) {
            fail("No FileHandler was configured");
        }

        assertThat(fileHandler.getFormatter()).isInstanceOf(LogFileFormatter.class);
        assertThat(fileHandler.getLevel()).isEqualTo(ERROR);  // as configured in beforeAll()
    }

    @Test
    void createsTheLogDirectoryIfNeeded() throws IOException {
        // we already created a temp directory to use as the log directory,
        // but we haven't done anything with it yet, so use a subdir instead
        Path newLogDir = Path.of(logDir.toString(), "actual-log-dir");
        System.setProperty("log_dir", newLogDir.toString());

        Logging.initLogging(rand.nextBoolean());

        assertThat(newLogDir).isDirectory();
    }

    @Test
    void exposesALogger() {
        assertThat(Logging.logger).isInstanceOf(Logger.class);
    }

    @Test
    void exposesLogLevelConstants() {
        assertThat(Logging.ERROR).isEqualTo(Level.SEVERE);
        assertThat(Logging.WARNING).isEqualTo(Level.WARNING);
        assertThat(Logging.INFO).isEqualTo(Level.INFO);
        assertThat(Logging.DEBUG).isEqualTo(Level.FINE);
    }
}
