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
import jakshin.mixcloudpodcast.utils.TrackLocator;
import java.io.File;
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
     * @param localHostAndPort The local host and port from which tracks will be served (optional).
     * @return A PodcastRSS object containing data equivalent to the feed's.
     * @throws MalformedURLException
     */
    public PodcastRSS createRSS(String localHostAndPort) throws MalformedURLException {
        PodcastRSS rss = new PodcastRSS();

        rss.title = this.title;
        rss.description = this.description;
        rss.link = new URL(this.url);
        rss.language = this.locale;

        rss.iTunesAuthor = this.title;
        rss.iTunesCategory = "Music";
        rss.iTunesExplicit = false;
        rss.iTunesImageUrl = this.imageUrl;
        rss.iTunesOwnerName = this.title;
        rss.iTunesOwnerEmail = "nobody@example.com";

        // represent each stream/track as a podcast episode
        for (Track track : this.tracks) {
            PodcastRSS.PodcastEpisode episode = new PodcastRSS.PodcastEpisode();

            String localUrlStr = TrackLocator.getLocalUrl(localHostAndPort, this.url, track.webPageUrl, track.musicUrl);
            String localPath = TrackLocator.getLocalPath(localUrlStr);

            String trackTitle = track.title;
            if (!(new File(localPath).exists())) {
                trackTitle += " [DOWNLOADING, CAN'T PLAY YET]";
            }

            episode.enclosureUrl = new URL(localUrlStr);
            episode.enclosureMimeType = track.musicContentType;
            episode.enclosureLengthBytes = track.musicLengthBytes;
            episode.link = new URL(track.webPageUrl);
            episode.pubDate = track.musicLastModifiedDate;
            episode.title = trackTitle;
            episode.iTunesAuthor = track.ownerName;
            episode.iTunesSummary = track.summary;

            rss.episodes.add(episode);
        }

        return rss;
    }

    /** The feed's URL for human consumption. Assumed not to be or need to be URL-encoded. */
    public String url;

    /** The feed's title; usually an artist or DJ's name. From the "og:title" meta tag. */
    public String title;

    /** The URL for the feed's image. From the "og:image" meta tag. Doesn't need URL-encoding. */
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

        /**
         * The URL to visit in a web browser to read about the track, listen to it, etc.
         * From the "m-url" attribute. Doesn't need URL-encoding.
         */
        public String webPageUrl;

        /**
         * The URL from which to download the music as an mp3, m4a, etc.
         * From the "m-play-info" attribute. Doesn't need URL-encoding.
         */
        public String musicUrl;

        /** The MIME content type of the music file at musicUrl. From its Content-Type header. */
        public String musicContentType;

        /** The length in bytes of the music file at musicUrl. From its Content-Length header. */
        public int musicLengthBytes;

        /** The date/time at which the music file at musicUrl was last modified. From its Last-Modified header. */
        public Date musicLastModifiedDate;

        /** The track's owner's name. From the "m-owner-name" attribute. */
        public String ownerName;
    }
}
