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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the DownloadComparator class.
 */
class DownloadComparatorTest {
    private final String url = "https://example.com/foo.m4a";
    private final String path = "/path/foo.m4a";

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void compareOldestFirstWorks() {
        DownloadComparator instance = new DownloadComparator(true);

        Date now = new Date();
        Download modifiedNow = new Download(url, 0, now, path);
        Download modifiedNowAgain = new Download(url, 0, new Date(now.getTime()), path);
        Download modifiedEarlier = new Download(url, 0, new Date(now.getTime() - 10_000), path);

        assertEquals(0, instance.compare(modifiedNow, modifiedNowAgain));
        assertEquals(1, instance.compare(modifiedNow, modifiedEarlier));
        assertEquals(-1, instance.compare(modifiedEarlier, modifiedNow));
    }

    @Test
    void compareNewestFirstWorks() {
        DownloadComparator instance = new DownloadComparator(false);

        Date now = new Date();
        Download modifiedNow = new Download(url, 0, now, path);
        Download modifiedNowAgain = new Download(url, 0, new Date(now.getTime()), path);
        Download modifiedEarlier = new Download(url, 0, new Date(now.getTime() - 10_000), path);

        assertEquals(0, instance.compare(modifiedNow, modifiedNowAgain));
        assertEquals(-1, instance.compare(modifiedNow, modifiedEarlier));
        assertEquals(1, instance.compare(modifiedEarlier, modifiedNow));
    }
}
