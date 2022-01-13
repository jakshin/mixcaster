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

import jakshin.mixcaster.mixcloud.MixcloudException;
import jakshin.mixcaster.mixcloud.MixcloudPlaylistException;
import jakshin.mixcaster.mixcloud.MixcloudUserException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.logging.LogRecord;

import static jakshin.mixcaster.logging.Logging.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for the LogFileFormatter class.
 */
class LogFileFormatterTest {
    private LogFileFormatter formatter;
    private final String line1 = "  This is the first line";
    private final String line2 = "  This is the second line";
    private final String logMessage = line1 + "\n" + line2;

    @BeforeEach
    void setUp() {
        formatter = new LogFileFormatter();
    }

    private void checkFormatted(String formatted, String logLevel, boolean hasMockThrowable) {
        try {
            var dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);
            int index = formatted.indexOf('\t');
            dateFormatter.parse(formatted.substring(0, index));
        }
        catch (ParseException ex) {
            fail(ex.getMessage());
        }

        assertThat(formatted).contains("\t" + logLevel + "\t" + line1.trim() + "\t");
        assertThat(formatted).contains(System.lineSeparator() + line2);

        if (hasMockThrowable) {
            assertThat(formatted).contains("ERROR: java.io.IOException: Test outer exception");
            assertThat(formatted).contains("CAUSE: java.lang.NullPointerException: Test inner exception");
        }
    }

    private void setMockThrowable(LogRecord record) {
        var inner = new NullPointerException("Test inner exception");
        var outer = new IOException("Test outer exception", inner);
        record.setThrown(outer);
    }

    @Test
    void formatsDebugMessages() {
        LogRecord record = new LogRecord(DEBUG, logMessage);

        String formatted = formatter.format(record);
        checkFormatted(formatted, "DEBUG", false);
    }

    @Test
    void formatsDebugMessagesWithThrowables() {
        LogRecord record = new LogRecord(DEBUG, logMessage);
        setMockThrowable(record);

        String formatted = formatter.format(record);
        checkFormatted(formatted, "DEBUG", true);
    }

    @Test
    void formatsInfoMessages() {
        LogRecord record = new LogRecord(INFO, logMessage);

        String formatted = formatter.format(record);
        checkFormatted(formatted, "INFO", false);
    }

    @Test
    void formatsInfoMessagesWithThrowables() {
        LogRecord record = new LogRecord(INFO, logMessage);
        setMockThrowable(record);

        String formatted = formatter.format(record);
        checkFormatted(formatted, "INFO", true);
    }

    @Test
    void formatsWarningMessages() {
        LogRecord record = new LogRecord(WARNING, logMessage);

        String formatted = formatter.format(record);
        checkFormatted(formatted, "WARNING", false);
    }

    @Test
    void formatsWarningMessagesWithThrowables() {
        LogRecord record = new LogRecord(WARNING, logMessage);
        setMockThrowable(record);

        String formatted = formatter.format(record);
        checkFormatted(formatted, "WARNING", true);
    }

    @Test
    void formatsErrorMessages() {
        LogRecord record = new LogRecord(ERROR, logMessage);

        String formatted = formatter.format(record);
        checkFormatted(formatted, "ERROR", false);
    }

    @Test
    void formatsErrorMessagesWithThrowables() {
        LogRecord record = new LogRecord(ERROR, logMessage);
        setMockThrowable(record);

        String formatted = formatter.format(record);
        checkFormatted(formatted, "ERROR", true);
    }

    @Test
    void fillsInEmptyMessages() {
        LogRecord record = new LogRecord(INFO, null);
        String formatted = formatter.format(record);
        assertThat(formatted).contains("\tINFO\t-\t");
    }

    @Test
    void formatsMixcloudUserExceptions() {
        LogRecord record = new LogRecord(ERROR, logMessage);
        record.setThrown(new MixcloudUserException("Testing", "mock-username"));

        String formatted = formatter.format(record);
        assertThat(formatted).contains("Testing (mock-username)");
    }

    @Test
    void formatsMixcloudPlaylistExceptions() {
        LogRecord record = new LogRecord(ERROR, logMessage);
        record.setThrown(new MixcloudPlaylistException("Testing", "mock-username", "mock-playlist"));

        String formatted = formatter.format(record);
        assertThat(formatted).contains("Testing (mock-username, mock-playlist)");
    }

    @Test
    void formatsMixcloudExceptions() {
        LogRecord record = new LogRecord(ERROR, logMessage);
        record.setThrown(new MixcloudException("Testing", "http://mixcloud.com/foo"));

        String formatted = formatter.format(record);
        assertThat(formatted).contains("Testing (at URL: http://mixcloud.com/foo)");
    }
}
