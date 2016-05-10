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

import jakshin.mixcaster.utils.DateFormatter;
import java.io.*;
import java.text.ParseException;
import java.util.Date;
import static jakshin.mixcaster.logging.Logging.*;

/**
 * Responds to an HTTP request for a favicon. Our favicon.ico is stored as a resource;
 * it came from http://www.softicons.com/social-media-icons/cloud-social-icons-by-graphics-vibe/rss-icon,
 * and was converted to ICO format using http://tools.dynamicdrive.com/favicon/.
 */
class FavIconResponder {
    /**
     * Responds to the favicon request.
     *
     * @param request The incoming HTTP request.
     * @param writer A writer which can be used to output the response.
     * @param out An output stream which can be used to output the response.
     * @throws HttpException
     * @throws IOException
     */
    void respond(HttpRequest request, Writer writer, OutputStream out) throws IOException, ParseException {
        logger.log(INFO, "Serving favicon.ico");

        // handle If-Modified-Since
        Date lastModified = DateFormatter.parse("Sun, 08 May 2016 03:00:00 GMT");  // favicon.ico creation, no milliseconds
        HttpHeaderWriter headerWriter = new HttpHeaderWriter();

        try {
            if (request.headers.containsKey("If-Modified-Since")) {
                Date ifModifiedSince = DateFormatter.parse(request.headers.get("If-Modified-Since"));

                if (ifModifiedSince.compareTo(lastModified) >= 0) {
                    headerWriter.sendNotModifiedHeaders(writer);
                    return;
                }
            }
        }
        catch (ParseException ex) {
            // log and continue without If-Modified-Since handling
            String msg = String.format("Invalid If-Modifed-Since header: %s", request.headers.get("If-Modified-Since"));
            logger.log(WARNING, msg, ex);
        }

        // read the icon resource into a buffer, if we haven't already done so;
        // we do this in its own step so that we can send a Content-Length response header, and cache the icon
        synchronized (FavIconResponder.iconBufferLock) {
            if (FavIconResponder.iconBuffer == null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(16_000);  // a bit larger than favicon.ico

                try (InputStream in = this.getClass().getResourceAsStream("favicon.ico")) {
                    final byte[] buf = new byte[2000];

                    while (true) {
                        int count = in.read(buf);
                        if (count < 0) break;

                        buffer.write(buf, 0, count);
                    }
                }

                FavIconResponder.iconBuffer = buffer;
            }
        }

        // send the response headers;
        // we don't expect to receive a Range header for this type of request
        headerWriter.sendSuccessHeaders(writer, lastModified, "image/x-icon", FavIconResponder.iconBuffer.size());

        // send the icon, if needed
        if (!request.isHead()) {
            FavIconResponder.iconBuffer.writeTo(out);
            out.flush();
        }
    }

    /** A buffer where we cache the icon resource on first use. */
    private static ByteArrayOutputStream iconBuffer;

    /** An object on which we synchronize writes to iconBuffer. */
    private static final Object iconBufferLock = new Object();
}
