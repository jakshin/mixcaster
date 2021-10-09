/*
 * Copyright (c) 2021 Jason Jackson
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

package jakshin.mixcaster.mixcloud;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;

/**
 * Thrown by Mixcaster code when something has gone wrong interfacing with Mixcloud.
 */
public class MixcloudException extends Exception {
    /**
     * Constructs a new exception with the specified detail message.
     * @param message The detail message.
     */
    MixcloudException(@NotNull String message) {
        super(message);
        this.url = null;
    }

    /**
     * Constructs a new exception with the specified detail message,
     * when the problem relates to a specific URL.
     *
     * @param message The detail message.
     * @param url The URL related to the problem (optional).
     */
    public MixcloudException(@NotNull String message, @Nullable String url) {
        super(message);
        this.url = url;
    }

    /** A URL related to the problem (optional). */
    @Nullable
    public final String url;

    /** Serialization version number.
        This should be updated whenever the class definition changes. */
    @Serial
    private static final long serialVersionUID = 1L;
}
