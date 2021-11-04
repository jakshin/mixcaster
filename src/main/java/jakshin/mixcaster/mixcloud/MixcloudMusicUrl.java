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

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

import static jakshin.mixcaster.logging.Logging.DEBUG;
import static jakshin.mixcaster.logging.Logging.logger;

/**
 * The URL of a Mixcloud music file, including a query string. For example:
 * https://stream1.mixcloud.com/secure/c/m4a/64/7/6/d/2/ea68-9849-4bd4-a2d7-500b34b4f091.m4a?sig=lUzqTvegL68sOJJj_mXXqA
 * (the subdomain can range from "stream1" to "stream18", and they all seem to hold the same music files).
 */
record MixcloudMusicUrl(@NotNull String urlStr) {
    /**
     * Provides the local URL for a music file, given some Mixcloud info about it.
     *
     * @param localHostAndPort The local HTTP server's host:port.
     * @param mixcloudUsername The username of the Mixcloud user who owns the music.
     * @param slug The last path component of Mixcloud's web URL about the music
     *             (which their GraphQL API calls "slug").
     * @return The music file's complete local URL.
     */
    @NotNull
    String localUrl(@NotNull String localHostAndPort, @NotNull String mixcloudUsername, @NotNull String slug) {
        // strip any query string (expected to be present)
        int pos = urlStr.indexOf('?');
        String url = (pos == -1) ? urlStr : urlStr.substring(0, pos);

        String extension = url.substring(url.lastIndexOf('.'));  // includes the dot, e.g. ".m4a"
        return "http://" + localHostAndPort + "/" + mixcloudUsername + "/" + slug + extension;
    }

    /**
     * Gets some HTTP response headers from the music URL, using a HEAD request.
     * @param timeoutMillis Timeout for connects and reads; 0 means no timeout.
     */
    @SuppressWarnings("SameParameterValue")
    @NotNull
    ResponseHeaders getHeaders(int timeoutMillis) throws IOException, MixcloudException {
        HttpURLConnection conn = null;

        try {
            logger.log(DEBUG, "Getting HEAD of URL: {0}", urlStr);

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("User-Agent", System.getProperty("user_agent"));
            conn.setRequestProperty("Referer", urlStr);
            conn.setConnectTimeout(timeoutMillis);
            conn.setReadTimeout(timeoutMillis);
            conn.connect();

            String contentType = conn.getContentType();
            if (!contentType.startsWith("audio/") && !contentType.startsWith("video/")) {
                String msg = String.format("Unexpected Content-Type header: %s", contentType);
                throw new MixcloudException(msg, urlStr);
            }

            long length = conn.getContentLengthLong();
            if (length < 0) {
                throw new MixcloudException("The content length is not known", urlStr);
            }

            long lastModified = conn.getLastModified();
            if (lastModified == 0) {
                throw new MixcloudException("The last-modified date/time is not known", urlStr);
            }

            var headers = new ResponseHeaders();
            headers.contentType = contentType;
            headers.contentLength = length;
            headers.lastModified = new Date(lastModified);
            return headers;
        }
        catch (Throwable ex) {
            if (conn != null) {
                // only disconnect when something has gone wrong,
                // otherwise we want to reuse the network connection
                conn.disconnect();
            }

            throw ex;
        }
    }

    /**
     * A container for a few HTTP response headers.
     */
    @SuppressWarnings("PMD.CommentRequired")
    static class ResponseHeaders {
        long contentLength;
        String contentType;
        Date lastModified;
    }
}
