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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.util.List;
import java.util.Locale;

/**
 * A "set" of music files, defined by three bits of info: a Mixcloud user,
 * a music type (stream, uploads, etc.), and playlist slug if applicable.
 *
 * @param username  A Mixcloud user's username. If this is empty, the music set isn't valid.
 * @param musicType A music type (stream, shows, favorites, history, or playlist).
 *                  If this is empty, MixcloudClient's queryDefaultView() should be used.
 * @param playlist  A playlist's slug (the last element of its Mixcloud URL).
 *                  This should empty unless musicType is "playlist", in which case it's required.
 */
public record MusicSet(@NotNull String username, @Nullable String musicType, @Nullable String playlist) {
    /**
     * Creates a new instance.
     */
    public MusicSet {
        if (username.endsWith("'s") || username.endsWith("’s") || username.endsWith("‘s")) {
            // un-possessive the username
            username = username.substring(0, username.length() - 2);
        }

        if (username.isBlank()) {
            throw new InvalidInputException("Empty Mixcloud username");
        }

        if (musicType != null) {
            musicType = switch (musicType) { //NOPMD - suppressed UseStringBufferForStringAppends - WTF PMD?
                case "stream", "shows", "favorites", "history", "playlist" -> musicType;
                case "uploads" -> "shows";
                case "listens" -> "history";
                case "playlists" -> "playlist";
                default -> throw new InvalidInputException("Invalid music type: " + musicType);
            };
        }

        if ("playlist".equals(musicType)) {
            if (playlist == null || playlist.isBlank())
                throw new InvalidInputException("Missing a playlist slug");
        }
        else if (playlist != null) {
            String msg = String.format("Extra argument with music type '%s': %s", musicType, playlist);
            throw new InvalidInputException(msg);
        }
    }

    /**
     * Creates a new instance.
     * @param input Input that defines the music set (username, music type, playlist).
     */
    @Contract("_ -> new")
    @NotNull
    public static MusicSet of(@NotNull final List<String> input) throws InvalidInputException {
        if (input.isEmpty() || input.size() > 3) {
            throw new InvalidInputException("Wrong number of arguments: " + input.size());
        }

        String username = input.get(0);
        String musicType = (input.size() > 1) ? input.get(1).toLowerCase(Locale.ROOT) : null;
        String playlist = (input.size() > 2) ? input.get(2) : null;

        return new MusicSet(username, musicType, playlist);
    }

    /**
     * Oh noes, we ran into a problem constructing a music set.
     */
    public static class InvalidInputException extends RuntimeException {
        /**
         * Constructs a new exception with the specified detail message.
         * @param message The detail message (which is saved for later retrieval by the Throwable.getMessage() method).
         */
        InvalidInputException(String message) {
            super(message);
        }

        /** Serialization version number.
            This should be updated whenever the class definition changes. */
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
