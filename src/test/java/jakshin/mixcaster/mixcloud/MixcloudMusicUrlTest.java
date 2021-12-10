/*
 * Copyright (c) 2021 Jason Jackson
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

package jakshin.mixcaster.mixcloud;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MixcloudMusicUrlTest {
    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void localUrlWorks() {
        String host = System.getProperty("http_hostname") + ":" + System.getProperty("http_port");
        String expResult = "http://" + host + "/Somebody/some-lovely-music.m4a";

        var mixcloudMusicUrl = new MixcloudMusicUrl("https://stream.mixcloud.com/a/b/c/d.m4a?sig=blah");
        String result = mixcloudMusicUrl.localUrl(host, "Somebody", "some-lovely-music");

        assertEquals(expResult, result);
    }
}
