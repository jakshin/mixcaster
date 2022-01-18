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

package jakshin.mixcaster.http;

import jakshin.mixcaster.TestUtilities;
import jakshin.mixcaster.dlqueue.DownloadQueue;
import jakshin.mixcaster.mixcloud.*;
import jakshin.mixcaster.podcast.Podcast;
import jakshin.mixcaster.podcast.PodcastEpisode;
import jakshin.mixcaster.stale.attributes.RssLastRequestedAttr;
import jakshin.mixcaster.utils.DateFormatter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
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
 * Unit tests for the PodcastXmlResponder class.
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PodcastXmlResponderTest {
    private PodcastXmlResponder responder;
    private HttpRequest request;
    private StringWriter writer;
    private ByteArrayOutputStream out;

    private DownloadQueue mockDownloadQueue;
    private MockedStatic<DownloadQueue> mockedQueueStatic;
    private MockedConstruction<MixcloudClient> mockedClientConstruction;
    private Podcast mockPodcast;  // null until our mock MixcloudClient.query() creates it

    private static Path mockMusicDir;

    @BeforeAll
    static void beforeAll() throws IOException {
        mockMusicDir = TestUtilities.createMockMusicDir("mix-pxr-");

        LogManager.getLogManager().reset();
        logger.setLevel(Level.OFF);
    }

    @AfterAll
    static void afterAll() {
        System.clearProperty("music_dir");
        TestUtilities.removeTempDirectory(mockMusicDir);
    }

    @BeforeEach
    void setUp() {
        responder = new PodcastXmlResponder();
        writer = new StringWriter(8192);
        out = new ByteArrayOutputStream();

        mockDownloadQueue = mock(DownloadQueue.class);
        mockedQueueStatic = mockStatic(DownloadQueue.class);
        mockedQueueStatic.when(DownloadQueue::getInstance).thenReturn(mockDownloadQueue);

        mockedClientConstruction = mockConstruction(MixcloudClient.class,
                (mock, context) -> {
                    // this gets called when a MixcloudClient constructor is used,
                    // and initializes that specific instance of the MixcloudClient mock

                    when(mock.queryDefaultView(anyString())).thenReturn("stream");

                    when(mock.query(any())).thenAnswer(invocation -> {
                        MusicSet queried = invocation.getArgument(0);

                        if ("does-not-exist".equals(queried.username()))
                            throw new MixcloudUserException("Mock exception", "does-not-exist");

                        if ("does-not-exist".equals(queried.playlist()))
                            throw new MixcloudPlaylistException("Mock exception", "artist", "does-not-exist");

                        int episodes = "empty".equals(queried.playlist()) ? 0 : 3;
                        mockPodcast = Utilities.createMockPodcast(episodes);
                        return mockPodcast;
                    });
                });
    }

    @AfterEach
    void tearDown() {
        mockedQueueStatic.close();
        mockedClientConstruction.close();
    }

    @Test
    void worksWithOrWithoutXmlAtTheEndOfTheUrl() throws MixcloudException, HttpException, IOException,
                                        URISyntaxException, InterruptedException, TimeoutException {
        var request1 = new HttpRequest("GET", "/artist/shows.xml", "HTTP/1.1");
        responder.respond(request1, writer, out);
        String headers1 = writer.toString();
        String response1 = out.toString();

        Utilities.resetStringWriter(writer);
        out.reset();

        var request2 = new HttpRequest("GET" ,"/artist/shows", "HTTP/1.1");
        responder.respond(request2, writer, out);
        String headers2 = writer.toString();
        String response2 = out.toString();

        assertThat(headers1).startsWith("HTTP/1.1 200 OK\r\n");
        assertThat(response1).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(response1).contains("<rss version=\"2.0\" xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\">");
        assertThat(headers1).isEqualTo(headers2);
        assertThat(response1).isEqualTo(response2);
    }

    @Test
    void rejectsInvalidUrls() {
        List<String> invalidUrls = List.of(
                "/extra/artist/shows.xml",
                "/artist/extra/shows.xml",
                "/artist/shows/extra.xml",
                "/artist/invalid.xml",
                "/artist/playlist.xml",  // missing a playlist name
                "/artist/shows/playlist-name.xml"
        );

        for (String url : invalidUrls) {
            var invalid = new HttpRequest("GET", url, "HTTP/1.1");
            assertThatThrownBy(() -> responder.respond(invalid, writer, out))
                    .isInstanceOf(HttpException.class)
                    .hasFieldOrPropertyWithValue("httpResponseCode", 404);
        }
    }

    @Test
    void resolvesAndCachesDefaultViews() throws MixcloudException, HttpException, IOException,
                                        URISyntaxException, InterruptedException, TimeoutException {
        request = new HttpRequest("GET", "/artist2", "HTTP/1.1");

        responder.respond(request, writer, out);

        MixcloudClient mockClient = mockedClientConstruction.constructed().get(0);
        verify(mockClient).queryDefaultView("artist2");
        verify(mockClient).query(new MusicSet("artist2", "stream", null));

        // the user's default view should be in PodcastXmlResponder's cache now
        responder.respond(request, writer, out);

        mockClient = mockedClientConstruction.constructed().get(1);
        verify(mockClient, never()).queryDefaultView("artist2");
    }

    @Test
    void cachesPodcastObjects() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        request = new HttpRequest("GET", "/artist3/shows.xml", "HTTP/1.1");

        responder.respond(request, writer, out);

        MixcloudClient mockClient = mockedClientConstruction.constructed().get(0);
        verify(mockClient).query(any());

        // the podcast should be in PodcastXmlResponder's cache now
        responder.respond(request, writer, out);

        mockClient = mockedClientConstruction.constructed().get(1);
        verify(mockClient, never()).query(any());
    }

    @Test
    void checksTheExistenceOfEachFile() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        request = new HttpRequest("GET", "/somebody/shows.xml", "HTTP/1.1");

        responder.respond(request, writer, out);

        String responseBody1 = out.toString();
        assertThat(responseBody1).contains("[DOWNLOADING");
        out.reset();

        for (PodcastEpisode ep : mockPodcast.episodes) {
            Path path = Path.of(mockMusicDir.toString(), ep.enclosureUrl.getPath());
            Files.createDirectories(path.getParent());
            Files.writeString(path, path.getFileName().toString());
        }

        responder.respond(request, writer, out);

        String responseBody2 = out.toString();
        assertThat(responseBody2).doesNotContain("[DOWNLOADING");
    }

    @Test
    void returns404WhenTheUserDoesNotExist() {
        request = new HttpRequest("GET", "/does-not-exist.xml", "HTTP/1.1");
        assertThatThrownBy(() -> responder.respond(request, writer, out))
                .isInstanceOf(HttpException.class)
                .hasFieldOrPropertyWithValue("httpResponseCode", 404);
    }

    @Test
    void returns404WhenThePlaylistDoesNotExist() {
        request = new HttpRequest("GET", "/artist/playlists/does-not-exist.xml", "HTTP/1.1");
        assertThatThrownBy(() -> responder.respond(request, writer, out))
                .isInstanceOf(HttpException.class)
                .hasFieldOrPropertyWithValue("httpResponseCode", 404);
    }

    @Test
    void returns404WhenThePodcastHasNoEpisodes() {
        request = new HttpRequest("GET", "/artist/playlists/empty.xml", "HTTP/1.1");
        assertThatThrownBy(() -> responder.respond(request, writer, out))
                .isInstanceOf(HttpException.class)
                .hasFieldOrPropertyWithValue("httpResponseCode", 404);
    }

    @Test
    @Order(1)  // so the RssLastRequested attribute hasn't already been set
    void setsTheRssLastRequestAttribute() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        var attr = new RssLastRequestedAttr(mockMusicDir);
        assertThat(attr.exists()).isFalse();  // sanity check

        request = new HttpRequest("GET", "/artist4/shows.xml", "HTTP/1.1");

        responder.respond(request, writer, out);

        assertThat(writer.toString()).startsWith("HTTP/1.1 200 OK");  // sanity check
        assertThat(attr.exists()).isTrue();
    }

    @Test
    void handlesIfModifiedSince() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        request = new HttpRequest("GET", "/artist5/shows.xml", "HTTP/1.1");

        Date later = Date.from(Instant.now().plus(10, ChronoUnit.MINUTES));
        request.headers.put("If-Modified-Since", DateFormatter.format(later));

        responder.respond(request, writer, out);

        assertThat(writer.toString()).startsWith("HTTP/1.1 304 Not Modified");
        Utilities.resetStringWriter(writer);

        Date backThen = Date.from(Instant.now().minus(10, ChronoUnit.MINUTES));
        request.headers.put("If-Modified-Since", DateFormatter.format(backThen));

        responder.respond(request, writer, out);

        assertThat(writer.toString()).startsWith("HTTP/1.1 200 OK");
    }

    @Test
    void startsDownloadingNewFiles() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        when(mockDownloadQueue.enqueue(any())).thenReturn(true);
        request = new HttpRequest("GET", "/artist6/shows.xml", "HTTP/1.1");

        responder.respond(request, writer, out);

        int episodes = mockPodcast.episodes.size();
        assertThat(episodes).isGreaterThan(0);  // sanity check

        verify(mockDownloadQueue, times(episodes)).enqueue(any());
        verify(mockDownloadQueue).processQueue(any());
    }

    private void validateResponseHeaders(String headers) {
        assertThat(headers).startsWith("HTTP/1.1 200 OK\r\n");
        Utilities.parseDateHeader("Date", headers);
        assertThat(headers).containsOnlyOnce("Connection: close\r\n");
        Utilities.parseDateHeader("Last-Modified", headers);
        assertThat(headers).containsOnlyOnce("Content-Type: text/xml; charset=UTF-8\r\n");
        assertThat(headers).matches("(?s).*Content-Length:\\s+[1-9][0-9]+\\r\\n.*");
        assertThat(headers).doesNotContain("Cache-Control: no-cache");
    }

    private int parseContentLengthHeader(String headers) {
        String headerName = "Content-Length: ";
        int start = headers.indexOf(headerName) + headerName.length();
        int end = headers.indexOf('\r', start);
        return Integer.parseInt(headers.substring(start, end));
    }

    @Test
    void respondsToHeadRequests() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        var headRequest = new HttpRequest("HEAD", "/artist7/shows.xml", "HTTP/1.1");

        responder.respond(headRequest, writer, out);

        String headers = writer.toString();
        validateResponseHeaders(headers);

        byte[] body = out.toByteArray();
        assertThat(body).isEmpty();
    }

    @Test
    void respondsToGetRequests() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        request = new HttpRequest("GET", "/artist8/shows.xml", "HTTP/1.1");

        responder.respond(request, writer, out);

        String headers = writer.toString();
        validateResponseHeaders(headers);

        byte[] body = out.toByteArray();
        int contentLength = parseContentLengthHeader(headers);
        assertThat(body.length).isEqualTo(contentLength);
        assertThat(body).isNotEmpty();
    }
}