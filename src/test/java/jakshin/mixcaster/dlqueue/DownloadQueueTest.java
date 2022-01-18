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

import jakshin.mixcaster.TestUtilities;
import jakshin.mixcaster.mixcloud.MusicSet;
import jakshin.mixcaster.stale.attributes.LastUsedAttr;
import jakshin.mixcaster.stale.attributes.WatchesAttr;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.LogManager;

import static jakshin.mixcaster.logging.Logging.logger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.find;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the DownloadQueue class.
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DownloadQueueTest {
    private static final Random rand = new Random();
    private static final String testUserAgent = "Test User Agent/1.1";
    private static Path mockMusicDir;

    @BeforeAll
    static void beforeAll() throws IOException {
        mockMusicDir = TestUtilities.createMockMusicDir("mix-dlq-");

        System.setProperty("music_dir", mockMusicDir.toString());
        System.setProperty("user_agent", testUserAgent);
        System.setProperty("download_threads", "auto");
        System.setProperty("download_oldest_first", String.valueOf(rand.nextBoolean()));

        LogManager.getLogManager().reset();
        logger.setLevel(Level.OFF);
    }

    @AfterAll
    static void afterAll() {
        TestUtilities.removeTempDirectory(mockMusicDir);

        System.clearProperty("music_dir");
        System.clearProperty("user_agent");
        System.clearProperty("download_threads");
        System.clearProperty("download_oldest_first");
    }

    private Download createDownload(String fileName) {
        return createDownload(fileName, null);
    }

    private Download createDownload(String fileName, MusicSet inWatchedSet) {
        String remoteUrl = "http://example.com/" + fileName;
        String localFilePath = Path.of(mockMusicDir.toString(), fileName).toString();
        return new Download(remoteUrl, 42, new Date(), localFilePath, inWatchedSet);
    }

    @Test
    void getInstanceAlwaysReturnsTheSameInstance() {
        DownloadQueue q1 = DownloadQueue.getInstance();
        DownloadQueue q2 = DownloadQueue.getInstance();
        assertThat(q1).isSameAs(q2);
    }

    @Test
    void doesNotQueueFilesAlreadyQueued() {
        Download download = createDownload("test-queued.m4a");
        DownloadQueue q = DownloadQueue.getInstance();
        int originalSize = q.queueSize();

        q.enqueue(download);  // should be queued

        int newSize = originalSize + 1;
        assertThat(q.queueSize()).isEqualTo(newSize);

        q.enqueue(download);  // should not be queued a second time

        assertThat(q.queueSize()).isEqualTo(newSize);
    }

    @Test
    void doesNotQueueFilesAlreadyBeingDownloaded() {
        try (MockedConstruction<DownloadQueue.DownloadRunnable>
                     ignored = mockConstruction(DownloadQueue.DownloadRunnable.class)) {

            Download download = createDownload("test-being-downloaded.m4a");
            DownloadQueue q = DownloadQueue.getInstance();
            int originalSize = q.queueSize();

            q.enqueue(download);  // should be queued
            assertThat(q.queueSize()).isEqualTo(originalSize + 1);

            q.processQueue(null);
            assertThat(q.queueSize()).isEqualTo(0);  // sanity check, all files being downloaded now

            q.enqueue(download);  // shouldn't be queued again

            assertThat(q.queueSize()).isEqualTo(0);
        }
    }

    @Test
    void doesNotQueueFilesThatAlreadyExistLocally() throws IOException {
        Download download = createDownload("test-exists.m4a");
        Files.writeString(Path.of(download.localFilePath), "test-exists");
        DownloadQueue q = DownloadQueue.getInstance();
        int originalSize = q.queueSize();

        q.enqueue(download);  // should not be queued

        assertThat(q.queueSize()).isEqualTo(originalSize);
    }

    @Test
    void updatesAttributesOnExistingFiles() throws IOException {
        var watchedSet = new MusicSet("that-guy", "shows", null);
        Download download = createDownload("test-exists-2.m4a", watchedSet);
        Files.writeString(Path.of(download.localFilePath), "test-exists-2");
        DownloadQueue q = DownloadQueue.getInstance();

        q.enqueue(download);

        Path localPath = Path.of(download.localFilePath);
        var lastUsedAttr = new LastUsedAttr(localPath);
        OffsetDateTime lastUsed = lastUsedAttr.getValue();
        OffsetDateTime aMinuteAgo = OffsetDateTime.now().minus(1, ChronoUnit.MINUTES);
        assertThat(lastUsed).isBetween(aMinuteAgo, OffsetDateTime.now());

        var watchesAttr = new WatchesAttr(localPath);
        assertThat(watchesAttr.getValue()).contains(watchedSet);
    }

    @Test
    @Order(1)  // execute first, so the DownloadQueue instance is still empty
    @SuppressWarnings("Convert2Lambda")
    void invokesTheCallbackImmediatelyIfAlreadyEmpty() {
        Runnable callback = spy(new Runnable() {
            @Override public void run() {}
        });

        DownloadQueue q = DownloadQueue.getInstance();
        assertThat(q.queueSize()).isEqualTo(0);  // sanity checks
        assertThat(q.activeDownloadCount()).isEqualTo(0);

        q.processQueue(callback);

        verify(callback).run();
    }

    @Test
    @Order(2)  // so no other tests' files are queued (which would cause actual network requests)
    @SuppressWarnings("Convert2Lambda")
    void invokesTheCallbackWhenItBecomesEmpty() throws InterruptedException {
        Runnable callback = spy(new Runnable() {
            @Override public void run() {}
        });

        DownloadQueue q = DownloadQueue.getInstance();

        for (int i = 1; i <= 3; i++) {
            // an easy way to set up a queue that processQueue() will clear, without a ton of mocking,
            // is to use invalid downloads, each of which will quickly error out
            Path localPath = Path.of(mockMusicDir.toString(), String.format("invalid-%d", i));
            var download = new Download("invalid-url", i, new Date(), localPath.toString());
            q.enqueue(download);
        }

        q.processQueue(callback);

        Thread.sleep(10);
        verify(callback).run();
    }

    private List<Download> createAgedDownloads() {
        Date oldest = Date.from(Instant.now().minus(3, ChronoUnit.DAYS));
        Date middle = Date.from(Instant.now().minus(2, ChronoUnit.DAYS));
        Date newest = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));

        return List.of(
            new Download("url-1", 1234, middle, "middle"),
            new Download("url-2", 2345, newest, "newest"),
            new Download("url-3", 3456, oldest, "oldest")
        );
    }

    @Test
    void downloadsFilesInTheConfiguredOrder() {
        List<String> downloaded = new ArrayList<>();
        try (MockedConstruction<DownloadQueue.DownloadRunnable>
                     ignored = mockConstruction(DownloadQueue.DownloadRunnable.class,
                (mock, context) -> {
                    Download download = (Download) context.arguments().get(1);
                    downloaded.add(download.localFilePath);
                })) {

            DownloadQueue q = DownloadQueue.getInstance();
            for (Download download : createAgedDownloads()) {
                q.enqueue(download);
            }

            q.processQueue(null);

            // we choose oldest-first or newest-first randomly in beforeAll()
            List<String> expected = Boolean.parseBoolean(System.getProperty("download_oldest_first"))
                    ? List.of("oldest", "middle", "newest") : List.of("newest", "middle", "oldest");

            assertThat(downloaded).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("DownloadRunnable")
    class DownloadRunnableTest {
        private final String fakeFileData = "fake-file-data";
        private String filename;
        private Date lastModified;
        private MusicSet watchedSet;
        private Download download;
        private HttpURLConnection mockConnection;
        private DownloadQueue.DownloadRunnable runnable;

        private String randomString(int length) {
            return rand.ints(97, 123)  // a-z
                    .limit(length)  // desired string length
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();
        }

        @BeforeEach
        void setUp() throws IOException {
            String artist = "user-" + randomString(8);  // name of the music file's parent directory
            filename = randomString(10) + ".m4a";
            lastModified = Date.from(Instant.now().minus(3, ChronoUnit.DAYS));
            watchedSet = new MusicSet(artist, "shows", null);

            String remoteUrl = "http://example.com/" + filename;
            String localPath = Path.of(mockMusicDir.toString(), artist, filename).toString();
            download = new Download(remoteUrl, fakeFileData.length(), lastModified, localPath, watchedSet);

            mockConnection = mock(HttpURLConnection.class);
            InputStream in = new ByteArrayInputStream(fakeFileData.getBytes(StandardCharsets.UTF_8));
            lenient().doReturn(in).when(mockConnection).getInputStream();

            var realRunnable = DownloadQueue.getInstance().new DownloadRunnable(download);
            runnable = spy(realRunnable);
            lenient().doCallRealMethod().when(runnable).run();
            lenient().doReturn(mockConnection).when(runnable).openConnection(anyString());
        }

        @Test
        void downloadsTheFileFromTheUrl() throws IOException {
            runnable.run();

            String fileData = Files.readString(Path.of(download.localFilePath));
            assertThat(fileData).isEqualTo(fakeFileData);

            verify(runnable).openConnection(download.remoteUrl);
        }

        @Test
        void followsRedirects() {
            runnable.run();
            verify(mockConnection).setInstanceFollowRedirects(true);
        }

        @Test
        void sendsTheConfiguredUserAgent() {
            runnable.run();
            verify(mockConnection).setRequestProperty("User-Agent", testUserAgent);
        }

        @Test
        void sendRefererWithTheSameDomain() throws IOException {
            runnable.run();

            var url = new URL(download.remoteUrl);
            String host = String.format("%s://%s", url.getProtocol(), url.getHost());
            verify(mockConnection).setRequestProperty(eq("Referer"), find(host));
        }

        @Test
        void createsTheLocalDirectoryIfNeeded() {
            Path localDir = Path.of(download.localFilePath).getParent();
            assertThat(localDir).doesNotExist();

            runnable.run();

            assertThat(localDir).isDirectory();
            assertThat(Path.of(download.localFilePath)).isRegularFile();
        }

        @Test
        void overwritesAnExistingPartFile() throws IOException {
            Path localDir = Path.of(download.localFilePath).getParent();
            Path partFile = Path.of(localDir.toString(), filename + ".part");
            Files.createDirectories(localDir);
            Files.writeString(partFile, "some-other-fake-data");

            runnable.run();

            assertThat(partFile).doesNotExist();

            String fileData = Files.readString(Path.of(download.localFilePath));
            assertThat(fileData).isEqualTo(fakeFileData);
        }

        @Test
        void replacesAnExistingPartFileSymlink() throws IOException {
            Path localDir = Path.of(download.localFilePath).getParent();
            Path partFile = Path.of(localDir.toString(), filename + ".part");
            Path linkTargetPath = Path.of(localDir.toString(), "target");
            String linkTargetText = "this is the symlink target";
            Files.createDirectories(localDir);
            Files.writeString(linkTargetPath, linkTargetText);
            Files.createSymbolicLink(partFile, linkTargetPath);

            runnable.run();

            assertThat(partFile).doesNotExist();

            Path localPath = Path.of(download.localFilePath);
            String fileData = Files.readString(localPath);
            assertThat(fileData).isEqualTo(fakeFileData);
            assertThat(localPath).isRegularFile();

            String linkTargetFileData = Files.readString(linkTargetPath);
            assertThat(linkTargetFileData).isEqualTo(linkTargetText);
        }

        @Test
        void overwritesAnExistingFile() throws IOException {
            Path localPath = Path.of(download.localFilePath);
            Files.createDirectories(localPath.getParent());
            Files.writeString(localPath, "some-other-fake-data");

            runnable.run();

            String fileData = Files.readString(localPath);
            assertThat(fileData).isEqualTo(fakeFileData);
        }

        @Test
        void replacesAnExistingSymlink() throws IOException {
            Path localPath = Path.of(download.localFilePath);
            Path localDir = localPath.getParent();
            Path linkTargetPath = Path.of(localDir.toString(), "target");
            String linkTargetText = "this is the symlink target";
            Files.createDirectories(localDir);
            Files.writeString(linkTargetPath, linkTargetText);
            Files.createSymbolicLink(localPath, linkTargetPath);

            runnable.run();

            String fileData = Files.readString(localPath);
            assertThat(fileData).isEqualTo(fakeFileData);
            assertThat(localPath).isRegularFile();

            String linkTargetFileData = Files.readString(linkTargetPath);
            assertThat(linkTargetFileData).isEqualTo(linkTargetText);
        }

        @Test
        void setsTheLastModifiedTimestamp() throws IOException {
            runnable.run();

            FileTime actualLastModified = Files.getLastModifiedTime(Path.of(download.localFilePath));
            assertThat(actualLastModified.toInstant()).isEqualTo(lastModified.toInstant());
        }

        @Test
        void setsTheLastUsedAndWatchesAttributes() throws IOException {
            runnable.run();

            Path localPath = Path.of(download.localFilePath);
            var lastUsedAttr = new LastUsedAttr(localPath);
            OffsetDateTime lastUsed = lastUsedAttr.getValue();
            OffsetDateTime aMinuteAgo = OffsetDateTime.now().minus(1, ChronoUnit.MINUTES);
            assertThat(lastUsed).isBetween(aMinuteAgo, OffsetDateTime.now());

            var watchesAttr = new WatchesAttr(localPath);
            assertThat(watchesAttr.getValue()).contains(watchedSet);
        }

        @Test
        void doesNotDisconnectOnSuccess() {
            runnable.run();
            verify(mockConnection, never()).disconnect();
        }

        @Test
        void disconnectsAfterAnError() throws IOException {
            doThrow(new IOException("Testing")).when(mockConnection).getInputStream();
            runnable.run();
            verify(mockConnection).disconnect();
        }

        @Test
        void opensConnections() throws IOException {
            doCallRealMethod().when(runnable).openConnection(anyString());
            String urlStr = "http://example.com/foo.m4a";

            HttpURLConnection conn = runnable.openConnection(urlStr);

            URL url = conn.getURL();
            assertThat(url).isEqualTo(new URL(urlStr));
        }
    }
}
