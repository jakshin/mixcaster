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

package jakshin.mixcaster.dlqueue;

import jakshin.mixcaster.mixcloud.MusicSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit tests for the Download class.
 */
class DownloadTest {
    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void equalsWorks() {
        Date date = new Date();
        Download baseline = new Download("remoteUrl", 42, date, "localFile");
        Download same = new Download("remoteUrl", 42, date, "localFile");
        assertEquals(baseline, same);

        Download diff1 = new Download("~~remoteUrl~~", 42, date, "localFile");
        Download diff2 = new Download("remoteUrl", 43, date, "localFile");
        Download diff3 = new Download("remoteUrl", 42, new Date(date.getTime() + 1), "localFile");
        Download diff4 = new Download("remoteUrl", 42, date, "~~localFile~~");
        Download diff5 = new Download("remoteUrl", 42, date, "localFile",
                new MusicSet("user", "shows", null));
        assertEquals(baseline, diff1);  // remoteUrl isn't compared
        assertNotEquals(baseline, diff2);
        assertNotEquals(baseline, diff3);
        assertNotEquals(baseline, diff4);
        assertEquals(baseline, diff5);  // inWatchedSet isn't compared

        assertNotEquals(null, baseline);
        assertNotEquals(baseline, new Object());
    }

    @Test
    void hashCodeReturnsTheSameValueForLikeInstances() {
        Date date = new Date();
        Download baseline = new Download("remoteUrl", 42, date, "localFile");
        Download same = new Download("remoteUrl", 42, date, "localFile");
        assertEquals(baseline.hashCode(), same.hashCode());

        Download equivalent = new Download("~~remoteUrl~~", 42, date, "localFile",
                new MusicSet("user", "shows", null));
        assertEquals(baseline.hashCode(), equivalent.hashCode());
    }
}
