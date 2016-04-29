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

package jakshin.mixcloudpodcast.podcast;

import java.net.URL;
import java.util.Date;

/**
 * A single episode of a podcast.
 */
public class PodcastEpisode {
    /**
     * The URL of the episode's audio file.
     * Shouldn't require URL-encoding.
     */
    public URL enclosureUrl;

    /** The MIME type of the episode's audio file. */
    public String enclosureMimeType;

    /** The length in bytes of the episode's audio file. */
    public int enclosureLengthBytes;

    /**
     * The episode's web page's URL (for human consumption, not the URL of the audio file).
     * Shouldn't require URL-encoding.
     */
    public URL link;

    /** The episode's publication date/time. */
    public Date pubDate;

    /** The episode's title. */
    public String title;

    /** The episode's author. */
    public String iTunesAuthor;

    /** A summary of the episode. */
    public String iTunesSummary;
}
