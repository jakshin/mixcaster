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
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;

import static jakshin.mixcaster.logging.Logging.*;

/**
 * Responds to an HTTP request for a folder, by issuing a 403 Forbidden or 404 Not Found response.
 * This responder is not used in the site's root folder; there, a banner page is served instead.
 */
class FolderResponder extends Responder {
    /**
     * Responds to the folder request.
     *
     * @param request The incoming HTTP request.
     * @param writer A writer which can be used to output the response.
     * @param out An output stream which can be used to output the response.
     */
    void respond(@NotNull HttpRequest request, @NotNull Writer writer, @NotNull OutputStream out)
            throws HttpException, InterruptedException, IOException, MixcloudException, TimeoutException, URISyntaxException {

        var folder = new ServableFile(request.path);
        String localPath = folder.getPath();
        logger.log(INFO, "Responding to request for folder: {0}", localPath);

        // delegate to PodcastXmlResponder if this looks like a Mixcloud URL
        if (delegateToPodcastXmlResponder(request, writer, out)) {
            return;
        }

        // check on disk
        if (folder.isFile()) {
            logger.log(INFO, "Folder is actually a file, redirecting: {0}", localPath);

            String filePath = request.path;
            if (filePath.endsWith("/")) filePath = filePath.substring(0, filePath.length() - 1);

            HttpHeaderWriter headerWriter = new HttpHeaderWriter();
            headerWriter.sendRedirectHeadersAndBody(writer, filePath, request.isHead());
            return;
        }

        if (folder.isDirectory()) {
            // we don't allow folder contents to be listed
            throw new HttpException(403, "Forbidden");
        }

        throw new HttpException(404, "Not Found");
    }
}
