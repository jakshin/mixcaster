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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the TimeSpanFormatter class.
 */
class TimeSpanFormatterTest {
    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void formatWorksWithHoursMinutesSeconds() {
        String result = TimeSpanFormatter.formatTimeSpan(90_610);
        assertEquals("25:10:10", result);
    }

    @Test
    void formatWorksWithHoursMinuteSecond() {
        String result = TimeSpanFormatter.formatTimeSpan(7261);
        assertEquals("2:01:01", result);
    }

    @Test
    void formatWorksWithHour() {
        String result = TimeSpanFormatter.formatTimeSpan(3600);
        assertEquals("1:00:00", result);
    }

    @Test
    void formatWorksWithMinutesSeconds() {
        String result = TimeSpanFormatter.formatTimeSpan(754);
        assertEquals("12:34", result);
    }

    @Test
    void formatWorksWithMinuteSeconds() {
        String result = TimeSpanFormatter.formatTimeSpan(62);
        assertEquals("1:02", result);
    }

    @Test
    void formatWorksWithSeconds() {
        String result = TimeSpanFormatter.formatTimeSpan(10);
        assertEquals("0:10", result);
    }

    @Test
    void formatWorksWithSecond() {
        String result = TimeSpanFormatter.formatTimeSpan(1);
        assertEquals("0:01", result);
    }

    @Test
    void formatWorksWithNoTime() {
        String result = TimeSpanFormatter.formatTimeSpan(0);
        assertEquals("0:00", result);
    }

    @Test
    void formatWorksWithNegatives() {
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
