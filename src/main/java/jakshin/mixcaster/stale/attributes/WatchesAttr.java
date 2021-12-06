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

package jakshin.mixcaster.stale.attributes;

import jakshin.mixcaster.mixcloud.MusicSet;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import static jakshin.mixcaster.logging.Logging.ERROR;
import static jakshin.mixcaster.logging.Logging.logger;

/**
 * Represents the mixcaster.watches user-defined attribute, holding an array of music sets,
 * for a specific path, which is expected to be a music file.
 */
public class WatchesAttr extends BaseAttr {
    /**
     * Creates a new instance, for a specific file/directory.
     * @param path The path to the file or directory on which the attribute will be read/written.
     */
    public WatchesAttr(@NotNull Path path) {
        super(watchesAttributeName, path);
    }

    /**
     * Gets the attribute's current value.
     * Returns an empty list if the attribute doesn't exist.
     *
     * @throws IOException if an I/O error occurs reading the attribute
     * @throws UnsupportedOperationException if Java can't read/write user-defined attributes in this file system
     */
    @NotNull
    public List<MusicSet> getValue() throws IOException {
        var currentValue = new LinkedList<MusicSet>();

        if (! this.exists()) {
            return currentValue;  // return an empty list when the attribute doesn't exist
        }

        byte[] bytes = (byte[]) Files.getAttribute(path, "user:" + attributeName, LinkOption.NOFOLLOW_LINKS);
        String str = new String(bytes, StandardCharsets.UTF_8);
        String[] setStrings = str.split("\\|");

        for (String setStr : setStrings) {
            if (setStr.isBlank()) continue;

            try {
                String[] words = setStr.split("\\s+");
                MusicSet set = MusicSet.of(List.of(words));
                currentValue.add(set);
            }
            catch (MusicSet.InvalidInputException ex) {
                logger.log(ERROR, ex, () -> String.format("Ignoring invalid data in 'watches' attribute on %s", path));
            }
        }

        return currentValue;
    }

    /**
     * Sets the attribute's value.
     *
     * @throws IOException if an I/O error occurs writing the attribute
     * @throws UnsupportedOperationException if Java can't read/write user-defined attributes in this file system
     */
    public void setValue(@NotNull final List<MusicSet> newValue) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (MusicSet set : newValue) {
            if (sb.length() > 0) sb.append('|');
            sb.append(set.toString());
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        Files.setAttribute(path, "user:" + attributeName, bytes, LinkOption.NOFOLLOW_LINKS);
    }

    /** This attribute's name. */
    private static final String watchesAttributeName = "mixcaster.watches";
}
