/*
 * Copyright (c) 2022 Jason Jackson
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
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.Closeable;
import java.io.IOException;

import static org.mockito.Mockito.*;

/**
 * Unit tests for the Closer class.
 */
class CloserTest {
    @Mock
    private MockCloseable mockCloseable;

    @BeforeEach
    void setUp() {
        mockCloseable = Mockito.mock(MockCloseable.class);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void closeWorks() throws IOException {
        Closer.close(mockCloseable, "Test object");
        verify(mockCloseable).close();
    }

    @Test
    void closeCatchesExceptions() throws IOException {
        doThrow(new IOException("Testing")).when(mockCloseable).close();
        Closer.close(mockCloseable, "Test object");
        verify(mockCloseable).close();
    }

    static class MockCloseable implements Closeable {
        @Override
        public void close() throws IOException {}
    }
}