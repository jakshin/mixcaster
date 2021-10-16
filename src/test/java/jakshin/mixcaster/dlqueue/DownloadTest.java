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

import java.util.Date;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the Download class.
 */
public class DownloadTest {
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
    public void equalsShouldWork() {
        Date date = new Date();
        Download baseline = new Download("remoteUrl", 42, date, "localFile");
        Download same = new Download("remoteUrl", 42, date, "localFile");
        assertEquals(baseline, same);

        Download diff1 = new Download("~~remoteUrl~~", 42, date, "localFile");
        Download diff2 = new Download("remoteUrl", 43, date, "localFile");
        Download diff3 = new Download("remoteUrl", 42, new Date(date.getTime() + 1), "localFile");
        Download diff4 = new Download("remoteUrl", 42, date, "~~localFile~~");
        assertEquals(baseline, diff1);  // remoteUrl isn't compared
        assertNotEquals(baseline, diff2);
        assertNotEquals(baseline, diff3);
        assertNotEquals(baseline, diff4);

        assertNotEquals(null, baseline);
        assertNotEquals(baseline, new Object());
    }

    @Test
    public void hashCodeShouldReturnTheSameValueForLikeInstances() {
        Date date = new Date();
        Download baseline = new Download("remoteUrl", 42, date, "localFile");
        Download same = new Download("remoteUrl", 42, date, "localFile");
        assertEquals(baseline.hashCode(), same.hashCode());
    }
}
