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

package jakshin.mixcaster.download;

import jakshin.mixcaster.TestUtilities;
import jakshin.mixcaster.dlqueue.Download;
import jakshin.mixcaster.dlqueue.DownloadQueue;
import jakshin.mixcaster.mixcloud.*;
import jakshin.mixcaster.podcast.Podcast;
import jakshin.mixcaster.stale.attributes.RssLastRequestedAttr;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogManager;

import static jakshin.mixcaster.logging.Logging.logger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Downloader class.
 */
@ExtendWith(MockitoExtension.class)
class DownloaderTest {
    private static final Random rand = new Random();

    private Downloader downloader;

    private DownloadQueue mockDownloadQueue;
    private MockedStatic<DownloadQueue> mockedStaticForDownloadQueue;
    private Podcast mockPodcast;
    private MockedConstruction<MixcloudClient> mockedConstructionForMixcloudClient;

    @BeforeEach
    void setUp() {
        System.setProperty("music_dir", "/dev/null");

        downloader = new Downloader(null);

        mockDownloadQueue = mock(DownloadQueue.class);
        mockedStaticForDownloadQueue = mockStatic(DownloadQueue.class);
        mockedStaticForDownloadQueue.when(DownloadQueue::getInstance).thenReturn(mockDownloadQueue);

        mockPodcast = TestUtilities.createMockPodcast();
        mockedConstructionForMixcloudClient = mockConstruction(MixcloudClient.class,
                (mock, context) -> {
                    // this gets called when a MixcloudClient constructor is used,
                    // and initializes that specific instance of the MixcloudClient mock

                    when(mock.queryDefaultView(anyString())).thenReturn("favorites");

                    when(mock.query(any())).thenAnswer(invocation -> {
                        MusicSet queried = invocation.getArgument(0);

                        if ("does-not-exist".equals(queried.username()))
                            throw new MixcloudUserException("Mock exception", "does-not-exist");

                        if ("does-not-exist".equals(queried.playlist()))
                            throw new MixcloudPlaylistException("Mock exception", "artist", "does-not-exist");

                        return mockPodcast;
                    });
                });

        LogManager.getLogManager().reset();
        logger.setLevel(Level.OFF);
    }

    @AfterEach
    void tearDown() {
        mockedStaticForDownloadQueue.close();
        mockedConstructionForMixcloudClient.close();

        System.clearProperty("music_dir");
    }

    @Test
    void rejectsInvalidArguments() {
        List<String> invalids = List.of(
                "extra ArmadaMusicOfficial shows",
                "ArmadaMusicOfficial extra shows",
                "ArmadaMusicOfficial shows extra",
                "ArmadaMusicOfficial invalid",
                "ArmadaMusicOfficial playlist",  // missing a playlist name
                "ArmadaMusicOfficial shows playlist-name"
        );

        for (String invalid : invalids) {
            String[] args = invalid.split("\\s+");

            assertThatThrownBy(() -> downloader.download(args, rand.nextBoolean()))
                    .isInstanceOf(MusicSet.InvalidInputException.class);

            assertThat(mockedConstructionForMixcloudClient.constructed()).isEmpty();
            verify(mockDownloadQueue, never()).enqueue(any());
            verify(mockDownloadQueue, never()).processQueue(any());
        }
    }

    @Test
    void queriesMixcloudBasedOnItsArguments() throws MixcloudException, IOException,
                            URISyntaxException, InterruptedException, TimeoutException {

        String artist = "ArmadaMusicOfficial";
        List<String> musicTypes = List.of("stream", "shows", "favorites", "history",
                                        "uploads", "listens", "playlist", "playlists");

        for (String musicType : musicTypes) {
            String playlist = musicType.startsWith("playlist") ? "playlist-slug" : null;
            String[] args = (playlist != null)
                    ? new String[] { artist, musicType, playlist }
                    : new String[] { artist, musicType };

            int result = downloader.download(args, rand.nextBoolean());

            assertThat(result).isEqualTo(0);

            int index = mockedConstructionForMixcloudClient.constructed().size() - 1;
            MixcloudClient latestClient = mockedConstructionForMixcloudClient.constructed().get(index);
            verify(latestClient).query(new MusicSet(artist, musicType, playlist));
        }

        // since we passed a music type every time, we had no need to call queryDefaultView();
        // also see the resolvesDefaultViewIfNeeded() test below
        for (MixcloudClient client : mockedConstructionForMixcloudClient.constructed()) {
            verify(client, never()).queryDefaultView(anyString());
        }
    }

    @Test
    void acceptsMixcloudUrlAsItsArgument() throws MixcloudException, IOException,
                            URISyntaxException, InterruptedException, TimeoutException {

        String artist = "ArmadaMusicOfficial";
        List<String> nextUrlParts = List.of("stream", "uploads", "favorites", "listens", "playlists");

        for (String part : nextUrlParts) {
            String playlist = part.equals("playlists") ? "armada-radio" : "";
            String url = "https://www.mixcloud.com/" + artist + "/" + part + "/" + playlist;
            if (! url.endsWith("/")) url += "/";
            String[] args = new String[] { url };

            int result = downloader.download(args, rand.nextBoolean());

            assertThat(result).isEqualTo(0);

            int index = mockedConstructionForMixcloudClient.constructed().size() - 1;
            MixcloudClient latestClient = mockedConstructionForMixcloudClient.constructed().get(index);
            if (playlist.isBlank()) playlist = null;  // for the MusicSet constructor
            verify(latestClient).query(new MusicSet(artist, part, playlist));
        }
    }

    @Test
    void startsDownloadingMusicFiles() throws MixcloudException, IOException,
                            URISyntaxException, InterruptedException, TimeoutException {

        final int[] queuedFileCount = {0};
        when(mockDownloadQueue.queueSize()).then(invocation -> queuedFileCount[0]);

        when(mockDownloadQueue.enqueue(any())).then(invocation -> {
            queuedFileCount[0]++;
            return true;
        });

        String[] args = new String[] { "artist", "shows" };

        downloader.download(args, rand.nextBoolean());

        assertThat(queuedFileCount[0]).isEqualTo(mockPodcast.episodes.size());
        verify(mockDownloadQueue).processQueue(any());
    }

    @Test
    void doesNotProcessTheDownloadQueueIfItDidNotEnqueueAnyFiles() throws MixcloudException, IOException,
                                                URISyntaxException, InterruptedException, TimeoutException {
        mockPodcast.episodes.clear();
        String[] args = new String[] { "artist", "shows" };

        downloader.download(args, rand.nextBoolean());

        verify(mockDownloadQueue, never()).enqueue(any());
        verify(mockDownloadQueue, never()).processQueue(any());
    }

    @Test
    void doesNotProcessTheDownloadQueueIfAllFilesAlreadyExist() throws MixcloudException, IOException,
                                                URISyntaxException, InterruptedException, TimeoutException {
        // enqueue() is already mocked to return false by default,
        // as it would do if the file already existed locally
        String[] args = new String[] { "artist", "shows" };

        downloader.download(args, rand.nextBoolean());

        verify(mockDownloadQueue, times(3)).enqueue(any());
        verify(mockDownloadQueue, never()).processQueue(any());
    }

    @Test
    void resolvesTheDefaultViewIfNeeded() throws MixcloudException, IOException,
                            URISyntaxException, InterruptedException, TimeoutException {

        String[] args = new String[] { "just-a-username" };

        downloader.download(args, rand.nextBoolean());

        MixcloudClient client = mockedConstructionForMixcloudClient.constructed().get(0);
        verify(client).queryDefaultView("just-a-username");  // mocked to return "favorites"
        verify(client).query(new MusicSet("just-a-username", "favorites", null));
    }

    @Test
    void resolvesTheDefaultViewFromUrlIfNeeded() throws MixcloudException, IOException,
                            URISyntaxException, InterruptedException, TimeoutException {

        String artist = "ArmadaMusicOfficial";
        String[] args = new String[] { "https://www.mixcloud.com/ArmadaMusicOfficial/" };

        downloader.download(args, rand.nextBoolean());

        MixcloudClient client = mockedConstructionForMixcloudClient.constructed().get(0);
        verify(client).queryDefaultView(artist);  // mocked to return "favorites"
        verify(client).query(new MusicSet(artist, "favorites", null));
    }

    @Test
    void keepsTheUnresolvedMusicSetAsTheWatchedSet() throws MixcloudException, IOException,
                                        URISyntaxException, InterruptedException, TimeoutException {

        // we need to resolve a user's default view if a music type wasn't given,
        // but if it was part of a watch, i.e. we're watching that user's default view,
        // we want to write the attribute as such, not with the resolved music type

        String[] args = new String[] { "watched-username" };
        MusicSet expectedSet = new MusicSet("watched-username", null, null);

        try (MockedConstruction<Download> mocked = mockConstruction(Download.class,
                (mock, context) -> {
                    // this is called each time one of Download's constructors is used
                    var actualSet = (MusicSet) context.arguments().get(4);
                    assertThat(actualSet).isEqualTo(expectedSet);
                })) {

            downloader.download(args, true);

            // ensure we've actually tested
            assertThat(mocked.constructed().size()).isEqualTo(mockPodcast.episodes.size());
        }
    }

    @Test
    void returnsAnErrorIfTheUserDoesNotExist() throws MixcloudException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {

        String[] args = new String[] { "does-not-exist", "shows" };
        int result = downloader.download(args, rand.nextBoolean());
        assertThat(result).isEqualTo(2);
    }

    @Test
    void returnsAnErrorIfThePlaylistDoesNotExist() throws MixcloudException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {

        String[] args = new String[] { "artist", "playlist", "does-not-exist" };
        int result = downloader.download(args, rand.nextBoolean());
        assertThat(result).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("Convert2Lambda")
    void invokesTheCallback() throws MixcloudException, IOException,
                                URISyntaxException, InterruptedException, TimeoutException {

        doReturn(3).when(mockDownloadQueue).queueSize();

        doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(0);
            callback.run();  // like the real processQueue() would do
            return null;
        }).when(mockDownloadQueue).processQueue(any());

        Runnable callback = spy(new Runnable() {
            @Override public void run() {}
        });

        String[] args = new String[] { "artist", "shows" };

        downloader = new Downloader(callback);
        downloader.download(args, rand.nextBoolean());

        verify(mockDownloadQueue).processQueue(callback);
        verify(callback).run();
    }

    @Test
    void doesNotUpdateTheRssLastRequestedAttribute() throws MixcloudException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {

        final Path tempDir = Files.createTempDirectory("mix-down-");
        System.setProperty("music_dir", tempDir.toString());

        try {
            doReturn(3).when(mockDownloadQueue).queueSize();
            String[] args = new String[] { "artist", "shows" };

            downloader.download(args, rand.nextBoolean());

            verify(mockDownloadQueue).processQueue(any());  // sanity check
            var attr = new RssLastRequestedAttr(tempDir);
            assertThat(attr.exists()).isFalse();
        }
        finally {
            TestUtilities.removeTempDirectory(tempDir);
        }
    }

    @Test
    void validatesOptions() throws MixcloudException, IOException,
                    URISyntaxException, InterruptedException, TimeoutException {

        List<String> invalids = List.of(
                "-limit=0", "-limit=-1", "-limit=foo", "-limit=", "-limit",
                "-out=\t", "-out=", "-out", "-foo", "-foo=42"
        );

        for (String invalid : invalids) {
            String[] args = ("artist shows " + invalid).split(" +");
            assertThatThrownBy(() -> downloader.download(args, rand.nextBoolean()))
                    .isInstanceOf(DownloadOptions.InvalidOptionException.class);
        }

        String[] args = "artist shows -rss -out=/private/tmp".split("\\s+");
        int result = downloader.download(args, rand.nextBoolean());
        assertThat(result).isEqualTo(1);
    }

    @Test
    void respectsTheLimitOption() throws MixcloudException, IOException,
                    URISyntaxException, InterruptedException, TimeoutException {

        // It's in MixcloudClient that the episode_max_count system property is handled,
        // Downloader just needs to put the -limit option's value into the property

        String[] args = "artist shows -limit=42".split("\\s+");
        int result = downloader.download(args, rand.nextBoolean());

        assertThat(result).isEqualTo(0);
        assertThat(System.getProperty("episode_max_count")).isEqualTo("42");
    }

    @Test
    void respectsTheOutOption() throws MixcloudException, IOException,
                    URISyntaxException, InterruptedException, TimeoutException {

        final Path tempDir = Files.createTempDirectory("mix-down-");

        try {
            String argStr = "artist shows -out=" + tempDir;
            String[] args = argStr.split("\\s+");

            when(mockDownloadQueue.enqueue(any())).then(invocation -> {
                // ensure the local path in each Download enqueued is in the requested output directory
                Download download = invocation.getArgument(0);
                Path dir = Path.of(download.localFilePath).getParent();
                assertThat(dir).isEqualTo(tempDir);
                return false;
            });

            int result = downloader.download(args, rand.nextBoolean());

            assertThat(result).isEqualTo(0);

            int expectedEnqueueCalls = mockPodcast.episodes.size();
            verify(mockDownloadQueue, times(expectedEnqueueCalls)).enqueue(any());
        }
        finally {
            TestUtilities.removeTempDirectory(tempDir);
        }
    }

    @Test
    void respectsTheRssOption() throws MixcloudException, IOException,
                    URISyntaxException, InterruptedException, TimeoutException {

        final Path tempDir = Files.createTempDirectory("mix-down-");
        final Path rssFile = Path.of(tempDir.toString(), "testy.xml");

        try {
            String argStr = "artist shows -rss=" + rssFile;
            String[] args = argStr.split("\\s+");

            downloader.download(args, rand.nextBoolean());

            String rss = Files.readString(rssFile);
            assertThat(rss).isEqualTo(mockPodcast.createXml());
        }
        finally {
            TestUtilities.removeTempDirectory(tempDir);
        }
    }

    @Test
    void respectsTheEmptyRssOption() throws MixcloudException, IOException,
                    URISyntaxException, InterruptedException, TimeoutException {

        Path rssFile = null;

        try {
            String artist = "dj-" + randomString();
            String playlist = "some-music";
            String[] args = new String[] { artist, "playlist", playlist, "-rss" };

            downloader.download(args, rand.nextBoolean());

            Path currentDir = Path.of(System.getProperty("user.dir"));
            String[] files = currentDir.toFile().list((dir, name) -> name.contains(artist));
            assert files != null;
            String rssFileName = files[0];

            assertThat(rssFileName).contains(artist);
            assertThat(rssFileName).contains(playlist);
            assertThat(rssFileName).contains("playlist");
            assertThat(rssFileName).endsWith(".rss.xml");

            rssFile = Path.of(currentDir.toString(), rssFileName);
            String rss = Files.readString(rssFile);
            assertThat(rss).isEqualTo(mockPodcast.createXml());
        }
        finally {
            if (rssFile != null) {
                Files.deleteIfExists(rssFile);
            }
        }
    }

    private String randomString() {
        return rand.ints(97, 123)  // a-z
                .limit(10)  // desired string length
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
