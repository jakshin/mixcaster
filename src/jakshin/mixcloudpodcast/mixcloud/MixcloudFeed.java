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

package jakshin.mixcloudpodcast.mixcloud;

import jakshin.mixcloudpodcast.rss.PodcastRSS;
import jakshin.mixcloudpodcast.utils.TrackLocation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * A representation of the data associated with a Mixcloud artist feed.
 * Includes some info about the artist/feed itself as well as a collection of tracks.
 */
public class MixcloudFeed {
    /**
     * Creates a podcast RSS object from the feed.
     * The Mixcloud-style data in the feed is mapped to podcast-style data for use in RSS.
     *
     * @return A PodcastRSS object containing data equivalent to the feed's.
     * @throws MalformedURLException
     */
    public PodcastRSS createRSS() throws MalformedURLException {
        PodcastRSS rss = new PodcastRSS();

        rss.title = this.title;
        rss.description = this.description;
        rss.link = new URL(this.url);
        rss.language = this.locale;

        rss.iTunesAuthor = this.title;
        rss.iTunesCategory = "Music";
        rss.iTunesExplicit = false;
        rss.iTunesImage = this.imageUrl;
        rss.iTunesOwnerName = this.title;
        rss.iTunesOwnerEmail = "nobody@example.com";

        // represent each stream/track as a podcast episode
        for (Track track : this.tracks) {
            PodcastRSS.PodcastEpisode episode = new PodcastRSS.PodcastEpisode();

            episode.enclosureUrl = new URL(track.location.getLocalUrl());
            episode.enclosureMimeType = track.musicContentType;
            episode.enclosureLengthBytes = track.musicLengthBytes;
            episode.link = new URL(track.webPageUrl);
            episode.pubDate = track.musicLastModifiedDate;
            episode.title = track.title;
            episode.iTunesAuthor = track.ownerName;
            episode.iTunesSummary = track.summary;

            rss.episodes.add(episode);
        }

        return rss;
    }

    /** The feed's URL for human consumption. */
    public String url;

    /** The feed's title; usually an artist or DJ's name. From the "og:title" meta tag. */
    public String title;

    /** The URL for the feed's image. From the "og:image" meta tag. */
    public String imageUrl;

    /** A description of the feed. From the "og:description" meta tag. */
    public String description;

    /** The feed's locale. From the "og:locale" meta tag. */
    public String locale;

    /** Tracks in the feed. */
    public final List<Track> tracks = new LinkedList<>();

    /**
     * A music track in the feed.
     */
    public static class Track {
        /** The track's title. From the "m-title" attribute. */
        public String title;

        /** The track's summary. From the track's detail page's "og:description" meta tag. */
        public String summary;

        /** The URL to visit in a web browser to read about the track, listen to it, etc. From the "m-url" attribute. */
        public String webPageUrl;

        /** The URL from which to download the music as an mp3, m4a, etc. From the "m-play-info" attribute. */
        public String musicUrl;

        /** The MIME content type of the music file at musicUrl. From its Content-Type header. */
        public String musicContentType;

        /** The length in bytes of the music file at musicUrl. From its Content-Length header. */
        public int musicLengthBytes;

        /** The date/time at which the music file at musicUrl was last modified. From its Last-Modified header. */
        public Date musicLastModifiedDate;

        /** The track's owner's name. From the "m-owner-name" attribute. */
        public String ownerName;

        /**
         * Provides information on how the music file can be accessed,
         * after it has been downloaded from Mixcloud and stored locally.
         */
        public TrackLocation location;
    }
}
