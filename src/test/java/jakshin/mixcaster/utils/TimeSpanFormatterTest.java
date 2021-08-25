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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the TimeSpanFormatter class.
 */
public class TimeSpanFormatterTest {
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
    public void formatShouldWorkWithHoursMinutesSeconds() {
        String result = TimeSpanFormatter.formatTimeSpan(90_610);
        assertEquals("25:10:10", result);
    }

    /** Test. */
    @Test
    public void formatShouldWorkWithHoursMinuteSecond() {
        String result = TimeSpanFormatter.formatTimeSpan(7261);
        assertEquals("2:01:01", result);
    }

    /** Test. */
    @Test
    public void formatShouldWorkWithHour() {
        String result = TimeSpanFormatter.formatTimeSpan(3600);
        assertEquals("1:00:00", result);
    }

    /** Test. */
    @Test
    public void formatShouldWorkWithMinutesSeconds() {
        String result = TimeSpanFormatter.formatTimeSpan(754);
        assertEquals("12:34", result);
    }

    /** Test. */
    @Test
    public void formatShouldWorkWithMinuteSeconds() {
        String result = TimeSpanFormatter.formatTimeSpan(62);
        assertEquals("1:02", result);
    }

    /** Test. */
    @Test
    public void formatShouldWorkWithSeconds() {
        String result = TimeSpanFormatter.formatTimeSpan(10);
        assertEquals("0:10", result);
    }

    /** Test. */
    @Test
    public void formatShouldWorkWithSecond() {
        String result = TimeSpanFormatter.formatTimeSpan(1);
        assertEquals("0:01", result);
    }

    /** Test. */
    @Test
    public void formatShouldWorkWithNoTime() {
        String result = TimeSpanFormatter.formatTimeSpan(0);
        assertEquals("0:00", result);
    }

    /** Test. */
    @Test
    public void formatShouldWorkWithNegatives() {
        String result = TimeSpanFormatter.formatTimeSpan(-90_610);
        assertEquals("-25:10:10", result);

        result = TimeSpanFormatter.formatTimeSpan(-7261);
        assertEquals("-2:01:01", result);

        result = TimeSpanFormatter.formatTimeSpan(-3600);
        assertEquals("-1:00:00", result);

        result = TimeSpanFormatter.formatTimeSpan(-754);
        assertEquals("-12:34", result);

        result = TimeSpanFormatter.formatTimeSpan(-62);
        assertEquals("-1:02", result);

        result = TimeSpanFormatter.formatTimeSpan(-10);
        assertEquals("-0:10", result);

        result = TimeSpanFormatter.formatTimeSpan(-1);
        assertEquals("-0:01", result);

        result = TimeSpanFormatter.formatTimeSpan(-0);
        assertEquals("0:00", result);
    }
}
