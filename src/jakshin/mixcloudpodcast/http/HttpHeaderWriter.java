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

import jakshin.mixcloudpodcast.Main;
import jakshin.mixcloudpodcast.utils.DateFormatter;
import java.io.IOException;
import java.io.Writer;
import java.util.Date;

/**
 * A thingy which knows how to write HTTP headers.
 * It can also send the message body for HTTP errors.
 */
class HttpHeaderWriter {
    /**
     * Sends HTTP success headers (response code 200).
     *
     * @param writer The writer to output the headers to.
     * @param contentLastModified When the content was last modified.
     * @param contentLength The content's length.
     * @param contentType The content's MIME type.
     * @throws IOException
     */
    void sendSuccessHeaders(Writer writer, Date contentLastModified, long contentLength, String contentType) throws IOException {
        this.sendHeader(writer, "HTTP/1.1 200 OK");
        this.sendCommonHeaders(writer);

        this.sendHeader(writer, "Last-Modified: %s", DateFormatter.format(contentLastModified));
        // TODO --- enable this.sendHeader(writer, "Accept-Ranges: bytes");
        this.sendHeader(writer, "Content-Length: %d", contentLength);
        this.sendHeader(writer, "Content-Type: %s", contentType);
        this.sendHeader(writer, "Connection: close");
        this.sendHeader(writer, "");
        writer.flush();
    }

    /**
     * Sends HTTP not-modified headers (response code 304).
     *
     * @param writer The writer to output the headers to.
     * @throws IOException
     */
    void sendNotModifiedHeaders(Writer writer) throws IOException {
        this.sendHeader(writer, "HTTP/1.1 304 Not Modified");
        this.sendCommonHeaders(writer);
        writer.flush();
    }

    /**
     * Sends HTTP error headers, along with a semi-helpful message body.
     * If the Throwable is an HttpException, its response code and message will be used;
     * otherwise, a 500 error will be sent with a generic message.
     *
     * @param writer The writer to output the headers to.
     * @param err A Throwable which contains details about the problem.
     * @param isHeadRequest Whether the error is in response to an HTTP HEAD request.
     * @throws IOException
     */
    void sendErrorHeadersAndBody(Writer writer, Throwable err, boolean isHeadRequest) throws IOException {
        int responseCode = 500;
        String reasonPhrase = "Internal Server Error";

        if (err instanceof HttpException) {
            responseCode = ((HttpException) err).httpResponseCode;
            reasonPhrase = err.getMessage();
        }

        this.sendHeader(writer, "HTTP/1.1 %d %s", responseCode, reasonPhrase);
        this.sendCommonHeaders(writer);

        String messageBody = null;
        if (!isHeadRequest) {
            messageBody = err.getClass().getCanonicalName() + ": " + err.getMessage() + "\r\n";
            this.sendHeader(writer, "Content-Length: %d", messageBody.length());
        }

        this.sendHeader(writer, "Content-Type: text/plain");
        this.sendHeader(writer, "Connection: close");
        this.sendHeader(writer, "");

        if (messageBody != null) {
            writer.write(messageBody);
        }

        writer.flush();
    }

    /**
     * Sends common HTTP headers which are included in every response, success or failure.
     *
     * @param writer The writer to output the headers to.
     * @throws IOException
     */
    private void sendCommonHeaders(Writer writer) throws IOException {
        this.sendHeader(writer, "Date: %s", DateFormatter.format(new Date()));
        this.sendHeader(writer, "Server: MixcloudPodcast/%s (%s)", Main.version, System.getProperty("os.name"));
    }

    /**
     * Sends an HTTP header.
     *
     * @param writer The writer to send the header with.
     * @param format The header's format string.
     * @param params Any parameters needed to perform formatting on the format string.
     * @throws IOException
     */
    private void sendHeader(Writer writer, String format, Object... params) throws IOException {
        String formatted = String.format(format, params);
        writer.write(formatted);
        writer.write("\r\n");

        System.out.println("<- " + formatted); // XXX logging
    }
}
