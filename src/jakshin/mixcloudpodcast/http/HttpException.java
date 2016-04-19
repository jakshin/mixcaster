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

/**
 * An exception raised directly by this application's code, related to HTTP processing.
 * Includes an appropriate HTTP response code.
 */
class HttpException extends Exception {
    /**
     * Constructs a new exception with the specified detail message.
     * @param message The detail message; saved for later retrieval by the Throwable.getMessage() method.
     */
    HttpException(int httpResponseCode, String message) {
        super(message);
        this.httpResponseCode = httpResponseCode;
    }

    /** HTTP response code. */
    final int httpResponseCode;

    /** Serialization version number. */
    private static final long serialVersionUID = 1L;  // update this whenever the class definition changes
}
