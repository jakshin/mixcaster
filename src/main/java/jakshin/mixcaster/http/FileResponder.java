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

import jakshin.mixcaster.mixcloud.MixcloudException;
import jakshin.mixcaster.stale.Freshener;
import jakshin.mixcaster.utils.MimeHelper;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.concurrent.TimeoutException;

import static jakshin.mixcaster.logging.Logging.*;

/**
 * Responds to an HTTP request for a file, or part of a file.
 */
class FileResponder extends Responder {
    /**
     * Responds to the file request.
     *
     * @param request The incoming HTTP request.
     * @param writer A writer which can be used to output the response.
     * @param out An output stream which can be used to output the response.
     */
    void respond(@NotNull HttpRequest request, @NotNull Writer writer, @NotNull OutputStream out)
            throws HttpException, InterruptedException, IOException, MixcloudException, TimeoutException, URISyntaxException {

        try {
            var file = new ServableFile(request.path);
            String localPath = file.getPath();
            logger.log(INFO, "Responding to request for file: {0}", localPath);

            // delegate to PodcastXmlResponder if this looks like a Mixcloud URL
            if (delegateToPodcastXmlResponder(request, writer, out)) {
                return;
            }

            // ensure the file really is in our music_dir
            if (! file.getCanonicalPath().startsWith(getMusicDirSetting())) {
                logger.log(WARNING, "Refusing to serve file outside music_dir: {0}", localPath);
                throw new HttpException(403, "Forbidden");
            }

            // if the file is actually a folder, redirect
            HttpHeaderWriter headerWriter = new HttpHeaderWriter();

            if (file.isDirectory()) {
                logger.log(INFO, "File is actually a folder, redirecting: {0}", localPath);

                String folderPathStr = request.path;
                if (!folderPathStr.endsWith("/")) folderPathStr += "/";

                headerWriter.sendRedirectHeadersAndBody(writer, folderPathStr, request.isHead());
                return;
            }

            // update the file's lastUsed attribute, iff it already exists
            // (the intent is to only track lastUsed on files downloaded by Mixcaster)
            new Freshener().updateLastUsedAttr(file.toPath(), true);

            // handle If-Modified-Since
            // this date is only valid if the file exists, so don't use it otherwise
            Date lastModified = new Date(file.lastModified() / 1000 * 1000);  // truncate milliseconds for comparison

            if (file.isFile() && headerWriter.sendNotModifiedHeadersIfNeeded(request, writer, lastModified)) {
                logger.log(INFO, "Responding with 304 for unmodified file: {0}", localPath);
                return;  // the request was satisfied via not-modified response headers
            }

            // open the file for reading before we send the response headers,
            // so that if it fails, we can send error response headers cleanly
            try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
                // check for a range retrieval request
                long fileSize = file.length();
                ByteRange range = request.byteRange(fileSize);

                if (range != null) {
                    logger.log(INFO, "Serving bytes {0} - {1}", new Object[]{ range.start(), range.end() });
                }

                // send the response headers
                String contentType = new MimeHelper().guessContentTypeFromName(localPath);

                if (range == null) {
                    headerWriter.sendSuccessHeaders(writer, lastModified, contentType, file.length());
                    range = new ByteRange(0, fileSize - 1);
                }
                else {
                    headerWriter.sendRangeSuccessHeaders(writer, lastModified, contentType,
                                                        fileSize, range.start(), range.end());
                }

                if (request.isHead()) {
                    logger.log(INFO, "Done responding to HEAD request for file: {0}", localPath);
                    return;
                }

                // read the appropriate part of the file & output
                if (range.start() > 0) {
                    // seek to the first byte of the partial request
                    in.seek(range.start());
                }

                long bytesLeftToSend = range.size();
                final byte[] buf = new byte[65536];

                while (true) {
                    int byteCount = in.read(buf, 0, buf.length);
                    if (byteCount < 0) break;

                    if (bytesLeftToSend < byteCount) byteCount = (int) bytesLeftToSend;
                    out.write(buf, 0, byteCount);

                    bytesLeftToSend -= byteCount;
                    if (bytesLeftToSend <= 0) break;
                }

                out.flush();
                logger.log(INFO, "Done responding to GET request for file: {0}", localPath);
            }
            catch (FileNotFoundException ex) {
                throw new HttpException(404, "Not Found", ex);
            }
        }
        catch (SocketException ex) {
            String message = ex.getMessage();
            if (("Broken pipe".equals(message) || "Protocol wrong type for socket".equals(message))
                    && request.isFromAppleApp()) {
                // while streaming a podcast episode without downloading it, Apple's Podcasts and iTunes apps
                // regularly close the socket connection before they've received all bytes they requested;
                // this seems to be normal behavior
                logger.log(INFO, "An Apple app closed the connection early, which is normal");
                return;
            }

            // this SocketException might actually be a problem
            throw ex;
        }
    }

    /**
     * Gets the music_dir configuration setting.
     * @return music_dir, as an absolute path, with a trailing slash.
     */
    @NotNull
    private static String getMusicDirSetting() {
        String musicDir = System.getProperty("music_dir");

        if (musicDir.startsWith("~/")) {
            musicDir = System.getProperty("user.home") + musicDir.substring(1);
        }

        if (! musicDir.endsWith("/")) {
            musicDir += "/";
        }

        return musicDir;
    }
}
