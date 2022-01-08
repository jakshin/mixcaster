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

import jakshin.mixcaster.utils.ResourceLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogManager;

import static jakshin.mixcaster.logging.Logging.logger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * Unit tests for the FavIconResponder class.
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FavIconResponderTest {
    private FavIconResponder responder;
    private HttpRequest request;
    private StringWriter writer;
    private ByteArrayOutputStream out;

    @BeforeAll
    static void beforeAll() {
        LogManager.getLogManager().reset();
        logger.setLevel(Level.OFF);
    }

    @BeforeEach
    void setUp() {
        responder = new FavIconResponder();
        request = new HttpRequest("GET", "/favicon.ico", "HTTP/1.1");
        writer = new StringWriter(8192);
        out = new ByteArrayOutputStream(16_384);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void handlesIfModifiedSince() throws IOException, ParseException {
        request.headers.put("If-Modified-Since", "Sun, 08 May 2016 04:00:00 GMT");

        responder.respond(request, writer, out);
        assertThat(writer.toString()).startsWith("HTTP/1.1 304 Not Modified");

        request.headers.put("If-Modified-Since", "Sun, 08 May 2016 02:00:00 GMT");
        writer.getBuffer().delete(0, writer.getBuffer().length());

        responder.respond(request, writer, out);
        assertThat(writer.toString()).startsWith("HTTP/1.1 200 OK");
    }

    @Test
    @Order(1)  // must run first, so it can populate FavIconResponder's static iconBuffer field
    void cachesTheResource() throws IOException, ParseException {
        AtomicReference<String> resourcePath = new AtomicReference<>();
        AtomicInteger usedBufferSize = new AtomicInteger(-1);

        try (MockedStatic<ResourceLoader> mockedStatic = mockStatic(ResourceLoader.class)) {
            MockedStatic.Verification loadResourceAsBytes = () -> ResourceLoader.loadResourceAsBytes(anyString(), anyInt());

            mockedStatic.when(loadResourceAsBytes).then((invocation) -> {
                Object[] args = invocation.getArguments();
                resourcePath.set((String) args[0]);
                usedBufferSize.set((int) args[1]);

                // this assumes our current working directory is the project directory
                Path path = Path.of("src/main/resources/jakshin/mixcaster/http/favicon.ico");
                return Files.readAllBytes(path);
            });

            responder.respond(request, writer, out);
            responder.respond(request, writer, out);

            // we should have only loaded the resource once
            mockedStatic.verify(loadResourceAsBytes, times(1));
        }

        // we should use a buffer big enough to hold the resource
        byte[] resource = ResourceLoader.loadResourceAsBytes(resourcePath.get(), 8192);
        int minBufferSize = resource.length;
        assertThat(usedBufferSize.get()).isGreaterThanOrEqualTo(minBufferSize);
    }

    @Test
    void sendsAppropriateResponseHeaders() throws IOException, ParseException {
        responder.respond(request, writer, out);

        String headers = writer.toString();

        assertThat(headers).containsOnlyOnce("Connection: close");
        assertThat(headers).containsOnlyOnce("Content-Length: " + out.size());
        assertThat(headers).containsOnlyOnce("Content-Type: image/x-icon");

        assertThat(headers).doesNotContain("Cache-Control: no-cache");

        Utilities.parseDateHeader("Date", headers);
        Utilities.parseDateHeader("Last-Modified", headers);
    }

    @Test
    void respondsToHeadRequests () throws IOException, ParseException {
        var headRequest = new HttpRequest("HEAD", "/favicon.ico", "HTTP/1.1");

        responder.respond(headRequest, writer, out);

        String headers = writer.toString();
        assertThat(headers).startsWith("HTTP/1.1 200 OK\r\n");
        assertThat(headers).doesNotContain("Cache-Control: no-cache");
        assertThat(headers).containsOnlyOnce("Connection: close\r\n");
        assertThat(headers).containsOnlyOnce("Content-Type: image/x-icon\r\n");
        Utilities.parseDateHeader("Date", headers);
        Utilities.parseDateHeader("Last-Modified", headers);

        byte[] body = out.toByteArray();
        assertThat(body).isEmpty();
    }

    @Test
    void respondsToGetRequests() throws IOException, ParseException {
        responder.respond(request, writer, out);
        byte[] body = out.toByteArray();

        // this assumes our current working directory is the project directory
        Path resourcePath = Path.of("src/main/resources/jakshin/mixcaster/http/favicon.ico");
        byte[] fileData = Files.readAllBytes(resourcePath);
        assertThat(body).isEqualTo(fileData);
    }
}