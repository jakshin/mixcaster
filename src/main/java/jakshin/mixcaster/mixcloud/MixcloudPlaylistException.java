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
import java.io.Serial;

/**
 * Thrown by Mixcaster code when a Mixcloud playlist doesn't exist
 * (or was requested as belonging to the owning user).
 */
public class MixcloudPlaylistException extends MixcloudException {
    /**
     * Constructs a new exception with the specified detail message, username and playlist slug.
     *
     * @param message The detail message.
     * @param username The Mixcloud user that might not exist.
     * @param playlist The slug of the Mixcloud playlist that doesn't exist.
     */
    public MixcloudPlaylistException(@NotNull String message, @NotNull String username, @NotNull String playlist) {
        super(message);
        this.username = username;
        this.playlist = playlist;
    }

    /** The Mixcloud user that might not exist. */
    @NotNull
    public final String username;

    /** The slug of the Mixcloud playlist that doesn't exist. */
    @NotNull
    public final String playlist;

    /** Serialization version number.
     This should be updated whenever the class definition changes. */
    @Serial
    private static final long serialVersionUID = 1L;
}
