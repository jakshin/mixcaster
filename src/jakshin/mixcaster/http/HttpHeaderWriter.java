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

import jakshin.mixcaster.Main;
import jakshin.mixcaster.utils.DateFormatter;
import java.io.IOException;
import java.io.Writer;
import java.text.ParseException;
import java.util.Date;
import static jakshin.mixcaster.logging.Logging.*;

/**
 * A thingy which knows how to write HTTP headers.
 * It can also send the message body for HTTP errors.
 */
class HttpHeaderWriter {
    /**
     * Sends HTTP success headers for a complete response (response code 200).
     *
     * @param writer The writer to output the headers to.
     * @param contentLastModified When the content was last modified.
     * @param contentType The content's MIME type.
     * @param contentLength The content's length.
     * @throws IOException
     */
    void sendSuccessHeaders(Writer writer, Date contentLastModified, String contentType, long contentLength)
            throws IOException {
        StringBuilder out = new StringBuilder(500);
        StringBuilder log = new StringBuilder(500);

        this.addHeader(out, log, "HTTP/1.1 200 OK");
        this.addCommonHeaders(out, log);

        this.addHeader(out, log, "Last-Modified: %s", DateFormatter.format(contentLastModified));
        this.addHeader(out, log, "Content-Type: %s", contentType);
        this.addHeader(out, log, "Content-Length: %d", contentLength);

        logger.log(DEBUG, "Sending HTTP response headers{0}", log);

        writer.write(out.toString());
        writer.write("\r\n");
        writer.flush();
    }

    /**
     * Sends HTTP success headers for a partial response to a range request (response code 206).
     *
     * @param writer The writer to output the headers to.
     * @param contentLastModified When the content was last modified.
     * @param contentType The content's MIME type.
     * @param fullContentLength The content's full length (the length of the file, not the part being sent).
     * @param firstByte The first byte being sent in the partial response.
     * @param lastByte The last byte being sent in the partial response.
     * @throws IOException
     */
    void sendRangeSuccessHeaders(Writer writer, Date contentLastModified, String contentType,
            long fullContentLength, long firstByte, long lastByte) throws IOException {
        StringBuilder out = new StringBuilder(500);
        StringBuilder log = new StringBuilder(500);

        this.addHeader(out, log, "HTTP/1.1 206 Partial Content");
        this.addCommonHeaders(out, log);

        this.addHeader(out, log, "Last-Modified: %s", DateFormatter.format(contentLastModified));
        this.addHeader(out, log, "Content-Type: %s", contentType);
        this.addHeader(out, log, "Content-Length: %d", (lastByte - firstByte + 1));  // the length of the partial response
        this.addHeader(out, log, "Content-Range: bytes %d-%d/%d", firstByte, lastByte, fullContentLength);

        logger.log(DEBUG, "Sending HTTP response headers for range request{0}", log);

        writer.write(out.toString());
        writer.write("\r\n");
        writer.flush();
    }

    /**
     * Sends HTTP not-modified headers (response code 304).
     *
     * @param writer The writer to output the headers to.
     * @throws IOException
     */
    void sendNotModifiedHeaders(Writer writer) throws IOException {
        StringBuilder out = new StringBuilder(500);
        StringBuilder log = new StringBuilder(500);

        this.addHeader(out, log, "HTTP/1.1 304 Not Modified");
        this.addCommonHeaders(out, log);

        logger.log(DEBUG, "Sending HTTP not-modified response headers{0}", log);

        writer.write(out.toString());
        writer.write("\r\n");
        writer.flush();
    }

    /**
     * Sends HTTP not-modified headers (response code 304), if they are needed.
     * Returns an indicator of whether the headers were needed, meaning that they satisfied the request,
     * and further processing should be aborted.
     *
     * @param request The HTTP request, from which the If-Modified-Since header is obtained.
     * @param writer The writer to output the headers to, if they are needed.
     * @param resourceLastModified The date/time at which the resource being requested was last modified.
     * @return Whether not-modified headers were output in order to satisfy the request.
     * @throws IOException
     */
    public boolean sendNotModifiedHeadersIfNeeded(HttpRequest request, Writer writer, Date resourceLastModified)
            throws IOException {
        try {
            if (request.headers.containsKey("If-Modified-Since")) {
                Date ifModifiedSince = DateFormatter.parse(request.headers.get("If-Modified-Since"));

                if (ifModifiedSince.compareTo(resourceLastModified) >= 0) {
                    new HttpHeaderWriter().sendNotModifiedHeaders(writer);
                    return true;  // not-modified headers sent, response has been satisfied
                }
            }
        }
        catch (ParseException ex) {
            // log and continue without If-Modified-Since handling
            String msg = String.format("Invalid If-Modifed-Since header: %s", request.headers.get("If-Modified-Since"));
            logger.log(WARNING, msg, ex);
        }

        return false;  // response was not satisfied
    }

    /**
     * Sends HTTP headers for a redirect response (response code 301),
     * along with a minimal message body noting the resource's new location.
     *
     * @param writer The writer to output the headers to.
     * @param newLocationUrl The URL to redirect to.
     * @param isHeadRequest Whether the error is in response to an HTTP HEAD request.
     * @throws IOException
     */
    void sendRedirectHeadersAndBody(Writer writer, String newLocationUrl, boolean isHeadRequest) throws IOException {
        StringBuilder out = new StringBuilder(500);
        StringBuilder log = new StringBuilder(500);

        this.addHeader(out, log, "HTTP/1.1 301 Moved Permanently");
        this.addCommonHeaders(out, log);

        this.addHeader(out, log, "Location: %s", newLocationUrl);
        this.addHeader(out, log, "Content-Type: text/plain");

        String messageBody = null;
        if (!isHeadRequest) {
            messageBody = "Moved to " + newLocationUrl + "\r\n";
            this.addHeader(out, log, "Content-Length: %d", messageBody.length());
        }

        logger.log(DEBUG, "Sending HTTP redirect response headers{0}", log);

        writer.write(out.toString());
        writer.write("\r\n");

        if (messageBody != null) {
            writer.write(messageBody);
        }

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

        StringBuilder out = new StringBuilder(500);
        StringBuilder log = new StringBuilder(500);

        this.addHeader(out, log, "HTTP/1.1 %d %s", responseCode, reasonPhrase);
        this.addCommonHeaders(out, log);

        this.addHeader(out, log, "Content-Type: text/plain");

        String messageBody = null;
        if (!isHeadRequest) {
            messageBody = err.getClass().getCanonicalName() + ": " + err.getMessage() + "\r\n";
            this.addHeader(out, log, "Content-Length: %d", messageBody.length());
        }

        logger.log(DEBUG, "Sending HTTP error response headers{0}", log);

        writer.write(out.toString());
        writer.write("\r\n");

        if (messageBody != null) {
            writer.write(messageBody);
        }

        writer.flush();
    }

    /**
     * Add common HTTP headers which are included in every response, success or failure,
     * to both of the passed StringBuilders (one formatted for sending to the client, one formatted for logging).
     *
     * @param out Collects the headers formatted for sending to the client.
     * @param log Collects the headers formatted for logging.
     */
    private void addCommonHeaders(StringBuilder out, StringBuilder log) {
        this.addHeader(out, log, "Date: %s", DateFormatter.format(new Date()));
        this.addHeader(out, log, "Server: Mixcaster/%s (%s)", Main.version, System.getProperty("os.name"));
        this.addHeader(out, log, "Connection: close");
        this.addHeader(out, log, "Accept-Ranges: bytes");
    }

    /**
     * Adds an HTTP header to both of the passed StringBuilders
     * (one formatted for sending to the client, one formatted for logging).
     *
     * @param out Collects the headers formatted for sending to the client.
     * @param log Collects the headers formatted for logging.
     * @param format The header's format string.
     * @param params Any parameters needed to perform formatting on the format string.
     */
    private void addHeader(StringBuilder out, StringBuilder log, String format, Object... params) {
        String header = (params.length > 0) ? String.format(format, params) : format;

        out.append(header);
        out.append("\r\n");

        log.append(HttpHeaderWriter.headerPrefix);
        log.append(header);
    }

    /** The prefix used to put each logged header on its own line. */
    private static final String headerPrefix = String.format("%n    <- ");
}
