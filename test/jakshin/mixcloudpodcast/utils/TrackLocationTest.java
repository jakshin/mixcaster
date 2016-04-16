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

import jakshin.mixcloudpodcast.Main;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the TrackLocation class.
 */
public class TrackLocationTest {
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
    public void getLocalUrlShouldWorkWithScrapeConstructor() {
        TrackLocation instance = new TrackLocation("http://www.mixcloud.com/Foo/",
                "http://www.mixcloud.com/Foo/ep1/", "https://foo.com/a/b/c/d.m4a");
        String host = Main.config.getProperty("http_hostname");
        String port = Main.config.getProperty("http_port");
        String expResult = "http://" + host + ":" + port + "/Foo/ep1.m4a";
        String result = instance.getLocalUrl();
        assertEquals(expResult, result);
    }

    /** Test. */
    @Test
    public void getLocalPathShouldWorkWithScrapeConstructor() {
        TrackLocation instance = new TrackLocation("http://www.mixcloud.com/Foo",
                "http://www.mixcloud.com/Foo/ep1", "https://foo.com/a/b/c/d.m4a");

        String expResult = Main.config.getProperty("music_dir");
        expResult = expResult.replace("~", System.getProperty("user.home"));
        if (!expResult.endsWith("/")) expResult += "/";
        expResult += "Foo/ep1.m4a";

        String result = instance.getLocalPath();
        assertEquals(expResult, result);
    }

    /** Test. */
    @Test
    public void getLocalUrlShouldWorkWithHttpConstructor() {
        TrackLocation instance = new TrackLocation("http://localhost:1234/Foo/ep1.m4a");
        String host = Main.config.getProperty("http_hostname");
        String port = Main.config.getProperty("http_port");
        String expResult = "http://" + host + ":" + port + "/Foo/ep1.m4a";
        String result = instance.getLocalUrl();
        assertEquals(expResult, result);
    }

    /** Test. */
    @Test
    public void getLocalPathShouldWorkWithHttpConstructor() {
        TrackLocation instance = new TrackLocation("http://localhost:1234/Foo/ep1.m4a");

        String expResult = Main.config.getProperty("music_dir");
        expResult = expResult.replace("~", System.getProperty("user.home"));
        if (!expResult.endsWith("/")) expResult += "/";
        expResult += "Foo/ep1.m4a";

        String result = instance.getLocalPath();
        assertEquals(expResult, result);
    }
}
