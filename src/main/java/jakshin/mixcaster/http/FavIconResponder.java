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
import jakshin.mixcaster.utils.ResourceLoader;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.text.ParseException;
import java.util.Date;
import static jakshin.mixcaster.logging.Logging.*;

/**
 * Responds to an HTTP request for a favicon. Our favicon.ico is stored as a resource;
 * it came from http://www.softicons.com/social-media-icons/cloud-social-icons-by-graphics-vibe/rss-icon,
 * and was converted to ICO format using http://tools.dynamicdrive.com/favicon/.
 */
class FavIconResponder extends Responder {
    /**
     * Responds to the favicon request.
     *
     * @param request The incoming HTTP request.
     * @param writer A writer which can be used to output the response.
     * @param out An output stream which can be used to output the response.
     */
    void respond(@NotNull HttpRequest request, @NotNull Writer writer, @NotNull OutputStream out)
            throws IOException, ParseException {

        logger.log(INFO, "Responding to request for favicon.ico");

        // handle If-Modified-Since
        HttpHeaderWriter headerWriter = new HttpHeaderWriter();
        Date lastModified = DateFormatter.parse("Sun, 08 May 2016 03:00:00 GMT");  // favicon.ico creation, no milliseconds

        if (headerWriter.sendNotModifiedHeadersIfNeeded(request, writer, lastModified)) {
            logger.log(INFO, "Responding with 304 for unmodified favicon.ico");
            return;  // the request was satisfied via not-modified response headers
        }

        // read the icon resource into a buffer, if we haven't already done so;
        // we do this in its own step so that we can send a Content-Length response header, and cache the icon
        synchronized (FavIconResponder.iconBufferLock) {
            if (FavIconResponder.iconBuffer == null) {
                logger.log(DEBUG, "Loading favicon.ico resource");
                int capacityNeeded = 16_000;  // a bit larger than favicon.ico
                FavIconResponder.iconBuffer = ResourceLoader.loadResourceAsBytes("http/favicon.ico", capacityNeeded);
            }
            else {
                logger.log(DEBUG, "Retrieved favicon.ico from cache");
            }
        }

        // send the response headers;
        // we don't expect to receive a Range header for this type of request
        String contentType = "image/x-icon";
        headerWriter.sendSuccessHeaders(writer, lastModified, contentType, FavIconResponder.iconBuffer.length);

        if (request.isHead()) {
            logger.log(INFO, "Done responding to HEAD request for favicon.ico");
            return;
        }

        // send the icon
        out.write(FavIconResponder.iconBuffer);
        out.flush();
        logger.log(INFO, "Done responding to GET request for favicon.ico");
    }

    /** A buffer where we cache the icon resource on first use. */
    private static byte[] iconBuffer;

    /** An object on which we synchronize writes to iconBuffer. */
    private static final Object iconBufferLock = new Object();
}
