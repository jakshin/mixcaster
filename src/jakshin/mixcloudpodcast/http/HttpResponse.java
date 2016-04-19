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

import java.io.*;
import java.net.Socket;
import java.util.Locale;

/**
 * Provides a response to a single incoming HTTP request, first parsing headers to understand the request,
 * and delegating to an appropriate HttpResponder instance.
 */
class HttpResponse implements Runnable {
    /**
     * Creates a new instance of the class.
     * @param socket The HTTP socket.
     */
    HttpResponse(Socket socket) {
        this.socket = socket;
    }

    /**
     * Parses the incoming HTTP request, and provides a response.
     */
    @Override
    public void run() {
        // we manually close readers/writers/streams because the socket is closed when any of them are closed,
        // so we need to control their life-cycle carefully
        BufferedReader reader = null;
        BufferedWriter writer = null;
        BufferedOutputStream out = null;

        try {
            // parse and check the request
            reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), "ISO-8859-1"));
            HttpRequest request = this.parseRequestHeaders(reader);

            if (request.httpVersion == null || !request.httpVersion.contains("/1.")) {
                throw new HttpException(505, String.format("HTTP Version %s not supported", request.httpVersion));
            }

            if (request.method == null || (!request.method.equals("GET") && !request.method.equals("HEAD"))) {
                throw new HttpException(405, String.format("Method %s Not Allowed", request.method));
            }

            if (request.url == null || request.url.isEmpty()) {
                throw new HttpException(400, "Bad Request: empty URL");
            }

            // route the request
            if (this.getUrlWithoutQueryOrReference(request.url).toLowerCase(Locale.ROOT).endsWith(".xml")) {
                // RSS XML request
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), 100_000);
                new XmlResponder().respond(request, writer);
            }
            else {
                // any other request must be for a file
                out = new BufferedOutputStream(socket.getOutputStream(), 100_000);
                new FileResponder().respond(request, out);
            }
        }
        catch (HttpException ex) {
            // TODO logging, HTTP error dictated by the exception
            System.out.println(ex.getClass().getCanonicalName() + ": " + ex.getMessage());
        }
        catch (Throwable ex) {
            // TODO logging, 500 error
            // the HTTP response headers may already have been written out, but try anyway
            System.out.println(ex.getClass().getCanonicalName() + ": " + ex.getMessage());
        }
        finally {
            this.closeAThing(reader);
            this.closeAThing(writer);
            this.closeAThing(out);
        }
    }

    /**
     * Parses the incoming HTTP request headers into an HttpRequest object.
     *
     * @param reader The reader from which request headers should be read.
     * @return The HTTP request.
     * @throws IOException
     */
    private HttpRequest parseRequestHeaders(BufferedReader reader) throws IOException, HttpException {
        HttpRequest request = null;
        String lastHeaderName = null;

        while (true) {
            String line = reader.readLine();
            if (line == null || line.isEmpty()) break;
            System.out.println(line);  // TODO remove test code

            if (request == null) {
                String[] parts = line.split("\\s+");
                if (parts.length != 3) {
                    throw new HttpException(400, "Bad Request: " + line);
                }

                request = new HttpRequest(parts[0], parts[1], parts[2]);
            }
            else if (lastHeaderName != null && Character.isWhitespace(line.charAt(0))) {
                // continuation line
                String oldValue = request.headers.get(lastHeaderName);
                request.headers.put(lastHeaderName, oldValue + line.trim());
            }
            else {
                int colon = line.indexOf(':');

                if (colon > 0) {
                    String headerName = line.substring(0, colon);
                    String value = line.substring(colon + 1).trim();
                    request.headers.put(headerName, value);
                    lastHeaderName = headerName;
                }
                else {
                    // TODO logging
                }
            }
        }

        return request;
    }

    /**
     * Gets a version of the URL stripped of any query string or reference/anchor.
     *
     * @param url The URL to strip.
     * @return The stripped URL.
     */
    private String getUrlWithoutQueryOrReference(String url) {
        int index = url.indexOf('?');
        if (index != -1) {
            url = url.substring(0, index);
        }

        index = url.indexOf('#');
        if (index != -1) {
            url = url.substring(0, index);
        }

        return url;
    }

    /**
     * Convenience method which closes something which can be closed.
     * @param thing The thing to be closed.
     */
    private void closeAThing(Closeable thing) {
        if (thing == null) return;

        try {
            thing.close();
        }
        catch (IOException ex) {
            // responders are expected to flush at the end of finishResponse(), making this unlikely
            // TODO logging
        }
    }

    /** The socket on which the HTTP request was received. */
    private final Socket socket;
}
