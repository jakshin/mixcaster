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

import jakshin.mixcaster.utils.AppVersion;
import jakshin.mixcaster.utils.DateFormatter;
import jakshin.mixcaster.utils.ResourceLoader;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;

import static jakshin.mixcaster.logging.Logging.*;

/**
 * Responds to an HTTP request in the root of the site with a banner page.
 * Our banner page is stored as a resource.
 */
class BannerResponder extends Responder {
    /**
     * Responds to the request with a banner page.
     *
     * @param request The incoming HTTP request.
     * @param writer A writer which can be used to output the response.
     */
    void respond(@NotNull HttpRequest request, @NotNull Writer writer) throws IOException, ParseException {
        logger.log(INFO, "Responding to request for banner page");

        // handle If-Modified-Since
        HttpHeaderWriter headerWriter = new HttpHeaderWriter();
        Date lastModified = this.getBannerLastModifiedDate();

        if (headerWriter.sendNotModifiedHeadersIfNeeded(request, writer, lastModified)) {
            logger.log(INFO, "Responding with 304 for unmodified banner page");
            return;  // the request was satisfied via not-modified response headers
        }

        // read the resource into a buffer, if we haven't already done so;
        // we do this in its own step so that we can send a Content-Length response header, and cache
        synchronized (BannerResponder.resourceBufferLock) {
            if (BannerResponder.resourceBuffer == null) {
                logger.log(DEBUG, "Loading banner.html resource");
                StringBuilder sb = ResourceLoader.loadResourceAsText("http/banner.html", 41_000);
                BannerResponder.resourceBuffer = sb.toString().replace("{{version}}", AppVersion.display);
            }
            else {
                logger.log(DEBUG, "Retrieved banner.html from cache");
            }
        }

        int length = BannerResponder.resourceBuffer.length();

        // send the response headers;
        // we don't expect to receive a Range header for this type of request
        LinkedHashMap<String,String> additionalHeaders = new LinkedHashMap<>(1);
        additionalHeaders.put("Cache-Control", "no-cache");

        headerWriter.sendSuccessHeaders(writer, lastModified, "text/html", length, additionalHeaders);

        if (request.isHead()) {
            logger.log(INFO, "Done responding to HEAD request for banner page");
            return;
        }

        // send the banner page
        writer.write(BannerResponder.resourceBuffer);
        writer.flush();
        logger.log(INFO, "Done responding to GET request for banner page");
    }

    /**
     * Gets the banner page's last-modified date/time.
     * We start with the banner.html resource's original creation time, then add some time
     * based on the program's current version, so it changes slightly with each new version.
     *
     * @return The banner page's last-modified date/time.
     */
    @NotNull
    private Date getBannerLastModifiedDate() throws ParseException {
        // use a synthetic creation timestamp for banner.html, with no milliseconds
        // (this value is unrelated to the filesystem's last-modified date for the banner.html file)
        Date lastModified = DateFormatter.parse("Thu, 12 May 2016 03:00:00 GMT");

        Calendar cal = Calendar.getInstance();
        cal.setTime(lastModified);

        String[] version = AppVersion.raw.split("\\.");
        if (version.length == 3) {
            // our use of the Cache-Control header causes browsers to send an If-Modified-Since request;
            // add hours for major version, minutes for minor version, and seconds for patch version
            // to implement caching of the banner per Mixcaster version

            int major = Integer.parseInt(version[0]);
            int minor = Integer.parseInt(version[1]);
            int patch = Integer.parseInt(version[2]);

            cal.add(Calendar.HOUR_OF_DAY, major);
            cal.add(Calendar.MINUTE, minor);
            cal.add(Calendar.SECOND, patch);
        }

        return cal.getTime();
    }

    /** A buffer where we cache the resource on first use. */
    private static String resourceBuffer;

    /** An object on which we synchronize writes to resourceBuffer. */
    private static final Object resourceBufferLock = new Object();
}
