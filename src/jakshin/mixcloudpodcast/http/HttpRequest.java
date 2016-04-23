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

package jakshin.mixcloudpodcast.http;

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
     * @param url The requested URL.
     * @param httpVersion The request's HTTP version.
     * @throws UnsupportedEncodingException
     */
    HttpRequest(String method, String url, String httpVersion) throws UnsupportedEncodingException {
        this.httpVersion = httpVersion;
        this.method = method;
        this.url = URLDecoder.decode(url, "UTF-8");
    }

    /**
     * The request's HTTP version.
     * Only version 1.x is supported.
     */
    public final String httpVersion;

    /** The request's HTTP method. */
    public final String method;

    /**
     * The requested URL. This may be a complete URL starting with "http://",
     * or an absolute path, which includes no protocol, host or port information.
     */
    public final String url;

    /**
     * Reports whether this is a HEAD request.
     * @return Whether this is a HEAD request.
     */
    public boolean isHead() {
        return (this.method != null && this.method.equals("HEAD"));
    }

    /**
     * Returns the value of the Host header, if present.
     * @return Host header value, including port (e.g. "localhost:25683"), or null if no Host header was received.
     */
    public String host() {
        return this.headers.get("Host");
    }

    /**
     * All HTTP request headers received, name -> value.
     */
    public final Map<String,String> headers = new HashMap<>(10);
}
