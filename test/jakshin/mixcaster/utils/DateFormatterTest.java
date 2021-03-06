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

import java.text.ParseException;
import java.util.Date;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the DateFormatter class.
 */
public class DateFormatterTest {
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
    public void formatShouldWork() {
        Date date = new Date(1461292769391L);
        String expResult = "Fri, 22 Apr 2016 02:39:29 GMT";
        String result = DateFormatter.format(date);
        assertEquals(expResult, result);
    }

    /**
     * Test.
     * @throws ParseException
     */
    @Test
    public void parseShouldWork() throws ParseException {
        String dateStr = "Fri, 22 Apr 2016 02:39:29 GMT";
        long expResult = 1461292769000L;  // no milliseconds
        Date result = DateFormatter.parse(dateStr);
        assertEquals(expResult, result.getTime());
    }
}
