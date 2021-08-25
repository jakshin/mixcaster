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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the ByteRangeParser class.
 */
public class ByteRangeParserTest {
    private ByteRangeParser instance;

    /** Scaffolding. */
    @BeforeClass
    public static void setUpClass() {
    }

    /** Scaffolding. */
    @AfterClass
    public static void tearDownClass() {
    }

    /** Scaffolding. */
    @Before
    public void setUp() {
        this.instance = new ByteRangeParser();
    }

    /** Scaffolding. */
    @After
    public void tearDown() {
    }

    /** Test. */
    @Test
    public void parseShouldReturnNullForEmptyRange() throws HttpException {
        ByteRange result = instance.parse("");
        assertNull(result);
    }

    /** Test. */
    @Test
    public void parseShouldHandleLeftRange() throws HttpException {
        ByteRange result = instance.parse("bytes=123-");
        assertEquals(123, result.start);
        assertEquals(-1, result.end);
    }

    /** Test. */
    @Test
    public void parseShouldHandleRightRange() throws HttpException {
        ByteRange result = instance.parse("bytes=-456");
        assertEquals(-1, result.start);
        assertEquals(456, result.end);
    }

    /** Test. */
    @Test
    public void parseShouldHandleCompleteRange() throws HttpException {
        ByteRange result = instance.parse("bytes=123-456");
        assertEquals(123, result.start);
        assertEquals(456, result.end);
    }

    /** Test. */
    @Test
    public void parseShouldReturnNullForNonByteRange() throws HttpException {
        ByteRange result = instance.parse("stuff=123-456");
        assertNull(result);
    }

    /** Test. */
    @Test (expected=HttpException.class)
    public void parseShouldThrowOnMultipleRange() throws HttpException {
        ByteRange result = instance.parse("bytes=123-456,789-1000");
        assertNull(result);  // shouldn't be reached
    }

    /** Test. */
    @Test
    public void parseShouldReturnNullForRangeWithoutDash() throws HttpException {
        ByteRange result = instance.parse("bytes=123");
        assertNull(result);
    }

    /** Test. */
    @Test
    public void parseShouldReturnNullForRangeWithExtraDash() throws HttpException {
        ByteRange result = instance.parse("bytes=123-456-");
        assertNull(result);
    }

    /** Test. */
    @Test
    public void parseShouldReturnNullForRangeWithOnlyDash() throws HttpException {
        ByteRange result = instance.parse("bytes=-");
        assertNull(result);
    }

    /** Test. */
    @Test
    public void parseShouldReturnNullForNonNumericRange() throws HttpException {
        ByteRange result = instance.parse("bytes=123-foo");
        assertNull(result);
    }

    /** Test. */
    @Test
    public void parseShouldReturnNullForInvalidRange() throws HttpException {
        ByteRange result = instance.parse("bytes=456-123");  // start is greater than end
        assertNull(result);
    }

    /** Test. */
    @Test
    public void translateShouldReturnNullForNull() throws HttpException {
        ByteRange result = instance.translate(null, 1234);
        assertNull(result);
    }

    /** Test. */
    @Test
    public void translateShouldReturnNullForZeroLengthFile() throws HttpException {
        ByteRange range = new ByteRange(123, 456);
        ByteRange result = instance.translate(range, 0);
        assertNull(result);
    }

    /** Test. */
    @Test
    public void translateShouldHandleLeftRange() throws HttpException {
        ByteRange range = new ByteRange(123, -1);
        ByteRange result = instance.translate(range, 1234);
        assertEquals(123, result.start);
        assertEquals(1233, result.end);
    }

    /** Test. */
    @Test (expected=HttpException.class)
    public void translateShouldHandleLongLeftRange() throws HttpException {
        ByteRange range = new ByteRange(12345, -1);
        ByteRange result = instance.translate(range, 1234);
        assertNull(result);  // shouldn't be reached
    }

    /** Test. */
    @Test
    public void translateShouldHandleRightRange() throws HttpException {
        ByteRange range = new ByteRange(-1, 456);
        ByteRange result = instance.translate(range, 1234);
        assertEquals(778, result.start);  // 1233 - 778 + 1 = 456
        assertEquals(1233, result.end);
    }

    /** Test. */
    @Test
    public void translateShouldHandleLongRightRange() throws HttpException {
        ByteRange range = new ByteRange(-1, 12345);
        ByteRange result = instance.translate(range, 1234);
        assertEquals(0, result.start);  // entire file
        assertEquals(1233, result.end);
    }

    /** Test. */
    @Test
    public void translateShouldHandleCompleteRange() throws HttpException {
        ByteRange range = new ByteRange(123, 456);
        ByteRange result = instance.translate(range, 1234);
        assertEquals(123, result.start);
        assertEquals(456, result.end);
    }

    /** Test. */
    @Test
    public void translateShouldHandleLongCompleteRange() throws HttpException {
        ByteRange range = new ByteRange(123, 4567);
        ByteRange result = instance.translate(range, 1234);
        assertEquals(123, result.start);
        assertEquals(1233, result.end);
    }

    /** Test. */
    @Test (expected=HttpException.class)
    public void translateShouldThrowOnInvalidRange() throws HttpException {
        ByteRange range = new ByteRange(-1, -1);
        ByteRange result = instance.translate(range, 1234);
        assertNull(result);  // shouldn't be reached
    }
}
