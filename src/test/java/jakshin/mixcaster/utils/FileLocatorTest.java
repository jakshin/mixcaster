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

import jakshin.mixcaster.Main;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the FileLocator class.
 */
public class FileLocatorTest {
    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void getLocalPathShouldWorkWithFullUrl() {
        String expResult = Main.config.getProperty("music_dir");
        expResult = expResult.replace("~", System.getProperty("user.home"));
        if (!expResult.endsWith("/")) expResult += "/";
        expResult += "Foo/ep1.m4a";

        String result = FileLocator.getLocalPath("http://localhost:25683/Foo/ep1.m4a");
        assertEquals(expResult, result);
    }

    @Test
    public void getLocalPathShouldWorkWithAbsoluteUrl() {
        Main.config.setProperty("music_dir", "/music/");
        String expResult = "/music/Foo/ep1.m4a";

        String result = FileLocator.getLocalPath("/Foo/ep1.m4a");
        assertEquals(expResult, result);
    }

    @Test
    public void getLocalPathShouldBeSecure() {
        Main.config.setProperty("music_dir", "/music");
        String[] urls = {"", ".", "..", "../..", "..//..", "//..//", "foo/..", "../foo/..", "..//foo//.." };

        for (var url : urls) {
            String result = FileLocator.getLocalPath(url);
            assertEquals("/music", result);
        }
    }

    @Test
    public void makeLocalUrlShouldWork() {
        String host = Main.config.getProperty("http_hostname");
        String port = Main.config.getProperty("http_port");
        String expResult = "http://" + host + ":" + port + "/Foo/some-lovely-music.m4a";
        String result = FileLocator.makeLocalUrl(null, "Foo",
                "some-lovely-music", "https://stream.mixcloud.com/a/b/c/d.m4a?sig=blah");
        assertEquals(expResult, result);
    }
}
