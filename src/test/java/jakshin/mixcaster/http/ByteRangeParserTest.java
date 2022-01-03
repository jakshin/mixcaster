/*
 * Copyright (C) 2016 Jason Jackson
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

package jakshin.mixcaster.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.LogManager;

import static jakshin.mixcaster.logging.Logging.logger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ByteRangeParser class.
 */
class ByteRangeParserTest {
    private ByteRangeParser instance;

    @BeforeAll
    static void beforeAll() {
        LogManager.getLogManager().reset();
        logger.setLevel(Level.OFF);
    }

    @BeforeEach
    void setUp() {
        this.instance = new ByteRangeParser();
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void parseReturnsNullForEmptyRange() throws HttpException {
        ByteRange result = instance.parse("");
        assertNull(result);
    }

    @Test
    void parseHandlesLeftRange() throws HttpException {
        ByteRange result = instance.parse("bytes=123-");

        assert result != null;
        assertEquals(123, result.start());
        assertEquals(-1, result.end());
    }

    @Test
    void parseHandlesRightRange() throws HttpException {
        ByteRange result = instance.parse("bytes=-456");

        assert result != null;
        assertEquals(-1, result.start());
        assertEquals(456, result.end());
    }

    @Test
    void parseHandlesCompleteRange() throws HttpException {
        ByteRange result = instance.parse("bytes=123-456");

        assert result != null;
        assertEquals(123, result.start());
        assertEquals(456, result.end());
    }

    @Test
    void parseReturnsNullForNonByteRange() throws HttpException {
        ByteRange result = instance.parse("stuff=123-456");
        assertNull(result);
    }

    @Test
    void parseThrowsOnMultipleRange() {
        HttpException thrown = assertThrows(HttpException.class,
                () -> instance.parse("bytes=123-456,789-1000"));

        assertEquals(500, thrown.httpResponseCode);
        assertTrue(thrown.getMessage().contains("Unsupported"));
    }

    @Test
    void parseReturnsNullForRangeWithoutDash() throws HttpException {
        ByteRange result = instance.parse("bytes=123");
        assertNull(result);
    }

    @Test
    void parseReturnsNullForRangeWithExtraDash() throws HttpException {
        ByteRange result = instance.parse("bytes=123-456-");
        assertNull(result);
    }

    @Test
    void parseReturnsNullForRangeWithOnlyDash() throws HttpException {
        ByteRange result = instance.parse("bytes=-");
        assertNull(result);
    }

    @Test
    void parseReturnsNullForNonNumericRange() throws HttpException {
        ByteRange result = instance.parse("bytes=123-foo");
        assertNull(result);
    }

    @Test
    void parseReturnsNullForInvalidRange() throws HttpException {
        ByteRange result = instance.parse("bytes=456-123");  // start is greater than end
        assertNull(result);
    }

    @Test
    void translateReturnsNullForNull() throws HttpException {
        ByteRange result = instance.translate(null, 1234);
        assertNull(result);
    }

    @Test
    void translateReturnsNullForZeroLengthFile() throws HttpException {
        ByteRange range = new ByteRange(123, 456);
        ByteRange result = instance.translate(range, 0);
        assertNull(result);
    }

    @Test
    void translateHandlesLeftRange() throws HttpException {
        ByteRange range = new ByteRange(123, -1);
        ByteRange result = instance.translate(range, 1234);

        assert result != null;
        assertEquals(123, result.start());
        assertEquals(1233, result.end());
    }

    @Test
    void translateHandlesLongLeftRange() {
        HttpException thrown = assertThrows(HttpException.class, () -> {
            ByteRange range = new ByteRange(12345, -1);
            instance.translate(range, 1234);
        });

        assertEquals(416, thrown.httpResponseCode);
    }

    @Test
    void translateHandlesRightRange() throws HttpException {
        ByteRange range = new ByteRange(-1, 456);
        ByteRange result = instance.translate(range, 1234);

        assert result != null;
        assertEquals(778, result.start());  // 1233 - 778 + 1 = 456
        assertEquals(1233, result.end());
    }

    @Test
    void translateHandlesLongRightRange() throws HttpException {
        ByteRange range = new ByteRange(-1, 12345);
        ByteRange result = instance.translate(range, 1234);

        assert result != null;
        assertEquals(0, result.start());  // entire file
        assertEquals(1233, result.end());
    }

    @Test
    void translateHandlesCompleteRange() throws HttpException {
        ByteRange range = new ByteRange(123, 456);
        ByteRange result = instance.translate(range, 1234);

        assert result != null;
        assertEquals(123, result.start());
        assertEquals(456, result.end());
    }

    @Test
    void translateHandlesLongCompleteRange() throws HttpException {
        ByteRange range = new ByteRange(123, 4567);
        ByteRange result = instance.translate(range, 1234);

        assert result != null;
        assertEquals(123, result.start());
        assertEquals(1233, result.end());
    }

    @Test
    void translateThrowsOnInvalidRange() {
        assertThrows(HttpException.class, () -> {
            ByteRange range = new ByteRange(-1, -1);
            instance.translate(range, 1234);
        });
    }
}
