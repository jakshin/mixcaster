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

package jakshin.mixcaster.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServableFileTest {
    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void getLocalPathWorksWithFullUrl() {
        String expResult = System.getProperty("music_dir");
        expResult = expResult.replace("~", System.getProperty("user.home"));
        if (!expResult.endsWith("/")) expResult += "/";
        expResult += "Foo/ep1.m4a";

        String result = ServableFile.getLocalPath("http://localhost:6499/Foo/ep1.m4a");
        assertEquals(expResult, result);
    }

    @Test
    void getLocalPathWorksWithAbsoluteUrl() {
        System.setProperty("music_dir", "/music/");
        String expResult = "/music/Foo/ep1.m4a";

        String result = ServableFile.getLocalPath("/Foo/ep1.m4a");
        assertEquals(expResult, result);
    }

    @Test
    void getLocalPathIsSecure() {
        System.setProperty("music_dir", "/music");
        String[] urls = {"", ".", "..", "../..", "..//..", "//..//", "foo/..", "../foo/..", "..//foo//.." };

        for (var url : urls) {
            String result = ServableFile.getLocalPath(url);
            assertEquals("/music", result);
        }
    }
}
