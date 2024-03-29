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

import org.jetbrains.annotations.NotNull;
import java.io.Serial;

/**
 * An exception raised directly by this application's code, related to HTTP processing.
 * Includes an appropriate HTTP response code.
 */
class HttpException extends Exception {
    /**
     * Constructs a new exception with the specified HTTP response code and detail message.
     *
     * @param httpResponseCode The HTTP response code.
     * @param message The detail message; saved for later retrieval by the Throwable.getMessage() method.
     */
    HttpException(int httpResponseCode, @NotNull String message) {
        super(message);
        this.httpResponseCode = httpResponseCode;
    }

    /**
     * Constructs a new exception with the specified HTTP response code, detail message, and cause.
     *
     * @param httpResponseCode The HTTP response code.
     * @param message The detail message; saved for later retrieval by the Throwable.getMessage() method.
     * @param cause The cause; saved for later retrieval by the Throwable.getCause() method.
     */
    HttpException(int httpResponseCode, @NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
        this.httpResponseCode = httpResponseCode;
    }

    /** HTTP response code. */
    final int httpResponseCode;

    /** Serialization version number. */
    @Serial
    private static final long serialVersionUID = 1L;  // update this whenever the class definition changes
}
