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

package jakshin.mixcloudpodcast.utils;

import java.io.*;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the FileUtils class.
 */
public class FileUtilsTest {
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
    public void readingAndWritingShouldWork() throws IOException, SecurityException {
        String fileName = System.getProperty("java.io.tmpdir") + "FileUtils.test";
        String str = "abc⪧Σ€\0\n";
        String result = this.writeThenRead(fileName, str, "UTF-8");
        assertEquals(str, result);
    }

    /** Test. */
    @Test
    public void readingAndWritingEmptyShouldWork() throws IOException, SecurityException {
        String fileName = System.getProperty("java.io.tmpdir") + "FileUtils-empty.test";
        String str = "";
        String result = this.writeThenRead(fileName, str, "UTF-8");
        assertEquals(str, result);
    }

    /** Helper method for some read/write tests. */
    private String writeThenRead(String fileName, String content, String charset)
            throws IOException, SecurityException {
        FileUtils.writeStringToFile(fileName, content, charset);
        String result = FileUtils.readFileIntoString(fileName, charset);

        File file = new File(fileName);
        if (!file.delete()) {
            fail("Invalid test because a temp file couldn't be deleted");
        }

        return result;
    }

    /** Test. */
    @Test (expected=FileNotFoundException.class)
    public void readingUnnamedShouldThrow() throws IOException, SecurityException {
        String result = FileUtils.readFileIntoString("", "UTF-8");
        assertEquals(null, result);  // shouldn't be reached
    }

    /** Test. */
    @Test (expected=FileNotFoundException.class)
    public void writingUnnamedShouldThrow() throws IOException, SecurityException {
        FileUtils.writeStringToFile("", "content", "UTF-8");
    }

    /** Test. */
    @Test (expected=UnsupportedEncodingException.class)
    public void readingInvalidCharsetShouldThrow() throws IOException, SecurityException {
        String result = FileUtils.readFileIntoString("/etc/hosts", "invalid");
        assertEquals(null, result);  // shouldn't be reached
    }

    /** Test. */
    @Test
    public void writingInvalidCharsetShouldThrow() throws IOException, SecurityException {
        String fileName = System.getProperty("java.io.tmpdir") + "FileUtils-invalid.test";
        File file = new File(fileName);
        if (file.exists()) {
            if (!file.delete()) {
                fail("Invalid test because an existing temp file couldn't be deleted");
            }
        }
        boolean thrown = false;

        try {
            FileUtils.writeStringToFile(fileName, "content", "invalid");
        }
        catch (UnsupportedEncodingException ex) {
            thrown = true;
        }

        if (!thrown) {
            fail("UnsupportedEncodingException not thrown");
        }
        if (file.exists()) {
            fail("Output file exists");
        }
    }

    /** Test. */
    @Test (expected=FileNotFoundException.class)
    public void readingDirectoryShouldThrow() throws IOException, SecurityException {
        String result = FileUtils.readFileIntoString("/etc/", "UTF-8");
        assertEquals(null, result);  // shouldn't be reached
    }

    /** Test. */
    @Test (expected=FileNotFoundException.class)
    public void writingDirectoryShouldThrow() throws IOException, SecurityException {
        FileUtils.writeStringToFile("/etc/", "content", "UTF-8");
    }

    /** Test. */
    @Test (expected=FileNotFoundException.class)
    public void readingInNonexistentDirectoryShouldThrow() throws IOException, SecurityException {
        String result = FileUtils.readFileIntoString("/does-not-exist/FileUtils.test", "UTF-8");
        assertEquals(null, result);  // shouldn't be reached
    }

    /** Test. */
    @Test (expected=FileNotFoundException.class)
    public void writingInNonexistentDirectoryShouldThrow() throws IOException, SecurityException {
        FileUtils.writeStringToFile("/does-not-exist/FileUtils.test", "content", "UTF-8");
    }
}
