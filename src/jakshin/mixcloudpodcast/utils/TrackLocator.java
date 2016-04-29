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

package jakshin.mixcloudpodcast.utils;

import jakshin.mixcloudpodcast.Main;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A thing which knows the locations of music tracks.
 */
public class TrackLocator {
    /**
     * Provides the local path to a track, given its local URL.
     * The local URL can be complete, with http://host:port,
     * or contain just the absolute path-and-filename URL to the track, starting with a slash.
     *
     * @param localUrl The track's local URL. Expected not to be URL-encoded.
     * @return The track's local path.
     */
    public static String getLocalPath(String localUrl) {
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
        return Paths.get(localMusicDir, normalized.toString().substring(1)).toString();
    }

    /**
     * Provides the local URL for a track, given info known about it from scraping Mixcloud.
     *
     * @param localHostAndPort The local host and HTTP port, or null/empty to use configured values.
     * @param feedUrl The Mixcloud artist/feed page's URL.
     * @param trackWebPageUrl The track's web page's URL, i.e. the human-readable Mixcloud page about it.
     * @param trackUrl The URL from which the track's music file can be downloaded.
     * @return
     */
    public static String getLocalUrl(String localHostAndPort, String feedUrl, String trackWebPageUrl, String trackUrl) {
        // some quick testing on Mixcloud shows that doesn't seem to ever create URLs which are URL-encoded or need to be
        if (localHostAndPort == null || localHostAndPort.isEmpty()) {
            localHostAndPort = Main.config.getProperty("http_hostname") + ":" + Main.config.getProperty("http_port");
        }

        String extension = trackUrl.substring(trackUrl.lastIndexOf('.'));   // includes the dot, e.g. ".m4a"
        String localDirName = TrackLocator.getLastComponentOfUrl(feedUrl);  // usually an artist name
        String localFileName = TrackLocator.getLastComponentOfUrl(trackWebPageUrl) + extension;

        return "http://" + localHostAndPort + "/" + localDirName + "/" + localFileName;
    }

    /**
     * Gets the last component of a URL. For example: http://foo/bar/ => bar.
     *
     * @param url The URL.
     * @return The URL's last component.
     */
    private static String getLastComponentOfUrl(String url) {
        String[] components = url.split("/");  // trailing empty string not included
        return components[components.length - 1];
    }

    /**
     * Private constructor to prevent instantiation.
     * This class's methods are all static, and it shouldn't be instantiated.
     */
    private TrackLocator() {
        // nothing here
    }
}
