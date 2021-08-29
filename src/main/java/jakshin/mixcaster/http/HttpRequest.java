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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
     */
    HttpRequest(@NotNull String method, @NotNull String url, @NotNull String httpVersion) {
        this.httpVersion = httpVersion;
        this.method = method;
        this.url = url;
        this.path = decodePath(url);
        this.headers = new HashMap<>();
    }

    /**
     * Creates a new instance which is a clone of another instance.
     *
     * @param other The other instance to clone.
     * @param overrideUrl URL which overrides the other instance's (optional).
     */
    @SuppressWarnings("unchecked")
    HttpRequest(@NotNull final HttpRequest other, @Nullable final String overrideUrl) {
        this.httpVersion = other.httpVersion;
        this.method = other.method;

        if (overrideUrl != null && !overrideUrl.isBlank()) {
            this.url = overrideUrl;
            this.path = decodePath(overrideUrl);
        }
        else {
            this.url = other.url;
            this.path = other.path;
        }

        // this is line is why we suppress unchecked warnings
        this.headers = (HashMap<String,String>) ((HashMap<String,String>) other.headers).clone();
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
     * All HTTP request headers received, name -> value.
     * Populated during parsing in HttpResponse, or via the clone constructor.
     */
    final Map<String,String> headers;

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
     * Gets the translated byte range for this request, or null if it's not a range retrieval request
     * (i.e. it has no Range header). You must pass the length of the file/resource being requested,
     * because that value is needed for calculating the actual first and last bytes which should be sent.
     *
     * @param fileSize The length of the file/resource requested.
     * @return An object representing the byte range for the request, with translation already performed, or null.
     */
    @Nullable
    ByteRange byteRange(long fileSize) throws HttpException {
        String rangeStr = this.headers.get("Range");
        if (rangeStr == null || rangeStr.isEmpty()) return null;

        ByteRangeParser parser = new ByteRangeParser();
        ByteRange range = parser.parse(rangeStr);
        return parser.translate(range, fileSize);
    }

    /**
     * Reports whether this request appears to have come from Apple's Podcasts or iTunes apps.
     * The determination is based on the User-Agent header, so can easily be spoofed.
     *
     * @return Whether this request appears to have come from Apple's Podcasts or iTunes apps.
     */
    boolean isFromAppleApp() {
        String userAgent = this.headers.get("User-Agent");
        if (userAgent == null) return false;

        // e.g. Podcasts/1575.1.2 CFNetwork/1240.0.4 Darwin/20.6.0
        if (userAgent.startsWith("Podcasts/")) return true;

        // e.g. AppleCoreMedia/1.0.0.20G165 (Macintosh; U; Intel Mac OS X 11_6; en_us)
        // apps other than Apple Podcasts also use this user agent string, which is NBD to us
        // see https://podnews.net/article/applecoremedia-user-agent
        if (userAgent.startsWith("AppleCoreMedia/")) return true;

        // e.g. iTunes/12.3.3 (Macintosh; OS X 10.9.5) AppleWebKit/537.78.2
        return userAgent.startsWith("iTunes/");
    }

    /**
     * Abstracts and URL-decodes a path from the given URL, which may or may not begin with protocol/host,
     * but which must otherwise be absolute (i.e. begin with a slash, if not protocol & host).
     *
     * @param url The URL containing the path.
     * @return The URL-decoded path.
     */
    @NotNull
    private String decodePath(@NotNull String url) {
        String pathStr = url;

        if (pathStr.startsWith("http://")) {
            // strip protocol & host if present
            int index = pathStr.indexOf('/', "http://".length() + 1);
            pathStr = (index == -1) ? "/" : pathStr.substring(index);
        }

        int index = pathStr.indexOf('?');
        if (index > 0) {
            // strip the query string
            pathStr = pathStr.substring(0, index);
        }

        // because we URL-decode here, slashes received encoded will be handled just like unencoded slashes;
        // no further URL-decoding should be done, even (especially) if the path contains percent signs
        return URLDecoder.decode(pathStr, StandardCharsets.UTF_8);
    }
}
