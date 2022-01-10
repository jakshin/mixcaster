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

import jakshin.mixcaster.utils.AppVersion;
import jakshin.mixcaster.utils.ResourceLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.StringWriter;
import java.text.ParseException;
import java.util.Date;
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
 * Unit tests for the BannerResponder class.
 */
@ExtendWith(MockitoExtension.class)
class BannerResponderTest {
    private BannerResponder responder;
    private HttpRequest request;
    private StringWriter writer;

    @BeforeAll
    static void beforeAll() {
        System.setProperty("http_port", "6499");

        LogManager.getLogManager().reset();
        logger.setLevel(Level.OFF);
    }

    @BeforeEach
    void setUp() {
        synchronized (BannerResponder.class) {
            BannerResponder.resourceBuffer = null;
        }

        responder = new BannerResponder();
        request = new HttpRequest("GET", "/", "HTTP/1.1");
        writer = new StringWriter(8192);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void handlesIfModifiedSince() throws IOException, ParseException {
        request.headers.put("If-Modified-Since", "Thu, 12 May 2016 04:00:00 GMT");

        responder.respond(request, writer);
        assertThat(writer.toString()).startsWith("HTTP/1.1 304 Not Modified");

        request.headers.put("If-Modified-Since", "Thu, 12 May 2016 02:00:00 GMT");
        Utilities.resetStringWriter(writer);

        responder.respond(request, writer);
        assertThat(writer.toString()).startsWith("HTTP/1.1 200 OK");
    }

    @Test
    void cachesTheResource() throws IOException, ParseException {
        AtomicReference<String> resourcePath = new AtomicReference<>();
        AtomicInteger usedBufferSize = new AtomicInteger(-1);

        try (MockedStatic<ResourceLoader> mockedStatic = mockStatic(ResourceLoader.class)) {
            MockedStatic.Verification loadResourceAsText = () -> ResourceLoader.loadResourceAsText(anyString(), anyInt());

            mockedStatic.when(loadResourceAsText).then((invocation) -> {
                Object[] args = invocation.getArguments();
                resourcePath.set((String) args[0]);
                usedBufferSize.set((int) args[1]);
                return new StringBuilder("fake");
            });

            responder.respond(request, writer);
            responder.respond(request, writer);

            // we should have only loaded the resource once
            mockedStatic.verify(loadResourceAsText, times(1));
        }

        // we should use a buffer big enough to hold the resource
        StringBuilder resource = ResourceLoader.loadResourceAsText(resourcePath.get(), 8192);
        int minBufferSize = resource.length();
        assertThat(usedBufferSize.get()).isGreaterThanOrEqualTo(minBufferSize);
    }

    private void validateResponse(String response, boolean headRequest) {
        int index = response.indexOf("\r\n\r\n");
        String headers = response.substring(0, index + 2);
        String body = response.substring(index + 4);

        assertThat(headers).startsWith("HTTP/1.1 200 OK\r\n");
        assertThat(headers).containsOnlyOnce("Cache-Control: no-cache\r\n");
        assertThat(headers).containsOnlyOnce("Connection: close\r\n");
        assertThat(headers).containsOnlyOnce("Content-Type: text/html\r\n");

        Utilities.parseDateHeader("Date", headers);
        Utilities.parseDateHeader("Last-Modified", headers);

        if (headRequest)
            assertThat(body).isEmpty();
        else
            assertThat(headers).containsOnlyOnce("Content-Length: " + body.length() + "\r\n");
    }

    @Test
    void sendsAppropriateResponseHeaders() throws IOException, ParseException {
        responder.respond(request, writer);
        validateResponse(writer.toString(), false);
    }

    @Test
    void respondsToHeadRequests() throws IOException, ParseException {
        var headRequest = new HttpRequest("HEAD", "/", "HTTP/1.1");
        responder.respond(headRequest, writer);
        validateResponse(writer.toString(), true);
    }

    @Test
    void respondsToGetRequests() throws IOException, ParseException {
        responder.respond(request, writer);

        String response = writer.toString();
        int index = response.indexOf("\r\n\r\n");
        String body = response.substring(index + 4);

        assertThat(body).contains("<!DOCTYPE html>");
        assertThat(body).contains("</html>");
    }

    @Test
    void usesLastModifiedForClientCaching() throws IOException, ParseException {
        // banner.html's content only changes if Mixcaster's version does (not counting dev versions)
        Date lastModified1 = getNextLastModifiedHeader();
        Date lastModified2 = getNextLastModifiedHeader();
        assertThat(lastModified2).isEqualTo(lastModified1);

        AtomicReference<String> version = new AtomicReference<>("1.2.3");

        try (MockedStatic<AppVersion> mockedStatic = mockStatic(AppVersion.class)) {
            //noinspection ResultOfMethodCallIgnored
            mockedStatic.when(AppVersion::raw).thenAnswer((invocation -> version.get()));

            // increment the patch version
            incrementVersion(version, 2);
            Date lastModified3 = getNextLastModifiedHeader();
            assertThat(lastModified3).isAfter(lastModified2);

            // increment the minor version
            incrementVersion(version, 1);
            Date lastModified4 = getNextLastModifiedHeader();
            assertThat(lastModified4).isAfter(lastModified3);

            // increment the major version
            incrementVersion(version, 0);
            Date lastModified5 = getNextLastModifiedHeader();
            assertThat(lastModified5).isAfter(lastModified4);
        }
    }

    private Date getNextLastModifiedHeader() throws IOException, ParseException {
        Utilities.resetStringWriter(writer);
        responder.respond(request, writer);
        return Utilities.parseDateHeader("Last-Modified", writer.toString());
    }

    private void incrementVersion(AtomicReference<String> version, int partToIncrement) {
        String[] parts = version.get().split("\\.");
        int current = Integer.parseInt(parts[partToIncrement]);
        parts[partToIncrement] = String.valueOf(current + 1);
        version.set(String.join(".", parts));
    }

    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    void showsTheAppVersionInTheBanner() throws IOException, ParseException {
        String mockRawVersion = "11.22.33";
        String mockDisplayVersion = "v" + mockRawVersion;

        try (MockedStatic<AppVersion> mockedStatic = mockStatic(AppVersion.class)) {
            mockedStatic.when(AppVersion::raw).thenReturn(mockRawVersion);
            mockedStatic.when(AppVersion::display).thenReturn(mockDisplayVersion);

            responder.respond(request, writer);

            String response = writer.toString();
            int index = response.indexOf("\r\n\r\n");
            String body = response.substring(index + 4);
            assertThat(body).contains(mockDisplayVersion);
        }
    }
}