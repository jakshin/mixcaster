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

package jakshin.mixcaster.mixcloud;

import jakshin.mixcaster.podcast.Podcast;
import jakshin.mixcaster.podcast.PodcastEpisode;
import jakshin.mixcaster.utils.TrackLocator;
import java.net.MalformedURLException;
import java.util.Date;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the MixcloudFeed class.
 */
public class MixcloudFeedTest {
    /** Scaffolding. */
    @BeforeClass
    public static void setUpClass() {
    }

    /** Scaffolding. */
    @AfterClass
    public static void tearDownClass() {
    }

    /** Scaffolding. */
    @Before
    public void setUp() {
    }

    /** Scaffolding. */
    @After
    public void tearDown() {
    }

    /**
     * Test.
     * @throws MalformedURLException
     */
    @Test
    public void createPodcastShouldWorkWithEpisodes() throws MalformedURLException {
        MixcloudFeed feed = new MixcloudFeed();
        feed.url = "http://example.com/foo";
        feed.title = "Test";
        feed.imageUrl = "http://example.com/foo.jpg";
        feed.description = "Testing";
        feed.locale = "en_US";

        MixcloudFeed.Track track = new MixcloudFeed.Track();
        track.title = "Test Track";
        track.summary = "Testing Track";
        track.webPageUrl = "http://example.com/track1/";
        track.musicUrl = "http://example.com/track1.mp3";
        track.musicContentType = "audio/mpeg";
        track.musicLengthBytes = 42;
        track.musicLastModifiedDate = new Date();
        track.ownerName = "Owner";
        feed.tracks.add(track);

        Podcast podcast = feed.createPodcast(null);
        assertEquals(feed.title, podcast.title);
        assertEquals(feed.description, podcast.description);
        assertEquals(feed.url, podcast.link.toString());
        assertEquals(feed.locale, podcast.language);
        assertEquals(feed.title, podcast.iTunesAuthor);
        assertEquals(feed.imageUrl, podcast.iTunesImageUrl);
        assertEquals(feed.title, podcast.iTunesOwnerName);

        PodcastEpisode ep = podcast.episodes.get(0);
        assertEquals(TrackLocator.getLocalUrl(null, feed.url, track.webPageUrl, track.musicUrl), ep.enclosureUrl.toString());
        assertEquals(track.musicContentType, ep.enclosureMimeType);
        assertEquals(track.musicLengthBytes, ep.enclosureLengthBytes);
        assertEquals(track.webPageUrl, ep.link.toString());
        assertEquals(track.musicLastModifiedDate, ep.pubDate);
        assertEquals(track.title + " [DOWNLOADING, CAN'T PLAY YET]", ep.title);
        assertEquals(track.ownerName, ep.iTunesAuthor);
        assertEquals(track.summary, ep.iTunesSummary);
    }

    /**
     * Test.
     * @throws MalformedURLException
     */
    @Test
    public void createPodcastShouldWorkWithoutEpisodes() throws MalformedURLException {
        MixcloudFeed feed = new MixcloudFeed();
        feed.url = "http://example.com/foo";
        feed.title = "Test";
        feed.imageUrl = "http://example.com/foo.jpg";
        feed.description = "Testing";
        feed.locale = "en_US";

        Podcast podcast = feed.createPodcast(null);
        assertEquals(feed.title, podcast.title);
        assertEquals(feed.description, podcast.description);
        assertEquals(feed.url, podcast.link.toString());
        assertEquals(feed.locale, podcast.language);
        assertEquals(feed.title, podcast.iTunesAuthor);
        assertEquals(feed.imageUrl, podcast.iTunesImageUrl);
        assertEquals(feed.title, podcast.iTunesOwnerName);
        assertEquals(0, podcast.episodes.size());
    }
}
