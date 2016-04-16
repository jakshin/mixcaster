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

package jakshin.mixcloudpodcast.entities;

import java.util.Map;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the HtmlEntities class.
 */
public class HtmlEntitiesTest {
    private HtmlEntities instance;

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
        this.instance = new HtmlEntities();
    }

    /** Scaffolding. */
    @After
    public void tearDown() {
    }

    /** Test. */
    @Test
    public void unescapeShouldUnescapeAllEntities() {
        StringBuilder test = new StringBuilder(20_000);
        StringBuilder expResult = new StringBuilder(2_500);

        for (Map.Entry<String,String> entry : HtmlEntities.entities.entrySet()) {
            test.append(entry.getKey());
            expResult.append(entry.getValue());
        }

        String result = instance.unescape(test.toString());
        assertEquals(expResult.toString(), result);
    }

    /** Test. */
    @Test
    public void unescapeShouldUnescapeEntities() {
        String result = instance.unescape("&gt; &gt &gtcc; &gtcc &foo ");
        assertEquals("> > ⪧ >cc &foo ", result);

        result = instance.unescape("&gt; &gt");
        assertEquals("> >", result);

        result = instance.unescape("&gt&gt;");
        assertEquals(">>", result);
    }

    /** Test. */
    @Test
    public void unescapeShouldUnescapeNumericRefs() {
        String result = instance.unescape("&#931; &#0931; &#x3A3; &#x03A3; &#X3a3; &#X03a3;");
        assertEquals("Σ Σ Σ Σ Σ Σ", result);

        result = instance.unescape("&#8364; &#8364");
        assertEquals("€ €", result);

        result = instance.unescape("&#8364&#8364;");
        assertEquals("€€", result);

        result = instance.unescape("foo &#999999999999 bar");
        assertEquals("foo &#999999999999 bar", result);
    }

    /** Test. */
    @Test
    public void unescapeShouldIgnoreIncompleteEntitiesAndRefs() {
        String result = instance.unescape("&#; &#x;");
        assertEquals("&#; &#x;", result);

        result = instance.unescape("ends with incomplete decimal &#");
        assertEquals("ends with incomplete decimal &#", result);

        result = instance.unescape("ends with incomplete hex &#x");
        assertEquals("ends with incomplete hex &#x", result);

        result = instance.unescape("&gt&");
        assertEquals(">&", result);

        result = instance.unescape("&#8364&");
        assertEquals("€&", result);
    }

    /** Test. */
    @Test
    public void unescapeShouldNotAlterStringsWithNoEntities() {
        String test = "no entities here";
        String expResult = "no entities here";
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
