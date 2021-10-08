/*
 * Copyright (c) 2021 Jason Jackson
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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class MemoryCacheTest {
    MemoryCache<String, Integer> instance;

    @Before
    public void setUp() {
        instance = new MemoryCache<>(1);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void putOverwritesExistingItems() {
        instance.put("answer", 42);
        instance.put("answer", 43);

        Integer result = instance.get("answer");
        assertEquals(Integer.valueOf(43), result);
    }

    @Test
    public void getReturnsCachedItems() {
        instance.put("Jordan", 23);
        assertEquals(Integer.valueOf(23), instance.get("Jordan"));
    }

    @Test
    public void getDoesNotReturnExpiredItems() throws InterruptedException {
        instance.put("Curry", 20);
        Thread.sleep(1000);
        assertNull(instance.get("Jordan"));
    }

    @Test
    public void getDoesNotReturnNonExistentItems() {
        assertNull(instance.get("it's not in there"));
    }

    @Test
    public void scrubRemovesExpiredItems() throws InterruptedException {
        assertFalse(instance.scrub());

        instance.put("one", 1);
        instance.put("two", 2);
        instance.put("three", 3);
        Thread.sleep(1000);
        instance.put("two", 2);  // overwrite with a newer value
        instance.put("four", 4);
        assertTrue(instance.scrub());

        assertNull(instance.get("one"));
        assertNull(instance.get("three"));
        assertEquals(Integer.valueOf(2), instance.get("two"));
        assertEquals(Integer.valueOf(4), instance.get("four"));
    }
}