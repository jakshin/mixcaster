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

package jakshin.mixcaster.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * An HTTP request.
 */
class HttpRequest {
    /**
     * Creates a new instance of the class.
     *
     * @param method The request's HTTP method.
     * @param url The requested URL, as received from the client (i.e. not URL-decoded).
     * @param httpVersion The request's HTTP version.
     * @throws UnsupportedEncodingException
     */
    HttpRequest(String method, String url, String httpVersion) throws UnsupportedEncodingException {
        this.httpVersion = httpVersion;
        this.method = method;
        this.url = url;

        // populate the URL-decoded path
        String pathStr = url;

        if (pathStr.startsWith("http://")) {
            // strip protocol & host if present
            int index = pathStr.indexOf('/', "http://".length() + 1);
            pathStr = (index == -1) ? "/" : pathStr.substring(index);
        }

        int index = pathStr.indexOf('?');
        if (index > 0) {
            // strip any query string present
            pathStr = pathStr.substring(0, index);
        }

        this.path = URLDecoder.decode(pathStr, "UTF-8");
    }

    /**
     * The request's HTTP version.
     * Only version 1.x is supported.
     */
    final String httpVersion;

    /** The request's HTTP method. */
    final String method;

    /**
     * The requested URL. This may be a complete URL starting with "http://",
     * or an absolute path, which includes no protocol, host or port information.
     * No URL-decoding is performed on this value, it's as-is from the client.
     */
    final String url;

    /**
     * The requested path.
     * This will be an absolute path, URL-decoded.
     */
    final String path;

    /**
     * Reports whether this is a HEAD request.
     * @return Whether this is a HEAD request.
     */
    boolean isHead() {
        return (this.method != null && this.method.equals("HEAD"));
    }

    /**
     * Returns the value of the Host header, if present.
     * @return Host header value, including port (e.g. "localhost:25683"), or null if no Host header was received.
     */
    String host() {
        return this.headers.get("Host");
    }

    /**
     * Gets the translated byte range for this request, or null if it's not a range retrieval request (no Range header).
     * You must pass the length of the file/resource being requested,
     * because that value is needed for calculating the actual first and last bytes which should be sent.
     *
     * @param fileSize The length of the file/resource requested.
     * @return An object representing the byte range for the request, with translation already performed, or null.
     * @throws HttpException
     */
    ByteRange byteRange(long fileSize) throws HttpException {
        String rangeStr = this.headers.get("Range");
        if (rangeStr == null || rangeStr.isEmpty()) return null;

        ByteRangeParser parser = new ByteRangeParser();
        ByteRange range = parser.parse(rangeStr);
        return parser.translate(range, fileSize);
    }

    /**
     * Reports whether this request appears to have come from iTunes.
     * The determination is based on the User-Agent header, so can easily be spoofed.
     *
     * @return Whether this request appears to have come from iTunes.
     */
    boolean isFromITunes() {
        String userAgent = this.headers.get("User-Agent");
        if (userAgent == null) return false;

        // e.g. iTunes/12.3.3 (Macintosh; OS X 10.9.5) AppleWebKit/537.78.2
        return userAgent.contains("iTunes/");
    }

    /**
     * All HTTP request headers received, name -> value.
     * Populated during parsing in HttpResponse.
     */
    final Map<String,String> headers = new HashMap<>(10);
}
