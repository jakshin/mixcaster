/*
 * Copyright (c) 2021 Jason Jackson
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

package jakshin.mixcaster.mixcloud;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.find;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MixcloudMusicUrlTest {
    private static final String urlStr = "https://stream.mixcloud.com/a/b/c/d.m4a?sig=blah";
    private static final String testUserAgent = "Test User Agent/1.0";
    private static final int timeoutMillis = 1234;
    private MixcloudMusicUrl mmu;
    private HttpURLConnection mockConnection;

    @BeforeAll
    static void beforeAll() {
        System.setProperty("user_agent", testUserAgent);
    }

    @AfterAll
    static void afterAll() {
        System.clearProperty("user_agent");
    }

    @BeforeEach
    void setUp() throws MixcloudException, IOException {
        mockConnection = mock(HttpURLConnection.class);
        doReturn("audio/mp4").when(mockConnection).getContentType();
        doReturn(12345678L).when(mockConnection).getContentLengthLong();
        doReturn(Instant.now().toEpochMilli()).when(mockConnection).getLastModified();

        mmu = mock(MixcloudMusicUrl.class, withSettings().useConstructor(urlStr));
        doCallRealMethod().when(mmu).localUrl(anyString(), anyString(), anyString());
        doCallRealMethod().when(mmu).getHeaders(anyInt());
        doReturn(mockConnection).when(mmu).openConnection(anyString());
    }

    @Test
    void localUrlWorks() {
        String host = "foo:123";

        MixcloudMusicUrl mmu = new MixcloudMusicUrl("https://stream.mixcloud.com/a/b/c/d.m4a?sig=blah");
        String result = mmu.localUrl(host, "Somebody", "some-lovely-music");

        String expected = "http://" + host + "/Somebody/some-lovely-music.m4a";
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void makesHeadRequests() throws MixcloudException, IOException {
        mmu.getHeaders(timeoutMillis);
        verify(mockConnection).setRequestMethod("HEAD");
    }

    @Test
    void sendsTheConfiguredUserAgent() throws MixcloudException, IOException {
        mmu.getHeaders(timeoutMillis);
        verify(mockConnection).setRequestProperty("User-Agent", testUserAgent);
    }

    @Test
    void sendRefererWithTheSameDomain() throws MixcloudException, IOException {
        mmu.getHeaders(timeoutMillis);

        var url = new URL(urlStr);
        String host = String.format("%s://%s", url.getProtocol(), url.getHost());
        verify(mockConnection).setRequestProperty(eq("Referer"), find(host));
    }

    @Test
    void usesTheGivenTimeout() throws MixcloudException, IOException {
        int millis = 2345;
        mmu.getHeaders(millis);

        verify(mockConnection).setConnectTimeout(millis);
        verify(mockConnection).setReadTimeout(millis);
    }

    @Test
    void doesNotDisconnectOnSuccess() throws MixcloudException, IOException {
        mmu.getHeaders(timeoutMillis);
        verify(mockConnection, never()).disconnect();
    }

    @Test
    void disconnectsAfterAnError() throws IOException {
        doThrow(new IOException("Testing")).when(mockConnection).connect();

        assertThatThrownBy(() -> mmu.getHeaders(timeoutMillis)).isInstanceOf(IOException.class);
        verify(mockConnection).disconnect();
    }

    @Test
    void throwsIfTheContentTypeIsUnexpected() {
        String contentType = "text/html";
        doReturn(contentType).when(mockConnection).getContentType();

        assertThatThrownBy(() -> mmu.getHeaders(timeoutMillis))
                .isInstanceOf(MixcloudException.class)
                .hasMessageContaining(contentType);
        verify(mockConnection).disconnect();
    }

    @Test
    void throwsIfTheContentLengthIsUnknown() {
        doReturn(-1L).when(mockConnection).getContentLengthLong();

        assertThatThrownBy(() -> mmu.getHeaders(timeoutMillis))
                .isInstanceOf(MixcloudException.class)
                .hasMessageContaining("content length");
        verify(mockConnection).disconnect();
    }

    @Test
    void throwsIfTheLastModifiedDateIsUnknown() {
        doReturn(0L).when(mockConnection).getLastModified();

        assertThatThrownBy(() -> mmu.getHeaders(timeoutMillis))
                .isInstanceOf(MixcloudException.class)
                .hasMessageContaining("last-modified");
        verify(mockConnection).disconnect();
    }

    @Test
    void openConnectionWorks() throws IOException {
        doCallRealMethod().when(mmu).openConnection(anyString());

        HttpURLConnection conn = mmu.openConnection(urlStr);
        URL url = conn.getURL();
        assertThat(url).isEqualTo(new URL(urlStr));
    }
}
