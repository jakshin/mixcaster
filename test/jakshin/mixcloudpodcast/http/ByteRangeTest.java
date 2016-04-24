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

package jakshin.mixcloudpodcast.http;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the ByteRange class.
 */
public class ByteRangeTest {
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
    }

    /** Scaffolding. */
    @After
    public void tearDown() {
    }

    /** Test. */
    @Test
    public void shouldParseEmptyRange() throws HttpException, NumberFormatException {
        ByteRange instance = new ByteRange("");
        assertNull(instance.start);
        assertNull(instance.end);
    }

    /** Test. */
    @Test
    public void shouldParseLeftRange() throws HttpException, NumberFormatException {
        ByteRange instance = new ByteRange("bytes=123-");
        assertEquals(123, instance.start.longValue());
        assertNull(instance.end);
    }

    /** Test. */
    @Test
    public void shouldParseRightRange() throws HttpException, NumberFormatException {
        ByteRange instance = new ByteRange("bytes=-456");
        assertNull(instance.start);
        assertEquals(456, instance.end.longValue());
    }

    /** Test. */
    @Test
    public void shouldParseCompleteRange() throws HttpException, NumberFormatException {
        ByteRange instance = new ByteRange("bytes=123-456");
        assertEquals(123, instance.start.longValue());
        assertEquals(456, instance.end.longValue());
    }

    /** Test. */
    @Test (expected=HttpException.class)
    public void shouldThrowOnNonByteRange() throws HttpException, NumberFormatException {
        ByteRange instance = new ByteRange("stuff=123-456");
        assertNull(instance);  // shouldn't be reached
    }

    /** Test. */
    @Test (expected=HttpException.class)
    public void shouldThrowOnMultipleRange() throws HttpException, NumberFormatException {
        ByteRange instance = new ByteRange("bytes=123-456,789-1000");
        assertNull(instance);  // shouldn't be reached
    }

    /** Test. */
    @Test (expected=HttpException.class)
    public void shouldThrowOnRangeWithoutDash() throws HttpException, NumberFormatException {
        ByteRange instance = new ByteRange("bytes=123");
        assertNull(instance);  // shouldn't be reached
    }

    /** Test. */
    @Test (expected=HttpException.class)
    public void shouldThrowOnRangeWithExtraDash() throws HttpException, NumberFormatException {
        ByteRange instance = new ByteRange("bytes=123-456-");
        assertNull(instance);  // shouldn't be reached
    }

    /** Test. */
    @Test (expected=NumberFormatException.class)
    public void shouldThrowOnNonNumericRange() throws HttpException, NumberFormatException {
        ByteRange instance = new ByteRange("bytes=123-foo");
        assertNull(instance);  // shouldn't be reached
    }

    /** Test. */
    @Test (expected=HttpException.class)
    public void shouldThrowOnInvalidRange() throws HttpException, NumberFormatException {
        ByteRange instance = new ByteRange("bytes=456-123");  // start is greater than end
        assertNull(instance);  // shouldn't be reached
    }
}
