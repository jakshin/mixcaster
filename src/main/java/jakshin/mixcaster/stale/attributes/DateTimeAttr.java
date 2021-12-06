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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Represents a user-defined attribute that stores a date/time.
 */
class DateTimeAttr extends BaseAttr {
    /**
     * Creates a new instance, for a specific attribute on a specific file/directory.
     *
     * @param attributeName The attribute's name, like "mixcaster.foo".
     * @param path The path to the file or directory on which the attribute will be read/written.
     */
    DateTimeAttr(@NotNull String attributeName, @NotNull Path path) {
        super(attributeName, path);
    }

    /**
     * Gets the attribute's current value.
     * Throws an exception if the attribute doesn't exist, or can't be parsed.
     *
     * @throws DateTimeParseException if the attribute's value can't be parsed in RFC-1123 format
     * @throws DateTimeException if the attribute's parsed value can't be converted to OffsetDateTime
     * @throws IOException if an I/O error occurs reading the attribute
     * @throws UnsupportedOperationException if Java can't read/write user-defined attributes in this file system
     */
    @NotNull
    public OffsetDateTime getValue() throws IOException {
        byte[] bytes = (byte[]) Files.getAttribute(path, "user:" + attributeName, LinkOption.NOFOLLOW_LINKS);
        String str = new String(bytes, StandardCharsets.UTF_8);
        return OffsetDateTime.from(formatter.parse(str));
    }

    /**
     * Sets the attribute's value.
     *
     * @throws IOException if an I/O error occurs writing the attribute
     * @throws UnsupportedOperationException if Java can't read/write user-defined attributes in this file system
     */
    public void setValue(@NotNull OffsetDateTime newValue) throws IOException {
        String str = formatter.format(newValue);
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        Files.setAttribute(path, "user:" + attributeName, bytes, LinkOption.NOFOLLOW_LINKS);
    }

    /** Formats like: Sun, 5 Dec 2021 22:01:02 GMT */
    private static final DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
}
