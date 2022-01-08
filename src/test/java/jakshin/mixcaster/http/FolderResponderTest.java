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
import jakshin.mixcaster.mixcloud.MixcloudException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogManager;

import static jakshin.mixcaster.logging.Logging.logger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the FolderResponder class.
 */
@ExtendWith(MockitoExtension.class)
class FolderResponderTest {
    private FolderResponder responder;
    private HttpRequest request;
    private StringWriter writer;
    private ByteArrayOutputStream out;
    private static Path mockMusicDir;

    @BeforeAll
    static void beforeAll() throws IOException {
        mockMusicDir = TestUtilities.createMockMusicDir("mix-for-");

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
        responder = new FolderResponder();
        writer = new StringWriter(8192);
        out = new ByteArrayOutputStream();
    }

    @AfterEach
    void tearDown() {
    }

    private void validateCommonHeaders(String response) {
        assertThat(response).containsOnlyOnce("Connection: close\r\n");
        assertThat(response).matches("(?s).*Content-Length:\\s+[1-9][0-9]+\\r\\n.*");
        Utilities.parseDateHeader("Date", response);
    }

    @Test
    void delegatesToPodcastXmlResponder() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        request = new HttpRequest("GET", "/maxswineberg/", "HTTP/1.1");

        responder.respond(request, writer, out);

        String response = writer.toString();
        assertThat(response).startsWith("HTTP/1.1 200 OK\r\n");
        assertThat(response).contains("Content-Type: text/xml; charset=UTF-8\r\n");
        Utilities.parseDateHeader("Last-Modified", response);
        validateCommonHeaders(response);
    }

    @Test
    void redirectsIfThePathIsAFile() throws MixcloudException, HttpException, IOException,
                                    URISyntaxException, InterruptedException, TimeoutException {
        request = new HttpRequest("GET", "/dir/file.m4a/", "HTTP/1.1");

        responder.respond(request, writer, out);

        String response = writer.toString();
        assertThat(response).startsWith("HTTP/1.1 301 Moved Permanently\r\n");
        assertThat(response).contains("Content-Type: text/plain\r\n");
        assertThat(response).contains("Location: /dir/file.m4a\r\n");
        validateCommonHeaders(response);
    }

    @Test
    void returns403IfThePathIsAFolder() {
        request = new HttpRequest("GET", "/dir/subdir/", "HTTP/1.1");
        assertThatThrownBy(() -> responder.respond(request, writer, out))
                .isInstanceOf(HttpException.class)
                .hasFieldOrPropertyWithValue("httpResponseCode", 403)
                .hasMessageContaining("Forbidden");
    }

    @Test
    void returns404IfThePathDoesNotExist() {
        request = new HttpRequest("GET", "/dir/does-not-exist/", "HTTP/1.1");
        assertThatThrownBy(() -> responder.respond(request, writer, out))
                .isInstanceOf(HttpException.class)
                .hasFieldOrPropertyWithValue("httpResponseCode", 404)
                .hasMessageContaining("Not Found");
    }
}
