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

import jakshin.mixcloudpodcast.utils.DateFormatter;
import jakshin.mixcloudpodcast.utils.MimeTyper;
import jakshin.mixcloudpodcast.utils.TrackLocator;
import java.io.*;
import java.text.ParseException;
import java.util.Date;

/**
 * Responds to an HTTP request for a file, or part of a file.
 */
public class FileResponder {
    /**
     * Responds to the file request.
     *
     * @param request The incoming HTTP request.
     * @param writer A writer which can be used to output the response.
     * @param out An output stream which can be used to output the response.
     * @throws HttpException
     * @throws IOException
     */
    void respond(HttpRequest request, Writer writer, OutputStream out) throws HttpException, IOException {
        String localPathStr = TrackLocator.getLocalPath(request.url);
        File localFile = new File(localPathStr);

        // handle If-Modified-Since
        Date lastModified = new Date(localFile.lastModified() / 1000 * 1000);  // truncate milliseconds for comparison
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
            // XXX logging
            // continue without If-Modified-Since handling
        }

        // open the file for reading before we send the response headers,
        // so that if it fails, we can send error response headers cleanly
        try (FileInputStream in = new FileInputStream(localFile)) {
            // check for a range retrieval request
            long fileSize = localFile.length();
            ByteRange range = request.byteRange(fileSize);

            // send the response headers
            // TODO --- send 206 if partial content, with Content-Range & modified Content-Length
            String contentType = new MimeTyper().guessContentTypeFromName(localPathStr);
            headerWriter.sendSuccessHeaders(writer, lastModified, localFile.length(), contentType);

            // read the appropriate part of the file & output in chunks, if needed
            // TODO --- if a range was requested, send only those bytes
            if (!request.isHead()) {
                final byte[] buf = new byte[100_000];

                while (true) {
                    int byteCount = in.read(buf, 0, buf.length);
                    if (byteCount < 0) break;

                    out.write(buf, 0, byteCount);
                }

                out.flush();
            }

            // the input stream is closed
        }
        catch (FileNotFoundException ex) {
            throw new HttpException(404, "Not Found", ex);
        }
    }
}
