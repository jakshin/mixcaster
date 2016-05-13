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
import java.io.*;
import java.text.ParseException;
import java.util.Date;
import static jakshin.mixcaster.logging.Logging.*;

/**
 * Responds to an HTTP request in the root of the site with a banner page.
 * Our banner page is stored as a resource.
 */
class BannerResponder {
    /**
     * Responds to the request with a banner page.
     *
     * @param request The incoming HTTP request.
     * @param writer A writer which can be used to output the response.
     * @throws IOException
     * @throws ParseException
     */
    void respond(HttpRequest request, Writer writer) throws IOException, ParseException {
        logger.log(INFO, "Serving banner page");

        // handle If-Modified-Since
        Date lastModified = DateFormatter.parse("Thu, 12 May 2016 03:00:00 GMT");  // banner.html creation, no milliseconds
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

        // read the resource into a buffer, if we haven't already done so;
        // we do this in its own step so that we can send a Content-Length response header, and cache
        synchronized (BannerResponder.resourceBufferLock) {
            if (BannerResponder.resourceBuffer == null) {
                logger.log(DEBUG, "Loading banner.html resource");
                StringBuilder sb = new StringBuilder(41_000);  // a bit larger than banner.html

                try (InputStream in = this.getClass().getResourceAsStream("banner.html")) {
                    final char[] buf = new char[1024];

                    try (Reader reader = new InputStreamReader(in, "UTF-8")) {
                        while (true) {
                            int count = reader.read(buf, 0, buf.length);
                            if (count < 0) break;

                            sb.append(buf, 0, count);
                        }
                    }
                }

                String buffer = sb.toString().replace("{{version}}", Main.version);
                BannerResponder.resourceBuffer = buffer;
            }
            else {
                logger.log(DEBUG, "Retrieved banner.html from cache");
            }
        }

        // send the response headers;
        // we don't expect to receive a Range header for this type of request
        headerWriter.sendSuccessHeaders(writer, lastModified, "text/html", BannerResponder.resourceBuffer.length());

        // send the resource, if needed
        if (!request.isHead()) {
            writer.write(BannerResponder.resourceBuffer);
            writer.flush();
        }
    }

    /** A buffer where we cache the resource on first use. */
    private static String resourceBuffer;

    /** An object on which we synchronize writes to resourceBuffer. */
    private static final Object resourceBufferLock = new Object();
}
