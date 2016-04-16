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

package jakshin.mixcloudpodcast.download;

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
    public void equalsShouldWork() {
        Date date = new Date();
        Download baseline = new Download("remoteUrl", 42, date, "localFile");
        Download same = new Download("remoteUrl", 42, date, "localFile");
        assertEquals(true, baseline.equals(same));

        Download diff1 = new Download("~~remoteUrl~~", 42, date, "localFile");
        Download diff2 = new Download("remoteUrl", 43, date, "localFile");
        Download diff3 = new Download("remoteUrl", 42, new Date(date.getTime() + 1), "localFile");
        Download diff4 = new Download("remoteUrl", 42, date, "~~localFile~~");
        assertEquals(false, baseline.equals(diff1));
        assertEquals(false, baseline.equals(diff2));
        assertEquals(false, baseline.equals(diff3));
        assertEquals(false, baseline.equals(diff4));

        assertEquals(false, baseline.equals(null));
        assertEquals(false, baseline.equals(new Object()));
    }

    /** Test. */
    @Test
    public void hashCodeShouldReturnTheSameValueForLikeInstances() {
        Date date = new Date();
        Download baseline = new Download("remoteUrl", 42, date, "localFile");
        Download same = new Download("remoteUrl", 42, date, "localFile");
        assertEquals(baseline.hashCode(), same.hashCode());
    }
}
