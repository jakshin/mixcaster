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

import jakshin.mixcaster.utils.AppSettings;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for the DownloadQueue class.
 */
class DownloadQueueTest {
    @BeforeAll
    static void beforeAll() throws IOException {
        AppSettings.initSettings();
    }

    @AfterAll
    static void afterAll() {
    }

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void getInstanceAlwaysReturnsTheSameInstance() {
        DownloadQueue q1 = DownloadQueue.getInstance();
        DownloadQueue q2 = DownloadQueue.getInstance();
        assertSame(q1, q2);
    }

    @Test
    void enqueueWorks() {
        DownloadQueue q = DownloadQueue.getInstance();
        int size = q.queueSize();

        Download download = new Download("http://example.com/foo", 42, new Date(), "foo");
        q.enqueue(download);  // should be queued
        assertEquals(++size, q.queueSize());

        q.enqueue(download);  // should not be queued a second time
        assertEquals(size, q.queueSize());
    }
}
