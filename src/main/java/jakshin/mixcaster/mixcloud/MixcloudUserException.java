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

/**
 * Thrown by Mixcaster code when a Mixcloud user doesn't exist.
 */
public class MixcloudUserException extends MixcloudException {
    /**
     * Constructs a new exception with the specified detail message and username.
     *
     * @param message The detail message.
     * @param username The Mixcloud user that doesn't exist.
     */
    public MixcloudUserException(@NotNull String message, @NotNull String username) {
        super(message);
        this.username = username;
    }

    /** The Mixcloud user that doesn't exist. */
    @NotNull
    public final String username;
}
