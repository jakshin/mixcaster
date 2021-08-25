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

package jakshin.mixcaster.utils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the MimeTyper class.
 */
public class MimeTyperTest {
    private MimeTyper instance;

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
        this.instance = new MimeTyper();
    }

    /** Scaffolding. */
    @After
    public void tearDown() {
    }

    /** Test. */
    @Test
    public void guessContentTypeShouldWorkForAudioFile() {
        String expResult = "audio/mp4";
        String result = instance.guessContentTypeFromName("foo.m4a");  // in our map of known MIME types
        assertEquals(expResult, result);
    }

    /** Test. */
    @Test
    public void guessContentTypeShouldWorkForMiscFile() {
        String expResult = "image/jpeg";
        String result = instance.guessContentTypeFromName("foo.jpg");  // not in our map of known MIME types
        assertEquals(expResult, result);
    }

    /** Test. */
    @Test
    public void guessContentTypeShouldReturnDefaultForUnknownExtension() {
        String expResult = "application/octet-stream";
        String result = instance.guessContentTypeFromName("foo.unknown");
        assertEquals(expResult, result);
    }

    /** Test. */
    @Test
    public void guessContentTypeShouldReturnDefaultForFileWithNoExtension() {
        String expResult = "application/octet-stream";
        String result = instance.guessContentTypeFromName("foo");
        assertEquals(expResult, result);
    }

    /** Test. */
    @Test
    public void guessContentTypeShouldReturnDefaultForDotFile() {
        String expResult = "application/octet-stream";
        String result = instance.guessContentTypeFromName(".foo");
        assertEquals(expResult, result);
    }

    /** Test. */
    @Test
    public void guessContentTypeShouldReturnNullForNull() {
        String result = instance.guessContentTypeFromName(null);
        assertEquals(null, result);
    }
}
