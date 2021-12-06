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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.List;

/**
 * Base class for user-defined attributes.
 * This exists just to hold some shared code.
 */
class BaseAttr {
    /**
     * Reports whether user-defined attributes are supported here.
     * Can vary by file system as well as by Java implementation.
     */
    public boolean isSupported() throws IOException {
        return Files.getFileStore(path).supportsFileAttributeView("user");
    }

    /**
     * Reports whether the attribute already exists.
     *
     * @throws IOException if an I/O error occurs reading attribute names
     * @throws UnsupportedOperationException if Java can't read/write user-defined attributes in this file system
     */
    public boolean exists() throws IOException {
        UserDefinedFileAttributeView view = Files.getFileAttributeView(path,
                UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);

        if (view == null) {
            throw new UnsupportedOperationException("Can't get UserDefinedFileAttributeView for " + path);
        }

        List<String> attrNames = view.list();
        return attrNames.contains(attributeName);
    }

    /**
     * Creates a new instance, for a specific attribute on a specific file/directory.
     * This constructor is protected to prevent instantiation except by subclasses.
     *
     * @param attributeName The attribute's name, like "mixcaster.foo".
     * @param path The path to the file or directory on which the attribute will be read/written.
     */
    protected BaseAttr(@NotNull String attributeName, @NotNull Path path) {
        this.attributeName = attributeName;
        this.path = path;
    }

    /**
     * When Java reads/writes user-defined attributes, it annoyingly prepends "user.",
     * so from outside Java, our attributes are named like "user.mixcaster.foo".
     */
    protected final String attributeName;

    /**
     * The path to the file or directory on which the attribute will be read/written.
     */
    protected final Path path;
}
