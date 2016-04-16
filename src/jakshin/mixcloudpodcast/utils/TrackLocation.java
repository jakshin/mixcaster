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

/**
 * A thing which knows how to locate music tracks when they've been downloaded from Mixcloud.
 */
public class TrackLocation {
    /**
     * Creates a new instance of the class, with information known while scraping a Mixcloud feed page.
     *
     * @param feedUrl The Mixcloud feed's URL.
     * @param trackWebPageUrl The track's human-consumable URL.
     * @param trackRemoteUrl The music track's remote URL, as decoded from the Mixcloud feed.
     */
    public TrackLocation(String feedUrl, String trackWebPageUrl, String trackRemoteUrl) {
        int index = trackRemoteUrl.lastIndexOf('.');
        String extension = trackRemoteUrl.substring(index);

        String localDirName = this.getLastComponentOfUrl(feedUrl);
        String localFileName = this.getLastComponentOfUrl(trackWebPageUrl) + extension;
        this.localFilePath = localDirName + "/" + localFileName;
    }

    /**
     * Creates a new instance of the class, with information known while serving a local HTTP request.
     * @param localUrl The local URL for the track.
     */
    public TrackLocation(String localUrl) {
        int index = localUrl.indexOf("//");
        index = localUrl.indexOf('/', index + "//".length());
        this.localFilePath = localUrl.substring(index + 1);
    }

    /**
     * Gets the track's local URL.
     * The local HTTP hostname and port can be configured in the properties file.
     *
     * @return The track's local URL.
     */
    public String getLocalUrl() {
        return "http://" + TrackLocation.httpHostName + ":" + TrackLocation.httpPort + "/" + this.localFilePath;
    }

    /**
     * Gets the track's complete local path, starting with the music directory.
     * The music directory can be configured in the properties file.
     *
     * @return The track's local path.
     */
    public String getLocalPath() {
        return TrackLocation.localMusicDir + "/" + this.localFilePath;
    }

    /** The hostname from which HTTP requests are served. */
    private static String httpHostName;

    /** The port on which HTTP requests are served. */
    private static String httpPort;

    /** The local music directory, where downloaded music files are stored. */
    private static String localMusicDir;

    /**
     * The track's relative local path, underneath the music directory.
     * This contains a relative path based on the Mixcloud artist, and the track's file name.
     */
    private final String localFilePath;

    /**
     * Gets the last component of a URL.
     * For example: http://foo/bar/ => bar.
     *
     * @param url The URL.
     * @return The URL's last component.
     */
    private String getLastComponentOfUrl(String url) {
        String[] components = url.split("/");  // trailing empty string not included
        return components[components.length - 1];
    }

    /** Static initializer. */
    static {
        String musicDir = Main.config.getProperty("music_dir");
        if (musicDir.endsWith("/")) {
            musicDir = musicDir.substring(0, musicDir.length() - 1);
        }
        if (musicDir.startsWith("~/")) {
            musicDir = System.getProperty("user.home") + musicDir.substring(1);
        }

        TrackLocation.localMusicDir = musicDir;
        TrackLocation.httpHostName = Main.config.getProperty("http_hostname");
        TrackLocation.httpPort = Main.config.getProperty("http_port");
    }
}
