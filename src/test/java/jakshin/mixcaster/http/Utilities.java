/*
 * Copyright (c) 2022 Jason Jackson
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

import jakshin.mixcaster.podcast.Podcast;
import jakshin.mixcaster.podcast.PodcastEpisode;
import jakshin.mixcaster.utils.DateFormatter;
import org.jetbrains.annotations.NotNull;

import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.fail;

/**
 * Utility functions for use in tests.
 */
public class Utilities {

    @NotNull
    public static Podcast createMockPodcast() {
        return createMockPodcast(3);
    }

    @NotNull
    public static Podcast createMockPodcast(int numEpisodes) {
        var podcast = new Podcast();

        try {
            podcast.userID = "VXNlcjozNjI3OTMy";
            podcast.title = "Mock Podcast";
            podcast.link = new URI("https://example.com/rss");
            podcast.description = "Mocked for testing";
            podcast.iTunesAuthorAndOwnerName = "Somebody";
            podcast.iTunesImageUrl = new URI("https://example.com/image.jpg");

            for (int num = 1; num <= numEpisodes; num++) {
                String musicFile = String.format("music-%d.m4a", num);

                var ep = new PodcastEpisode();
                ep.description = String.format("Mock description %d", num);
                ep.enclosureLastModified = new Date();
                ep.enclosureLengthBytes = 1234;
                ep.enclosureMimeType = "audio/mp4";
                ep.enclosureMixcloudUrl = new URI("https://example.com/" + musicFile);
                ep.enclosureUrl = new URI("http://localhost:6499/somebody/" + musicFile);
                ep.link = new URI("https://example.com/mock-music-" + num);
                ep.pubDate = new Date();
                ep.title = String.format("Mock Music %d", num);
                ep.iTunesAuthor = "Somebody";
                ep.iTunesDuration = 2345;
                ep.iTunesImageUrl = new URI("https://example.com/mock-music-image.jpg");

                podcast.episodes.add(ep);
            }
        }
        catch (URISyntaxException ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

        return podcast;
    }

    @NotNull
    public static Date parseDateHeader(String headerName, String headers) {
        Pattern p = Pattern.compile("\\r\\n" + headerName + ":\\s+[^\\r\\n]+GMT\\r\\n");
        Matcher m = p.matcher(headers);

        if (! m.find()) {
            fail(String.format("Missing the %s response header", headerName));
        }

        int index = m.group().indexOf(':');
        String headerValue = m.group().substring(index + 1).trim();

        try {
            return DateFormatter.parse(headerValue);
        }
        catch (ParseException ex) {
            fail(String.format("Invalid format in the %s response header: %s", headerName, headerValue));
            return new Date();  // we never get here, it's just to keep the compiler happy
        }
    }

    public static void resetStringWriter(@NotNull StringWriter writer) {
        writer.getBuffer().delete(0, writer.getBuffer().length());
    }
}
