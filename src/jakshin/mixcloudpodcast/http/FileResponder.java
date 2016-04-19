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

import java.io.IOException;
import java.io.OutputStream;

/**
 * Responds to an HTTP request for a file, or part of a file.
 */
public class FileResponder {
    /**
     * Responds to the file request.
     *
     * @param request The incoming HTTP request.
     * @param out An output stream which can be used to output the response.
     * @throws IOException
     */
    void respond(HttpRequest request, OutputStream out) throws IOException {
        /*
        TODO implement

        if file doesn't exist:
            return 404
        check byte range
        if range not valid:
            return 416
        read the appropriate part of the file & output in chunks
        flush
        */
    }
}
