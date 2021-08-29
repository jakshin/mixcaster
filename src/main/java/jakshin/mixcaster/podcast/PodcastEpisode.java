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

package jakshin.mixcaster.podcast;

import java.net.URI;
import java.util.Date;

/**
 * A single episode of a podcast.
 */
public class PodcastEpisode {
    /** A description of the episode (4000 chars max for Apple Podcasts). */
    public String description;

    /** The last-modified date of the episode's audio file. */
    public Date enclosureLastModified;

    /** The length in bytes of the episode's audio file. */
    public long enclosureLengthBytes;

    /** The MIME type of the episode's audio file. */
    public String enclosureMimeType;

    /** The Mixcloud URL the episode's audio file was downloaded from. */
    public URI enclosureMixcloudUrl;

    /** The local URL of the episode's audio file. */
    public URI enclosureUrl;

    /** The URL of the episode's web page on Mixcloud (not the URL of the audio file). */
    public URI link;

    /** The episode's publication date/time. */
    public Date pubDate;

    /** The episode's title. */
    public String title;

    /** The episode's author. */
    public String iTunesAuthor;

    /** The episode's duration, in seconds (optional). */
    public Integer iTunesDuration;

    /** The URL of the episode's image (optional). */
    public URI iTunesImageUrl;
}
