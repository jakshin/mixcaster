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
 * Unit tests for the XmlEntities class.
 */
class XmlEntitiesTest {
    private XmlEntities instance;

    @BeforeEach
    void setUp() {
        this.instance = new XmlEntities();
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void escapeEscapesAllEntities() {
        String test = "& <> \"' jason";
        String expResult = "&amp; &lt;&gt; &quot;&apos; jason";
        String result = instance.escape(test);
        assertEquals(expResult, result);
    }
}
