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

package jakshin.mixcaster.stale;

import jakshin.mixcaster.mixcloud.MusicSet;
import jakshin.mixcaster.stale.attributes.LastUsedAttr;
import jakshin.mixcaster.stale.attributes.RssLastRequestedAttr;
import jakshin.mixcaster.stale.attributes.WatchesAttr;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;

import static jakshin.mixcaster.logging.Logging.logger;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Freshener class.
 */
@ExtendWith(MockitoExtension.class)
class FreshenerTest {
    @Mock
    private Freshener freshener;

    private static final Path path = Path.of("path/to/some.file");
    private static String originalMusicDirProperty;

    @BeforeAll
    static void beforeAll() {
        originalMusicDirProperty = System.getProperty("music_dir");
        System.setProperty("music_dir", path.toString());

        LogManager.getLogManager().reset();
        logger.setLevel(Level.OFF);
    }

    @AfterAll
    static void afterAll() {
        if (originalMusicDirProperty == null)
            System.clearProperty("music_dir");
        else
            System.setProperty("music_dir", originalMusicDirProperty);
    }

    @Nested
    @DisplayName("updateLastUsedAttr()")
    class UpdateLastUsedAttrTest {
        private LastUsedAttr mockAttr;

        @BeforeEach
        void setUp() throws IOException {
            mockAttr = mock(LastUsedAttr.class, withSettings().useConstructor(path));
            when(freshener.newLastUsedAttr(path)).thenReturn(mockAttr);

            when(mockAttr.isSupported()).thenReturn(true);

            doCallRealMethod().when(freshener).updateLastUsedAttr(any(), anyBoolean());
            lenient().doCallRealMethod().when(freshener).updateLastUsedAttr(any());
        }

        @Test
        void doesNothingIfUserDefinedAttributesAreNotSupported() throws IOException {
            when(mockAttr.isSupported()).thenReturn(false);

            freshener.updateLastUsedAttr(path);
            freshener.updateLastUsedAttr(path, false);
            freshener.updateLastUsedAttr(path, true);

            verify(mockAttr, never()).setValue(any());
        }

        @Test
        void updatesTheAttributeOnlyIfItExists() throws IOException {
            when(mockAttr.exists()).thenReturn(false);

            freshener.updateLastUsedAttr(path, true);
            verify(mockAttr, never()).setValue(any());  // doesn't exist, so we didn't set it

            when(mockAttr.exists()).thenReturn(true);

            freshener.updateLastUsedAttr(path, true);
            verify(mockAttr).setValue(any());  // did already exist, so we updated it
        }

        @Test
        void updatesTheAttributeWhetherItExistsOrNot() throws IOException {
            when(mockAttr.exists()).thenReturn(false);

            freshener.updateLastUsedAttr(path, false);
            verify(mockAttr).setValue(any());

            lenient().when(mockAttr.exists()).thenReturn(true);

            freshener.updateLastUsedAttr(path, false);
            verify(mockAttr, times(2)).setValue(any());
        }

        @Test
        void suppressesExceptions() throws IOException {
            doThrow(new IOException("Testing")).when(mockAttr).setValue(any());

            freshener.updateLastUsedAttr(path);

            verify(mockAttr).setValue(any());
        }
    }

    @Nested
    @DisplayName("updateRssLastRequestedAttr()")
    class UpdateRssLastRequestedAttrTest {
        private RssLastRequestedAttr mockAttr;

        @BeforeEach
        void setUp() throws IOException {
            mockAttr = mock(RssLastRequestedAttr.class, withSettings().useConstructor(path));
            when(freshener.newRssLastRequestedAttr(path)).thenReturn(mockAttr);

            when(mockAttr.isSupported()).thenReturn(true);

            doCallRealMethod().when(freshener).updateRssLastRequestedAttr();
        }

        @Test
        void doesNothingIfUserDefinedAttributesAreNotSupported() throws IOException {
            when(mockAttr.isSupported()).thenReturn(false);

            freshener.updateRssLastRequestedAttr();

            verify(mockAttr, never()).setValue(any());
        }

        @Test
        void updatesTheAttribute() throws IOException {
            when(mockAttr.exists()).thenReturn(false);

            freshener.updateRssLastRequestedAttr();
            verify(mockAttr).setValue(any());

            lenient().when(mockAttr.exists()).thenReturn(true);

            freshener.updateRssLastRequestedAttr();
            verify(mockAttr, times(2)).setValue(any());
        }

        @Test
        void suppressesExceptions() throws IOException {
            doThrow(new IOException("Testing")).when(mockAttr).setValue(any());

            freshener.updateRssLastRequestedAttr();

            verify(mockAttr).setValue(any());
        }
    }

    @Nested
    @DisplayName("updateWatchesAttr()")
    class UpdateWatchesAttrTest {
        private WatchesAttr mockAttr;

        private final Path path = Path.of("/dev/null");  // the path must really exist
        private final MusicSet fooShows = new MusicSet("foo", "shows", null);
        private final MusicSet barStream = new MusicSet("var", "stream", null);

        @BeforeEach
        void setUp() throws IOException {
            mockAttr = mock(WatchesAttr.class, withSettings().useConstructor(path));
            when(freshener.newWatchesAttr(path)).thenReturn(mockAttr);

            when(mockAttr.isSupported()).thenReturn(true);

            doCallRealMethod().when(freshener).updateWatchesAttr(any(), any());
        }

        @Test
        void doesNothingIfUserDefinedAttributesAreNotSupported() throws IOException {
            when(mockAttr.isSupported()).thenReturn(false);

            freshener.updateWatchesAttr(path, fooShows);

            verify(mockAttr, never()).setValue(any());
        }

        @Test
        void addsTheAttribute() throws IOException {
            LinkedList<MusicSet> noAttribute = new LinkedList<>();  // empty list == attribute doesn't exist
            when(mockAttr.getValue()).thenReturn(noAttribute);

            freshener.updateWatchesAttr(path, fooShows);

            verify(mockAttr).setValue(List.of(fooShows));
        }

        @Test
        void updatesTheAttribute() throws IOException {
            LinkedList<MusicSet> attrValue = new LinkedList<>();
            attrValue.add(fooShows);
            when(mockAttr.getValue()).thenReturn(attrValue);

            // add a music set that's not yet listed in the attribute
            freshener.updateWatchesAttr(path, barStream);

            verify(mockAttr).setValue(List.of(fooShows, barStream));
        }

        @Test
        void doesNotUpdateTheAttributeIfNotNeeded() throws IOException {
            LinkedList<MusicSet> attrValue = new LinkedList<>();
            attrValue.add(fooShows);
            attrValue.add(barStream);
            when(mockAttr.getValue()).thenReturn(attrValue);

            // add a music set that's already listed in the attribute
            freshener.updateWatchesAttr(path, barStream);

            verify(mockAttr, never()).setValue(any());
        }

        @Test
        void suppressesExceptions() throws IOException {
            doThrow(new IOException("Testing")).when(mockAttr).getValue();

            freshener.updateWatchesAttr(path, fooShows);

            verify(mockAttr).getValue();
        }
    }
}
