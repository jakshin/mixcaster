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

record MixcloudMusicUrl(@NotNull String urlStr) {
    /**
     * Gets some HTTP response headers from the music URL, using a HEAD request.
     */
    @NotNull
    ResponseHeaders getHeaders() throws IOException, MixcloudException {
        HttpURLConnection conn = null;

        try {
            logger.log(DEBUG, "Getting HEAD of URL: {0}", urlStr);

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("User-Agent", System.getProperty("user_agent"));
            conn.setRequestProperty("Referer", urlStr);
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
