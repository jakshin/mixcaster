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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.logging.LogRecord;

import static jakshin.mixcaster.logging.Logging.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the SystemOutFormatter class.
 */
class SystemOutFormatterTest {
    private SystemOutFormatter formatter;
    private String logMessage;

    @BeforeEach
    void setUp() {
        formatter = new SystemOutFormatter();
        logMessage = """
                This is the first line
                This is the second line""";
    }

    @Test
    void formatsDebugMessages() {
        LogRecord record = new LogRecord(DEBUG, logMessage);
        String formatted = formatter.format(record);
        assertThat(formatted).contains(logMessage);
    }

    @Test
    void formatsInfoMessages() {
        LogRecord record = new LogRecord(INFO, logMessage);
        String formatted = formatter.format(record);
        assertThat(formatted).contains(logMessage);
    }

    @Test
    void formatsWarningMessages() {
        LogRecord record = new LogRecord(WARNING, logMessage);

        String formatted = formatter.format(record);

        assertThat(formatted).startsWith("Warning:");
        assertThat(formatted).contains(logMessage);
    }

    @Test
    void formatsErrorMessages() {
        LogRecord record = new LogRecord(ERROR, logMessage);

        String formatted = formatter.format(record);

        assertThat(formatted).startsWith("Error:");
        assertThat(formatted).contains(logMessage);
    }

    @Test
    void formatsErrorMessagesWithThrowables() {
        LogRecord record = new LogRecord(ERROR, null);

        var inner = new NullPointerException("Test inner exception");
        var outer = new IOException("Test outer exception", inner);
        record.setThrown(outer);

        String formatted = formatter.format(record);

        assertThat(formatted).startsWith("Error: -");
        assertThat(formatted).contains("java.io.IOException: Test outer exception");
        assertThat(formatted).contains("Caused by: java.lang.NullPointerException: Test inner exception");
    }
}
