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

import org.jetbrains.annotations.NotNull;

import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thingy which gets the MIME content-type of a file, based on its name.
 * It doesn't work very well in the general case, as it only knows about a few types of files,
 * but it should be fine for our limited purposes here.
 */
public class MimeTyper {
    /**
     * Guesses a files MIME content type, based on its name.
     *
     * @param pathOrName The path to or name of the file.
     * @return The file's probable MIME content type.
     */
    @NotNull
    public String guessContentTypeFromName(@NotNull String pathOrName) {
        // check our own extension => MIME type map first
        Path path = Paths.get(pathOrName);
        String fileName = path.getFileName().toString();

        int dot = fileName.lastIndexOf('.');
        String extension = (dot > 0) ? fileName.substring(dot) : "";

        String type = mimeTypes.get(extension);
        if (type != null && !type.isEmpty()) {
            return type;
        }

        // let URLConnection guess
        type = URLConnection.guessContentTypeFromName(fileName);
        if (type != null && !type.isEmpty()) {
            return type;
        }

        // fall back to a generic value
        return "application/octet-stream";
    }

    /** A map of known extensions to their MIME types. */
    private static final Map<String,String> mimeTypes = new ConcurrentHashMap<>(17, 1.0f);

    static {
        // common audio types
        mimeTypes.put(".aac", "audio/aac");
        mimeTypes.put(".m4a", "audio/mp4");
        mimeTypes.put(".m4b", "audio/mp4");
        mimeTypes.put(".m4p", "audio/mp4");
        mimeTypes.put(".m4r", "audio/mp4");
        mimeTypes.put(".mp3", "audio/mpeg");
        mimeTypes.put(".oga", "audio/ogg");
        mimeTypes.put(".ogg", "audio/ogg");
        mimeTypes.put(".wav", "audio/wav");
        mimeTypes.put(".m4v", "video/mp4");
        mimeTypes.put(".ogv", "video/ogg");

        // these could be audio or video; we assume audio
        mimeTypes.put(".mp4", "audio/mp4");
        mimeTypes.put(".webm", "audio/webm");

        // file types commonly used on the web, but unknown to URLConnection
        mimeTypes.put(".css", "text/css");
        mimeTypes.put(".ico", "image/x-icon");
        mimeTypes.put(".js", "application/javascript");
        mimeTypes.put(".svg", "image/svg+xml");
    }
}
