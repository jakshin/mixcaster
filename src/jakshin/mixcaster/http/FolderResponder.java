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

import jakshin.mixcaster.utils.TrackLocator;
import java.io.*;
import static jakshin.mixcaster.logging.Logging.*;

/**
 * Responds to an HTTP request for a folder, by issuing a 403 Forbidden or 404 Not Found response.
 * This responder is not used in the site's root folder; there, a banner page is served instead.
 */
class FolderResponder {
    /**
     * Responds to the folder request.
     *
     * @param request The incoming HTTP request.
     * @param writer A writer which can be used to output the response.
     * @throws HttpException
     * @throws IOException
     */
    void respond(HttpRequest request, Writer writer) throws HttpException, IOException {
        String localPathStr = TrackLocator.getLocalPath(request.path);
        logger.log(INFO, "Serving folder: {0}", localPathStr);

        // I tried redirecting to podcast.xml, but iTunes doesn't handle that well,
        // so instead we just return a 403 or 404 error, depending on whether the folder exists
        File localFolder = new File(localPathStr);

        if (localFolder.exists()) {
            throw new HttpException(403, "Forbidden");
        }
        else {
            throw new HttpException(404, "Not Found");
        }
    }
}
