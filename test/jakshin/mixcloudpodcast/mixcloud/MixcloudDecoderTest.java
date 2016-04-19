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

package jakshin.mixcloudpodcast.mixcloud;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the MixcloudDecoder class.
 */
public class MixcloudDecoderTest {
    private MixcloudDecoder instance;

    /** Scaffolding. */
    public MixcloudDecoderTest() {
        this.instance = null;
    }

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
        this.instance = new MixcloudDecoder();
    }

    /** Scaffolding. */
    @After
    public void tearDown() {
    }

    /** Test. */
    @Test
    public void decodeShouldWork() {
        String playInfo = "C04WFQEABQIxARYDVVRMTQkQGwUBV1pcGhcGDQQMQ0NHHh0LFAMBAQNLFx8MRgdfAVEAXFNQQF5bU0BF" +
                        "QQpABAZZFl8LFhFdTkAKAwJfTVkXRl4UCVkSVgMWRlNRAhJCCFUSR0hPTB0ATU1OX1xVXV9DRF5GX0lBHB" +
                        "wIDUcrGRoaFCgcCwcUDBsePgAAUlZFQzJSUVdeRyFZWiteVyJJWzAzXVgyWltGRVNUMDBZQEw1M1tXNUUY";
        String expResult = "https://stream17.mixcloud.com/c/m4a/64/0/7/2/f/eb6c-fcb4-4bfc-90d2-cf7f1fb628fb.m4a";
        String result = instance.decode(playInfo);
        assertEquals(expResult, result);
    }

    /** Test. */
    @Test
    public void decodeShouldReturnNullOnFailure() {
        String playInfo = "invalid";
        String result = instance.decode(playInfo);
        assertEquals(null, result);
    }

    /** Test. */
    @Test
    public void decodeShouldReturnNullOnNullInput() {
        String result = instance.decode(null);
        assertEquals(null, result);
    }
}
