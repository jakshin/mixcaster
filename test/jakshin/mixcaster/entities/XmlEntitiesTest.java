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

package jakshin.mixcaster.entities;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the XmlEntities class.
 */
public class XmlEntitiesTest {
    private XmlEntities instance;

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
        this.instance = new XmlEntities();
    }

    /** Scaffolding. */
    @After
    public void tearDown() {
    }

    /** Test. */
    @Test
    public void escapeShouldEscapeAllEntities() {
        String test = "& <> \"' jason";
        String expResult = "&amp; &lt;&gt; &quot;&apos; jason";
        String result = instance.escape(test);
        assertEquals(expResult, result);
    }

    /** Test. */
    @Test
    public void escapeShouldReturnNullWhenPassedNull() {
        String result = instance.escape(null);
        assertEquals(null, result);
    }

    /** Test. */
    @Test
    public void unescapeShouldUnescapeAllEntities() {
        String test = "&amp; &lt;&gt; &quot;&apos; jason &amp";
        String expResult = "& <> \"' jason &amp";
        String result = instance.unescape(test);
        assertEquals(expResult, result);
    }

    /** Test. */
    @Test
    public void unescapeShouldReturnNullWhenPassedNull() {
        String result = instance.unescape(null);
        assertEquals(null, result);
    }
}
