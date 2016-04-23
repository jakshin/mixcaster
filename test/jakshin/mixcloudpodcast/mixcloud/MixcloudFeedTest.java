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
import java.net.MalformedURLException;
import java.util.Date;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the MixcloudDecoder class.
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
    public void creatingRssShouldWork() throws MalformedURLException {
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

        PodcastRSS rss = feed.createRSS(null);
        assertEquals(feed.title, rss.title);
        assertEquals(feed.description, rss.description);
        assertEquals(feed.url, rss.link.toString());
        assertEquals(feed.locale, rss.language);
        assertEquals(feed.title, rss.iTunesAuthor);
        assertEquals(feed.imageUrl, rss.iTunesImageUrl);
        assertEquals(feed.title, rss.iTunesOwnerName);

        PodcastRSS.PodcastEpisode ep = rss.episodes.get(0);
        assertEquals(TrackLocator.getLocalUrl(null, feed.url, track.webPageUrl, track.musicUrl), ep.enclosureUrl.toString());
        assertEquals(track.musicContentType, ep.enclosureMimeType);
        assertEquals(track.musicLengthBytes, ep.enclosureLengthBytes);
        assertEquals(track.webPageUrl, ep.link.toString());
        assertEquals(track.musicLastModifiedDate, ep.pubDate);
        assertEquals(track.title + " [DOWNLOADING, CAN'T PLAY YET]", ep.title);
        assertEquals(track.ownerName, ep.iTunesAuthor);
        assertEquals(track.summary, ep.iTunesSummary);
    }
}
