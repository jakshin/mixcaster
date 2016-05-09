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

package jakshin.mixcaster;

/**
 * An exception raised directly by this application's code.
 */
public class ApplicationException extends Exception {
    /**
     * Constructs a new exception with the specified detail message.
     * @param message The detail message; saved for later retrieval by the Throwable.getMessage() method.
     */
    public ApplicationException(String message) {
        super(message);
        this.additionalInfo = null;
    }

    /**
     * Constructs a new exception with the specified detail message and additional information.
     *
     * @param message The detail message; saved for later retrieval by the Throwable.getMessage() method.
     * @param additionalInfo Additional info which may be helpful if logged, etc.
     */
    public ApplicationException(String message, String additionalInfo) {
        super(message);
        this.additionalInfo = additionalInfo;
    }

    /** Additional information about the exception. May be null. */
    public final String additionalInfo;

    /** Serialization version number. */
    private static final long serialVersionUID = 2L;  // update this whenever the class definition changes
}
