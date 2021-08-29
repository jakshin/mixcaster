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

package jakshin.mixcaster.utils;

import jakshin.mixcaster.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A thing which knows the locations of files, especially music files.
 */
public class FileLocator {
    /**
     * Provides the local path to a music file, given its local URL.
     * The local URL can be complete, like http://host:port/...,
     * or contain just the absolute path-and-filename URL to the file, starting with a slash.
     *
     * @param localUrl The music file's local URL. Expected not to be URL-encoded, nor need to be.
     * @return The music file's local path.
     */
    @NotNull
    public static String getLocalPath(@NotNull String localUrl) {
        int index = localUrl.indexOf("//");
        if (index != -1) {
            // strip http://host:port from the front
            index = localUrl.indexOf('/', index + "//".length());
            localUrl = localUrl.substring(index + 1);
        }

        String localMusicDir = Main.config.getProperty("music_dir");
        if (localMusicDir.startsWith("~/")) {
            localMusicDir = System.getProperty("user.home") + localMusicDir.substring(1);
        }

        // for security, we ensure that localUrl starts with a slash,
        // and normalize it to remove ".." entries, before combining it with localMusicDir
        if (!localUrl.isEmpty() && localUrl.charAt(0) != '/') localUrl = "/" + localUrl;
        Path normalized = Paths.get(localUrl).normalize();
        return Paths.get(localMusicDir, normalized.toString()).toString();
    }

    /**
     * Provides the local URL for a music file, given some Mixcloud info about it.
     *
     * @param localHostAndPort The local HTTP server's host:port, or null/empty to use configured values.
     * @param mixcloudUsername The username of the Mixcloud user who owns the music.
     * @param slug The last path component of Mixcloud's web URL about the music (which their GraphQL API calls "slug").
     * @param mixcloudUrl The URL to the music file on Mixcloud's servers.
     * @return The music file's complete local URL.
     */
    @NotNull
    public static String makeLocalUrl(@Nullable String localHostAndPort, @NotNull String mixcloudUsername,
                                      @NotNull String slug, @NotNull String mixcloudUrl) {

        if (localHostAndPort == null || localHostAndPort.isEmpty()) {
            localHostAndPort = Main.config.getProperty("http_hostname") + ":" + Main.config.getProperty("http_port");
        }

        int pos = mixcloudUrl.indexOf("?");
        if (pos != -1) {
            // found a query string, strip it
            mixcloudUrl = mixcloudUrl.substring(0, pos);
        }

        String extension = mixcloudUrl.substring(mixcloudUrl.lastIndexOf('.'));  // includes the dot, e.g. ".m4a"
        return "http://" + localHostAndPort + "/" + mixcloudUsername + "/" + slug + extension;
    }

    /**
     * Private constructor to prevent instantiation.
     * This class's methods are all static, and it shouldn't be instantiated.
     */
    private FileLocator() {
        // nothing here
    }
}
