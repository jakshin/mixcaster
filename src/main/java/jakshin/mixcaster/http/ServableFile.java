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

package jakshin.mixcaster.http;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serial;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A file that can be served by our HTTP server,
 * i.e. which is located under our music_dir.
 *
 * Use the inherited getPath() method to get its full path.
 */
public class ServableFile extends File {
    /**
     * Creates a new instance.
     * @param localUrl The file's local URL.
     */
    public ServableFile(@NotNull String localUrl) {
        super(getLocalPath(localUrl));
        this.localUrl = localUrl;
    }

    /**
     * Provides the local path to a file, given its local URL.
     * The local URL can be complete, like http://host:port/...,
     * or contain just the absolute path-and-filename URL to the file,
     * starting with a slash.
     *
     * @param localUrl The file's local URL.
     * @return The file's local path.
     */
    @NotNull
    public static String getLocalPath(@NotNull String localUrl) {
        int index = localUrl.indexOf("//");
        if (index != -1) {
            // strip http://host:port from the front
            index = localUrl.indexOf('/', index + "//".length());
            localUrl = localUrl.substring(index + 1);
        }

        String musicDir = System.getProperty("music_dir");
        if (musicDir.startsWith("~/")) {
            musicDir = System.getProperty("user.home") + musicDir.substring(1);
        }

        // for security, we ensure that localUrl starts with a slash,
        // and normalize it to remove ".." entries, before combining it with musicDir
        if (!localUrl.isEmpty() && localUrl.charAt(0) != '/') localUrl = "/" + localUrl;
        Path normalized = Paths.get(localUrl).normalize();
        return Paths.get(musicDir, normalized.toString()).toString();
    }

    /** The file's local URL. */
    public final String localUrl;

    /** Serialization version number.
        This should be updated whenever the class definition changes. */
    @Serial
    private static final long serialVersionUID = 1L;
}
