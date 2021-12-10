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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the MimeHelper class.
 */
class MimeHelperTest {
    private MimeHelper instance;

    @BeforeEach
    void setUp() {
        this.instance = new MimeHelper();
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void guessContentTypeWorksForAudioFile() {
        String expResult = "audio/mp4";
        String result = instance.guessContentTypeFromName("foo.m4a");  // in our map of known MIME types
        assertEquals(expResult, result);
    }

    @Test
    void guessContentTypeWorksForMiscFile() {
        String expResult = "image/jpeg";
        String result = instance.guessContentTypeFromName("foo.jpg");  // not in our map of known MIME types
        assertEquals(expResult, result);
    }

    @Test
    void guessContentTypeReturnsDefaultForUnknownExtension() {
        String expResult = "application/octet-stream";
        String result = instance.guessContentTypeFromName("foo.unknown");
        assertEquals(expResult, result);
    }

    @Test
    void guessContentTypeReturnsDefaultForFileWithNoExtension() {
        String expResult = "application/octet-stream";
        String result = instance.guessContentTypeFromName("foo");
        assertEquals(expResult, result);
    }

    @Test
    void guessContentTypeReturnsDefaultForDotFile() {
        String expResult = "application/octet-stream";
        String result = instance.guessContentTypeFromName(".foo");
        assertEquals(expResult, result);
    }
}
