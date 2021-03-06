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

import jakshin.mixcaster.utils.MimeTyper;
import jakshin.mixcaster.utils.TrackLocator;
import java.io.*;
import java.util.Date;
import static jakshin.mixcaster.logging.Logging.*;

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
        String localPathStr = TrackLocator.getLocalPath(request.path);
        logger.log(INFO, "Serving file: {0}", localPathStr);

        HttpHeaderWriter headerWriter = new HttpHeaderWriter();
        File localFile = new File(localPathStr);

        // if the file is actually a folder, redirect
        if (localFile.isDirectory()) {
            logger.log(INFO, "File {0} is actually a folder, redirecting", localPathStr);

            String folderPathStr = request.path;
            if (!folderPathStr.endsWith("/")) folderPathStr += "/";

            headerWriter.sendRedirectHeadersAndBody(writer, folderPathStr, request.isHead());
            return;
        }

        // handle If-Modified-Since
        Date lastModified = new Date(localFile.lastModified() / 1000 * 1000);  // truncate milliseconds for comparison

        if (headerWriter.sendNotModifiedHeadersIfNeeded(request, writer, lastModified)) {
            return;  // the request was satisfied via not-modified response headers
        }

        // open the file for reading before we send the response headers,
        // so that if it fails, we can send error response headers cleanly
        try (RandomAccessFile in = new RandomAccessFile(localFile, "r")) {
            // check for a range retrieval request
            long fileSize = localFile.length();
            ByteRange range = request.byteRange(fileSize);

            if (range != null) {
                logger.log(INFO, "Serving bytes {0} - {1}", new Object[] { range.start, range.end });
            }

            // send the response headers
            String contentType = new MimeTyper().guessContentTypeFromName(localPathStr);

            if (range == null) {
                headerWriter.sendSuccessHeaders(writer, lastModified, contentType, localFile.length());
                range = new ByteRange(0, fileSize - 1);
            }
            else {
                headerWriter.sendRangeSuccessHeaders(writer, lastModified, contentType, fileSize, range.start, range.end);
            }

            // read the appropriate part of the file & output, if needed
            if (!request.isHead()) {
                if (range.start > 0) {
                    // seek to the first byte of the partial request
                    in.seek(range.start);
                }

                long bytesLeftToSend = range.size();
                final byte[] buf = new byte[100_000];

                while (true) {
                    int byteCount = in.read(buf, 0, buf.length);
                    if (byteCount < 0) break;

                    if (bytesLeftToSend < byteCount) byteCount = (int) bytesLeftToSend;
                    out.write(buf, 0, byteCount);

                    bytesLeftToSend -= byteCount;
                    if (bytesLeftToSend <= 0) break;
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
