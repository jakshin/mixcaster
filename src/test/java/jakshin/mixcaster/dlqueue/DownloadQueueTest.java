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

import java.io.IOException;
import java.util.Date;

import jakshin.mixcaster.utils.AppSettings;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the DownloadQueue class.
 */
public class DownloadQueueTest {
    @BeforeClass
    public static void setUpClass() throws IOException {
        AppSettings.initSettings();
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
    public void getInstanceShouldAlwaysReturnTheSameInstance() {
        DownloadQueue q1 = DownloadQueue.getInstance();
        DownloadQueue q2 = DownloadQueue.getInstance();
        assertSame(q1, q2);
    }

    @Test
    public void enqueueShouldWork() {
        DownloadQueue q = DownloadQueue.getInstance();
        int size = q.queueSize();

        Download download = new Download("http://example.com/foo", 42, new Date(), "foo");
        q.enqueue(download);  // should be queued
        assertEquals(++size, q.queueSize());

        q.enqueue(download);  // should not be queued a second time
        assertEquals(size, q.queueSize());
    }
}
