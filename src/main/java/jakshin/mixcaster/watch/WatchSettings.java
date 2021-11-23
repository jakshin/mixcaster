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

package jakshin.mixcaster.watch;

import jakshin.mixcaster.mixcloud.MusicSet;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static jakshin.mixcaster.logging.Logging.*;

/**
 * Watches, from the relevant settings file.
 */
class WatchSettings {
    /**
     * Creates a new instance.
     * @param confFilePath Path to the settings file containing watches (mixcaster-watches.conf).
     */
    WatchSettings(@NotNull Path confFilePath) {
        this.confFilePath = confFilePath;
    }

    /**
     * Returns the watch settings, after loading them from disk if needed.
     * Returns an empty list if the settings file doesn't exist.
     */
    @NotNull
    List<MusicSet> loadIfChanged() throws IOException {
        logger.log(INFO, "Checking watch settings: {0}", confFilePath);
        String confFileName = confFilePath.getFileName().toString();

        if (! Files.isRegularFile(confFilePath)) {
            logger.log(INFO, "File \"{0}\" doesn''t exist", confFileName);
            lastModified = null;
            musicSets = List.of();
            return musicSets;
        }

        FileTime newLastModified = Files.getLastModifiedTime(confFilePath);  // exception if file doesn't exist
        if (newLastModified.equals(lastModified)) {
            logger.log(INFO, "File \"{0}\" hasn''t changed", confFileName);
            return musicSets;
        }

        // read each line in the file as a music set, like in a "-download" command line
        var newMusicSets = new LinkedList<MusicSet>();
        List<String> lines = Files.readAllLines(confFilePath);

        for (int lineNum = 0; lineNum < lines.size(); lineNum++) {
            String line = lines.get(lineNum).trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;  // blank line or comment
            }

            try {
                String[] words = line.split("\\s+");
                List<String> wordList = Arrays.stream(words).filter(s -> !s.startsWith("-")).toList();
                newMusicSets.add(MusicSet.of(wordList));
            }
            catch (MusicSet.InvalidInputException ex) {
                int realLineNum = lineNum + 1;  // 1-based for human consumption
                logger.log(ERROR, ex, () -> String.format("Skipping invalid watch setting on line %d", realLineNum));
            }
        }

        // change our state only if we don't throw an exception
        lastModified = newLastModified;
        musicSets = newMusicSets;

        if (musicSets.size() == 1) {
            logger.log(INFO, "Found 1 user/playlist to watch in {0}", confFileName);
        }
        else {
            logger.log(INFO, "Found {0} users/playlists to watch in {1}",
                    new Object[] { musicSets.size(), confFileName });
        }

        return musicSets;
    }

    /** The path to the *.conf file that contains watch settings. */
    private final Path confFilePath;

    /**
     * When the *.conf file was last modified.
     * Null if the file doesn't exist, or we haven't tried to load it yet.
     */
    private FileTime lastModified;

    /**
     * The music sets that were most recently loaded from the *.conf file.
     * Null only if we haven't tried to load the file yet; empty if it doesn't exist,
     * or it consists solely of comment lines and/or invalid lines, etc.
     */
    private List<MusicSet> musicSets;
}
