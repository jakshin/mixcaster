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

import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;

/**
 * Represents the mixcaster.lastUsed user-defined attribute, for a specific path.
 */
public class LastUsedAttr extends DateTimeAttr {
    /**
     * Creates a new instance, for a specific file/directory.
     * @param path The path to the file or directory on which the attribute will be read/written.
     */
    public LastUsedAttr(@NotNull Path path) {
        super(lastUsedAttributeName, path);
    }

    /** This attribute's name. */
    private static final String lastUsedAttributeName = "mixcaster.lastUsed";
}
