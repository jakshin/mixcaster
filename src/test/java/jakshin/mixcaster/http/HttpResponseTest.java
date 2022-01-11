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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.LogManager;

import static jakshin.mixcaster.logging.Logging.logger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the HttpResponse class.
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpResponseTest {
    @Mock
    private Socket socket;

    private HttpResponse httpResponse;
    private ByteArrayOutputStream out;

    // every test method needs to call this
    private void setUpInput(@NotNull String inputStr) {
        try {
            ByteArrayInputStream input = new ByteArrayInputStream(inputStr.getBytes(StandardCharsets.ISO_8859_1));
            when(socket.getInputStream()).thenReturn(input);
        }
        catch (IOException ex) {
            ex.printStackTrace();
            fail("Could not set up input: " + ex.getMessage());
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        httpResponse = new HttpResponse(socket);

        out = new ByteArrayOutputStream();
        when(socket.getOutputStream()).thenReturn(out);

        System.setProperty("http_port", "1234");

        LogManager.getLogManager().reset();
        logger.setLevel(Level.OFF);
    }

    private record Response(String headers, String body) {}

    private Response parseResponse(@NotNull ByteArrayOutputStream out) {
        String responseStr = out.toString(StandardCharsets.UTF_8);
        int index = responseStr.indexOf("\r\n\r\n");
        String headers = responseStr.substring(0, index + 2);
        String body = responseStr.substring(index + 4);
        return new Response(headers, body);
    }

    private void validateResponse(@NotNull ByteArrayOutputStream out,
                                  boolean isHeadRequest,
                                  int expectedResponseCode,
                                  String expectedReason) {
        Response response = parseResponse(out);

        String expectedFirstLine = "HTTP/1.1 " + expectedResponseCode + " " + expectedReason + "\r\n";
        assertThat(response.headers).startsWith(expectedFirstLine);

        Utilities.parseDateHeader("Date", response.headers);
        assertThat(response.headers).containsOnlyOnce("Connection: close\r\n");

        if (expectedResponseCode >= 400) {
            assertThat(response.headers).containsOnlyOnce("Content-Type: text/html\r\n");

            if (! isHeadRequest) {
                assertThat(response.body).contains(expectedReason);
            }
        }

        if (isHeadRequest)
            assertThat(response.body).isEmpty();
        else {
            assertThat(response.body).isNotEmpty();
            assertThat(response.headers).containsOnlyOnce("Content-Length: " + response.body.length() + "\r\n");
        }
    }

    @Test
    void returns200ToValidGetRequests() {
        setUpInput("""
            GET / HTTP/1.1
            Host: localhost:1234
            """);

        httpResponse.run();
        validateResponse(out, false, 200, "OK");
    }

    @Test
    void returns200ToValidHeadRequests() {
        setUpInput("""
            HEAD / HTTP/1.1
            Host: localhost:1234
            """);

        httpResponse.run();
        validateResponse(out, true, 200, "OK");
    }

    @Test
    void returns505ToUnsupportedHttpVersionGetRequests() {
        setUpInput("""
            GET / HTTP/2
            Host: localhost:1234
            """);

        httpResponse.run();

        String expectedReason = "HTTP Version HTTP/2 not supported";
        validateResponse(out, false, 505, expectedReason);
    }

    @Test
    void returns505ToUnsupportedHttpVersionHeadRequests() {
        setUpInput("""
            HEAD / HTTP/2
            Host: localhost:1234
            """);

        httpResponse.run();

        String expectedReason = "HTTP Version HTTP/2 not supported";
        validateResponse(out, true, 505, expectedReason);
    }

    private void makeRequestWithMethod(String method) {
        String request = method + " / HTTP/1.1\r\n";
        request += "Host: localhost:1234\r\n";
        setUpInput(request);

        httpResponse.run();

        String expectedReason = String.format("Method %s Not Allowed", method);
        validateResponse(out, false, 405, expectedReason);
    }

    @Test
    void returns405ToPostRequests() {
        makeRequestWithMethod("POST");
    }

    @Test
    void returns405ToPutRequests() {
        makeRequestWithMethod("PUT");
    }

    @Test
    void returns405ToDeleteRequests() {
        makeRequestWithMethod("DELETE");
    }

    @Test
    void returns405ToConnectRequests() {
        makeRequestWithMethod("CONNECT");
    }

    @Test
    void returns405ToOptionsRequests() {
        makeRequestWithMethod("OPTIONS");
    }

    @Test
    void returns405ToTraceRequests() {
        makeRequestWithMethod("TRACE");
    }

    @Test
    void returns405ToPatchRequests() {
        makeRequestWithMethod("PATCH");
    }

    @Test
    void returns400WhenNoUrlIsReceived() {
        setUpInput("""
            GET  HTTP/1.1
            Host: localhost:1234
            """);

        httpResponse.run();

        String expectedReason = "Bad Request: GET  HTTP/1.1";
        validateResponse(out, false, 400, expectedReason);
    }

    @Test
    void returns400WhenNoRequestHeadersAreReceived() {
        setUpInput("");

        httpResponse.run();

        String expectedReason = "No request headers received";
        validateResponse(out, false, 400, expectedReason);
    }

    @Test
    void returns400WhenNoHostHeaderIsReceived() {
        setUpInput("""
            GET / HTTP/1.1
            """);

        httpResponse.run();

        String expectedReason = "No Host header received";
        validateResponse(out, false, 400, expectedReason);
    }

    @Test
    void parsesContinuationLines() {
        setUpInput("""
            GET / HTTP/1.1
            Host:
            \t    localhost:1234
            """);

        httpResponse.run();
        validateResponse(out, false, 200, "OK");
    }

    @Test
    void ignoresHeadersItCannotParse() {
        setUpInput("""
            GET / HTTP/1.1
            this can't be parsed, and should be ignored
            Host: localhost:1234
            """);

        httpResponse.run();
        validateResponse(out, false, 200, "OK");
    }

    @Test
    @Order(1)  // so HttpHeaderWriter won't have the error.html resource cached yet
    void sendsATextErrorResponseIfItCannotSendHtml() {
        try (MockedStatic<ResourceLoader> mockedStatic = mockStatic(ResourceLoader.class)) {
            MockedStatic.Verification loadResourceAsText = () -> ResourceLoader.loadResourceAsText(anyString(), anyInt());
            mockedStatic.when(loadResourceAsText).thenThrow(IOException.class);

            setUpInput("invalid");

            httpResponse.run();

            String expectedReason = "Bad Request: invalid";
            Response response = parseResponse(out);
            assertThat(response.headers).startsWith("HTTP/1.1 400 " + expectedReason + "\r\n");
            Utilities.parseDateHeader("Date", response.headers);
            assertThat(response.headers).containsOnlyOnce("Connection: close\r\n");
            assertThat(response.headers).containsOnlyOnce("Content-Type: text/plain\r\n");
            assertThat(response.headers).containsOnlyOnce("Content-Length: " + response.body.length() + "\r\n");
            assertThat(response.body).contains(expectedReason);
        }
    }
}
