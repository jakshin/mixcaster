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

package jakshin.mixcaster.download;

import java.util.Date;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the DownloadComparator class.
 */
public class DownloadComparatorTest {
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
    public void compareOldestFirstShouldWork() {
        DownloadComparator instance = new DownloadComparator(true);

        Date now = new Date();
        Download modifiedNow = new Download(null, 0, now, null);
        Download modifiedNowAgain = new Download(null, 0, new Date(now.getTime()), null);
        Download modifiedEarlier = new Download(null, 0, new Date(now.getTime() - 10_000), null);

        assertEquals(0, instance.compare(modifiedNow, modifiedNowAgain));
        assertEquals(1, instance.compare(modifiedNow, modifiedEarlier));
        assertEquals(-1, instance.compare(modifiedEarlier, modifiedNow));
    }

    /** Test. */
    @Test
    public void compareNewestFirstShouldWork() {
        DownloadComparator instance = new DownloadComparator(false);

        Date now = new Date();
        Download modifiedNow = new Download(null, 0, now, null);
        Download modifiedNowAgain = new Download(null, 0, new Date(now.getTime()), null);
        Download modifiedEarlier = new Download(null, 0, new Date(now.getTime() - 10_000), null);

        assertEquals(0, instance.compare(modifiedNow, modifiedNowAgain));
        assertEquals(-1, instance.compare(modifiedNow, modifiedEarlier));
        assertEquals(1, instance.compare(modifiedEarlier, modifiedNow));
    }
}
