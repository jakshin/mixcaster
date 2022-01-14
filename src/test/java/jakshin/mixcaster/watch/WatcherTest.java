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

package jakshin.mixcaster.watch;

import jakshin.mixcaster.download.Downloader;
import jakshin.mixcaster.mixcloud.MixcloudException;
import jakshin.mixcaster.mixcloud.MusicSet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogManager;

import static jakshin.mixcaster.logging.Logging.logger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Watcher class.
 */
@ExtendWith(MockitoExtension.class)
class WatcherTest {
    private Watcher watcher;
    private Path mockConfigFile;
    private MockedStatic<Watcher> mockedWatcherStatic;
    private MockedConstruction<Downloader> mockedDownloaderConstruction;

    @BeforeAll
    static void beforeAll() {
        System.setProperty("watch_interval_minutes", "1000");

        LogManager.getLogManager().reset();
        logger.setLevel(Level.OFF);
    }

    @AfterAll
    static void afterAll() {
        System.clearProperty("watch_interval_minutes");
    }

    @BeforeEach
    void setUp() throws IOException {
        watcher = new Watcher();

        // create the file empty for now, we'll fill it during tests
        mockConfigFile = Files.createTempFile("mix-watches-", ".conf");

        mockedWatcherStatic = mockStatic(Watcher.class);
        mockedWatcherStatic.when(Watcher::isWatchingAnything).thenCallRealMethod();
        mockedWatcherStatic.when(() -> Watcher.isWatchingAnyOf(any())).thenCallRealMethod();
        mockedWatcherStatic.when(() -> Watcher.start(anyLong())).thenCallRealMethod();
        mockedWatcherStatic.when(Watcher::stop).thenCallRealMethod();
        mockedWatcherStatic.when(Watcher::getConfigFilePath).thenReturn(mockConfigFile);  // the sole actual mock
        mockedWatcherStatic.when(Watcher::loadOrRefreshSettings).thenCallRealMethod();
        mockedWatcherStatic.when(Watcher::getWatchIntervalMinutes).thenCallRealMethod();

        mockedDownloaderConstruction = mockConstruction(Downloader.class,
                (mock, context) -> {
                    // this gets called whenever Downloader's constructor is used,
                    // and initializes that specific instance of the Downloader mock
                });
    }

    @AfterEach
    void tearDown() throws IOException {
        Watcher.stop();

        mockedWatcherStatic.close();
        mockedDownloaderConstruction.close();

        if (mockConfigFile != null) {
            Files.deleteIfExists(mockConfigFile);
        }
    }

    private void createMockConfigFile() throws IOException {
        Files.writeString(mockConfigFile, """
                ArmadaMusicOfficial's playlist armada-trance-mixes
                OROCHIIO
                newagerage’s shows
                https://www.mixcloud.com/paulvandyk/uploads/
                """);
    }

    @Test
    void isWatchingAnythingWorks() throws IOException {
        assertThat(Watcher.isWatchingAnything()).isFalse();
        createMockConfigFile();

        watcher.run();

        assertThat(Watcher.isWatchingAnything()).isTrue();
    }

    @Test
    void isWatchingAnyOfWorks() throws IOException {
        List<MusicSet> sets = List.of(
                new MusicSet("some-rando", "favorites", null),
                new MusicSet("paulvandyk", "shows", null)  // in our mock config file
        );
        List<MusicSet> otherSets = List.of(
                // neither of these is in our mock config file
                new MusicSet("some-rando", "shows", null),
                new MusicSet("paulvandyk", "favorites", null)
        );

        assertThat(Watcher.isWatchingAnyOf(sets)).isFalse();
        createMockConfigFile();

        watcher.run();

        assertThat(Watcher.isWatchingAnyOf(sets)).isTrue();
        assertThat(Watcher.isWatchingAnyOf(otherSets)).isFalse();
    }

    @Test
    void startWorks() throws InterruptedException {
        try (MockedConstruction<Watcher> mockedConstruction = mockConstruction(Watcher.class,
                (mock, context) -> {
                    // this gets called whenever Watcher's constructor is used,
                    // and initializes that specific instance of the Watcher mock
                }))
        {
            Watcher.start(0);

            Thread.sleep(10);
            assertThat(mockedConstruction.constructed()).hasSize(1);

            // since we're already running, if start() is called again, it should do nothing
            Watcher.start(0);

            Thread.sleep(10);
            assertThat(mockedConstruction.constructed()).hasSize(1);
        }

    }

    @Test
    void loadsNewSettingsEveryRun() {
        watcher.run();
        mockedWatcherStatic.verify(Watcher::loadOrRefreshSettings);

        watcher.run();
        mockedWatcherStatic.verify(Watcher::loadOrRefreshSettings, times(2));
    }

    @Test
    void checksForNewMusic() throws IOException, MixcloudException,
            URISyntaxException, InterruptedException, TimeoutException {
        createMockConfigFile();  // contains 4 music sets
        int musicSetCount = 4;

        watcher.run();

        assertThat(mockedDownloaderConstruction.constructed()).hasSize(1);

        Downloader downloader = mockedDownloaderConstruction.constructed().get(0);
        verify(downloader, times(musicSetCount)).download(any(), eq(true));
    }

    @Test
    void doesNothingIfThereAreNoWatchesConfigured() {
        // our config file is empty, since we haven't called createMockConfigFile()
        watcher.run();
        assertThat(mockedDownloaderConstruction.constructed()).hasSize(0);
    }
}
