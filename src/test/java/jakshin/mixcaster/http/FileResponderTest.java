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
import jakshin.mixcaster.mixcloud.MixcloudClient;
import jakshin.mixcaster.mixcloud.MixcloudException;
import jakshin.mixcaster.stale.attributes.LastUsedAttr;
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
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogManager;

import static jakshin.mixcaster.logging.Logging.logger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the FileResponder class.
 */
@ExtendWith(MockitoExtension.class)
class FileResponderTest {
    private FileResponder responder;
    private HttpRequest request;
    private StringWriter writer;
    private ByteArrayOutputStream out;

    private static Path mockMusicDir;
    private static Path outsideDir;

    @BeforeAll
    static void beforeAll() throws IOException {
        mockMusicDir = TestUtilities.createMockMusicDir("mix-fir-");

        LogManager.getLogManager().reset();
        logger.setLevel(Level.OFF);
    }

    @AfterAll
    static void afterAll() {
        System.clearProperty("music_dir");
        TestUtilities.removeTempDirectory(mockMusicDir);
        TestUtilities.removeTempDirectory(outsideDir);
    }

    @BeforeEach
    void setUp() {
        responder = new FileResponder();
        writer = new StringWriter(8192);
        out = new ByteArrayOutputStream();
    }

    @AfterEach
    void tearDown() {
    }

    private void validateCommonHeaders(String headers) {
        assertThat(headers).containsOnlyOnce("Connection: close\r\n");
        assertThat(headers).matches("(?s).*Content-Length:\\s+[1-9][0-9]+\\r\\n.*");
        Utilities.parseDateHeader("Date", headers);
    }

    @Test
    void delegatesToPodcastXmlResponder() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {

        try (MockedConstruction<MixcloudClient> ignored = mockConstruction(MixcloudClient.class,
                (mock, context) -> {
                    when(mock.queryDefaultView(anyString())).thenReturn("shows");
                    when(mock.query(any())).thenAnswer(invocation -> Utilities.createMockPodcast());
                })) {

            request = new HttpRequest("GET", "/newagerage", "HTTP/1.1");

            responder.respond(request, writer, out);

            String headers = writer.toString();
            assertThat(headers).startsWith("HTTP/1.1 200 OK\r\n");
            assertThat(headers).contains("Content-Type: text/xml; charset=UTF-8\r\n");
            Utilities.parseDateHeader("Last-Modified", headers);
            validateCommonHeaders(headers);
        }
    }

    @Test
    void refusesToServeFilesOutsideTheMusicDir() throws IOException {
        // ServableFile protects us by normalizing paths, which usually results in 404s...
        requestFilesOutsideTheMusicDir(404, "Not Found");

        // ...but what if that changes, will we still be protected?
        try (MockedStatic<ServableFile> mockedStatic = mockStatic(ServableFile.class)) {
            MockedStatic.Verification getLocalPath = () -> ServableFile.getLocalPath(anyString());

            mockedStatic.when(getLocalPath).then((invocation) -> {
                Object[] args = invocation.getArguments();
                return args[0];
            });

            requestFilesOutsideTheMusicDir(403, "Forbidden");
        }
    }

    private void requestFilesOutsideTheMusicDir(int expectedResponseCode, String expectedReason) throws IOException {
        if (outsideDir == null) {
            outsideDir = Files.createTempDirectory("mix-fir-");
            Path outsideFile = Path.of(outsideDir.toString(), "outside.mp3");
            Files.writeString(outsideFile, "more fake file data");

            Path symlink = Path.of(mockMusicDir.toString(), "dir/symlink");
            Files.createSymbolicLink(symlink, outsideFile);
        }

        String scaryPath = "/../" + outsideDir.getFileName() + "/outside.mp3";
        request = new HttpRequest("GET", scaryPath, "HTTP/1.1");

        assertThatThrownBy(() -> responder.respond(request, writer, out))
                .isInstanceOf(HttpException.class)
                .hasFieldOrPropertyWithValue("httpResponseCode", expectedResponseCode)
                .hasMessageContaining(expectedReason);

        request = new HttpRequest("GET", "dir/symlink","HTTP/1.1");

        assertThatThrownBy(() -> responder.respond(request, writer, out))
                .isInstanceOf(HttpException.class)
                .hasFieldOrPropertyWithValue("httpResponseCode", 403)
                .hasMessageContaining("Forbidden");
    }

    @Test
    void redirectsIfThePathIsAFolder() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        request = new HttpRequest("GET", "/dir/subdir", "HTTP/1.1");

        responder.respond(request, writer, out);

        String headers = writer.toString();
        assertThat(headers).startsWith("HTTP/1.1 301 Moved Permanently\r\n");
        assertThat(headers).contains("Content-Type: text/plain\r\n");
        assertThat(headers).contains("Location: /dir/subdir/\r\n");
        validateCommonHeaders(headers);
    }

    @Test
    void updatesTheLastUsedAttribute() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        Path otherFile = Path.of(mockMusicDir.toString(), "dir/other.mp3");
        Files.writeString(otherFile, "other fake file data");
        var attr = new LastUsedAttr(otherFile);
        attr.setValue(OffsetDateTime.now().minusHours(1));

        request = new HttpRequest("GET", "/dir/other.mp3", "HTTP/1.1");

        responder.respond(request, writer, out);

        String headers = writer.toString();
        assertThat(headers).startsWith("HTTP/1.1 200 OK\r\n");  // sanity checks
        assertThat(headers).matches("(?s).*Content-Type: audio/[a-z0-9]+\r\n.*");

        OffsetDateTime updated = attr.getValue();
        long ago = updated.toInstant().until(Instant.now(), ChronoUnit.SECONDS);
        assertThat(ago).isGreaterThanOrEqualTo(0).isLessThan(30);
    }

    @Test
    void doesNotAddTheLastUsedAttribute() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        request = new HttpRequest("GET", "/dir/file.m4a", "HTTP/1.1");

        responder.respond(request, writer, out);

        String headers = writer.toString();
        assertThat(headers).startsWith("HTTP/1.1 200 OK\r\n");  // sanity checks
        assertThat(headers).matches("(?s).*Content-Type: audio/[a-z0-9]+\r\n.*");

        var attr = new LastUsedAttr(Path.of(mockMusicDir.toString(), "/dir/file.m4a"));
        assertThat(attr.exists()).isFalse();
    }

    @Test
    void handlesIfModifiedSince() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        request = new HttpRequest("GET", "/dir/file.m4a", "HTTP/1.1");

        Date now = Date.from(Instant.now());
        request.headers.put("If-Modified-Since", DateFormatter.format(now));

        responder.respond(request, writer, out);
        assertThat(writer.toString()).startsWith("HTTP/1.1 304 Not Modified");

        Date backThen = Date.from(Instant.now().minus(10, ChronoUnit.MINUTES));
        request.headers.put("If-Modified-Since", DateFormatter.format(backThen));
        writer.getBuffer().delete(0, writer.getBuffer().length());

        responder.respond(request, writer, out);
        assertThat(writer.toString()).startsWith("HTTP/1.1 200 OK");
    }

    @Test
    void respondsToHeadRequests() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        var headRequest = new HttpRequest("HEAD", "/dir/file.m4a", "HTTP/1.1");

        responder.respond(headRequest, writer, out);

        String headers = writer.toString();
        assertThat(headers).startsWith("HTTP/1.1 200 OK\r\n");
        assertThat(headers).contains("Accept-Ranges: bytes\r\n");
        assertThat(headers).doesNotContain("Cache-Control: no-cache");
        assertThat(headers).matches("(?s).*Content-Type: audio/[a-z0-9]+\r\n.*");
        Utilities.parseDateHeader("Last-Modified", headers);
        validateCommonHeaders(headers);

        byte[] body = out.toByteArray();
        assertThat(body).isEmpty();
    }

    @Test
    void respondsToGetRequests() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        request = new HttpRequest("GET", "/dir/file.m4a", "HTTP/1.1");

        responder.respond(request, writer, out);

        String headers = writer.toString();
        assertThat(headers).startsWith("HTTP/1.1 200 OK\r\n");
        assertThat(headers).contains("Accept-Ranges: bytes\r\n");
        assertThat(headers).doesNotContain("Cache-Control: no-cache");
        assertThat(headers).matches("(?s).*Content-Type: audio/[a-z0-9]+\r\n.*");
        Utilities.parseDateHeader("Last-Modified", headers);
        validateCommonHeaders(headers);

        byte[] body = out.toByteArray();
        int contentLength = parseContentLengthHeader(headers);
        assertThat(body.length).isEqualTo(contentLength);
        assertThat(body).isNotEmpty();
    }

    private int parseContentLengthHeader(String headers) {
        String headerName = "Content-Length: ";
        int start = headers.indexOf(headerName) + headerName.length();
        int end = headers.indexOf('\r', start);
        return Integer.parseInt(headers.substring(start, end));
    }

    @Test
    void respondsToRangeRequests() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        Files.writeString(Path.of(mockMusicDir.toString(), "dir/file.m4a"), "0123456789");
        request = new HttpRequest("GET", "/dir/file.m4a", "HTTP/1.1");

        var ranges = List.of(
                new Range("0-9", "0-9/10", 10),
                new Range("0-100", "0-9/10", 10),
                new Range("5-7", "5-7/10", 3),
                new Range("5-", "5-9/10", 5),
                new Range("-4", "6-9/10", 4),
                new Range("-100", "0-9/10", 10),
                new Range("1-1", "1-1/10", 1),
                new Range("-0", "", 10),
                new Range("2-1", "", 10)
        );

        for (var range : ranges) {
            checkRangeRequest(range);
        }

        assertThatThrownBy(() -> makeRangeRequest("10-"))
                .isInstanceOf(HttpException.class)
                .hasFieldOrPropertyWithValue("httpResponseCode", 416)
                .hasMessageContaining("Requested Range Not Satisfiable");
    }

    private record Range(String bytes, String expectedContentRange, int expectedLength) {}

    private void checkRangeRequest(Range range) throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        makeRangeRequest(range.bytes);
        String headers = writer.toString();

        if (range.expectedContentRange.isBlank()) {
            // the requested range is invalid, but we ignore rather than rejecting (copying Apache 2.2.29)
            assertThat(headers).startsWith("HTTP/1.1 200 OK\r\n");
            assertThat(headers).doesNotContain("Content-Range");
        }
        else {
            assertThat(headers).startsWith("HTTP/1.1 206 Partial Content\r\n");
            assertThat(headers).contains("Content-Range: bytes " + range.expectedContentRange + "\r\n");
        }

        int contentLength = parseContentLengthHeader(headers);
        assertThat(contentLength).isEqualTo(range.expectedLength);

        byte[] body = out.toByteArray();
        assertThat(body.length).isEqualTo(range.expectedLength);
    }

    private void makeRangeRequest(String rangeBytes) throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        request.headers.put("Range", "bytes=" + rangeBytes);
        writer = new StringWriter();
        out = new ByteArrayOutputStream();

        responder.respond(request, writer, out);
    }

    @Test
    void returns404IfThePathDoesNotExist() {
        request = new HttpRequest("GET", "/dir/does-not-exist", "HTTP/1.1");
        assertThatThrownBy(() -> responder.respond(request, writer, out))
                .isInstanceOf(HttpException.class)
                .hasFieldOrPropertyWithValue("httpResponseCode", 404)
                .hasMessageContaining("Not Found");
    }

    @Test
    void returns403IfThePathIsASymlink() {
        request = new HttpRequest("GET", "/dir/file-link", "HTTP/1.1");
        assertThatThrownBy(() -> responder.respond(request, writer, out))
                .isInstanceOf(HttpException.class)
                .hasFieldOrPropertyWithValue("httpResponseCode", 403)
                .hasMessageContaining("Forbidden");

        request = new HttpRequest("GET", "/dir/subdir-link", "HTTP/1.1");
        assertThatThrownBy(() -> responder.respond(request, writer, out))
                .isInstanceOf(HttpException.class)
                .hasFieldOrPropertyWithValue("httpResponseCode", 403)
                .hasMessageContaining("Forbidden");
    }
}
