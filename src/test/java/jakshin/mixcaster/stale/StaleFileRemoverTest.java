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

import jakshin.mixcaster.TestUtilities;
import jakshin.mixcaster.stale.attributes.LastUsedAttr;
import jakshin.mixcaster.stale.attributes.RssLastRequestedAttr;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;

import static jakshin.mixcaster.logging.Logging.logger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the StaleFileRemover class.
 */
@ExtendWith(MockitoExtension.class)
class StaleFileRemoverTest {
    @Mock
    private StaleFileRemover remover;

    private Path mockMusicDir;

    @BeforeAll
    static void beforeAll() {
        LogManager.getLogManager().reset();
        logger.setLevel(Level.OFF);

        // don't put a goofy Java icon in macOS's dock while tests run
        System.setProperty("apple.awt.UIElement", "true");
    }

    @BeforeEach
    void setUp() {
        System.setProperty("music_dir", "/dev/null");
        System.setProperty("remove_stale_music_files_after_days", "1000");
    }

    @AfterEach
    void tearDown() {
        remover.stop();
        System.clearProperty("music_dir");
        System.clearProperty("remove_stale_music_files_after_days");
        TestUtilities.removeTempDirectory(mockMusicDir);
    }

    @Test
    void doesNotRunIfTheFeatureIsDisabled() throws InterruptedException {
        doCallRealMethod().when(remover).start(anyLong(), anyLong(), any());
        doCallRealMethod().when(remover).stop();
        System.setProperty("remove_stale_music_files_after_days", "0");

        boolean result = remover.start(1, 1, TimeUnit.MILLISECONDS);
        Thread.sleep(10);

        verify(remover, never()).run();
        assertThat(result).isFalse();
    }

    @Test
    void doesNotRunIfAnyInstanceIsAlreadyRunning() {
        doCallRealMethod().when(remover).start(anyLong(), anyLong(), any());
        doCallRealMethod().when(remover).stop();

        boolean result = remover.start(1, 1, TimeUnit.MILLISECONDS);
        assertThat(result).isTrue();  // sanity check

        StaleFileRemover remover2 = new StaleFileRemover();
        boolean result2 = remover2.start(1000, 1000, TimeUnit.SECONDS);
        assertThat(result2).isFalse();
    }

    @Test
    void runsPeriodically() throws InterruptedException {
        doCallRealMethod().when(remover).start(anyLong(), anyLong(), any());
        doCallRealMethod().when(remover).stop();

        boolean result = remover.start(1, 1, TimeUnit.MILLISECONDS);
        Thread.sleep(10);

        verify(remover, atLeast(2)).run();
        assertThat(result).isTrue();
    }

    @Test
    void stopsWhenRequested() throws InterruptedException {
        doCallRealMethod().when(remover).start(anyLong(), anyLong(), any());
        doCallRealMethod().when(remover).stop();

        remover.start(1, 1, TimeUnit.MILLISECONDS);
        Thread.sleep(10);
        remover.stop();
        reset(remover);
        Thread.sleep(10);

        verify(remover, never()).run();
    }

    @Test
    void removesOnlyStaleFiles() throws IOException {
        doCallRealMethod().when(remover).run();

        mockMusicDir = Files.createTempDirectory("mix-sfr-");
        Path emptyDir = Path.of(mockMusicDir.toString(), "empty");
        Path subdir = Path.of(mockMusicDir.toString(), "subdir");
        Path noAttribute = Path.of(subdir.toString(), "no-attribute");
        Path stale = Path.of(subdir.toString(), "stale");
        Path fresh = Path.of(subdir.toString(), "fresh");

        Files.createDirectory(emptyDir);
        Files.createDirectory(subdir);
        Files.createFile(noAttribute);
        Files.createFile(stale);
        Files.createFile(fresh);

        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        new LastUsedAttr(emptyDir).setValue(nowUtc.minusDays(3));
        new LastUsedAttr(stale).setValue(nowUtc.minusDays(3));
        new LastUsedAttr(fresh).setValue(nowUtc.minusHours(23));

        System.setProperty("music_dir", mockMusicDir.toString());
        System.setProperty("remove_stale_music_files_after_days", "1");

        // the stale file should be deleted, but the rest shouldn't
        remover.run();

        assertThat(emptyDir).exists();
        assertThat(noAttribute).exists();
        assertThat(stale).doesNotExist();
        assertThat(fresh).exists();

        // now put an RssLastRequested attribute on the mock music dir, and run again;
        // this time the stale file shouldn't be deleted, because it's not in a watch
        Files.createFile(stale);
        new LastUsedAttr(stale).setValue(nowUtc.minusDays(3));
        new RssLastRequestedAttr(mockMusicDir).setValue(nowUtc.minusDays(2));

        remover.run();

        assertThat(emptyDir).exists();
        assertThat(noAttribute).exists();
        assertThat(stale).exists();
        assertThat(fresh).exists();
    }
}
