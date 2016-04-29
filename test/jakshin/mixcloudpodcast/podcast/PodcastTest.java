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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the Podcast class.
 */
public class PodcastTest {
    private Podcast instance = new Podcast();

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
        this.instance = new Podcast();
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
    public void createXmlShouldWorkWithEpisodes() throws MalformedURLException {
        instance.title = "Title";
        instance.description = "Description";
        instance.link = new URL("http://foo");
        instance.language = "en_US";
        instance.iTunesAuthor = "Author";
        instance.iTunesCategory = "Music";
        instance.iTunesImageUrl = "";

        PodcastEpisode ep = new PodcastEpisode();
        ep.enclosureUrl = new URL("http://foo/foo.mp3");
        ep.enclosureMimeType = "audio/mpeg";
        ep.enclosureLengthBytes = 123456;
        ep.link = new URL("http://foo/foo.html");
        ep.pubDate = new Date(1459917701000L);
        ep.title = "MP3";
        instance.episodes.add(ep);

        String result = instance.createXml();

        int itemIndex = result.indexOf("<item>");
        assertTrue(itemIndex > 0);

        String[] podcastTags = new String[] { "title", "description", "link", "language",
            "itunes:author", "itunes:explicit", "itunes:owner", "itunes:name", "itunes:email", "itunes:summary" };
        for (String tag : podcastTags) {
            int index = result.indexOf("<" + tag + ">");
            assertTrue(index != -1 && index < itemIndex);
        }

        String[] podcastTags2 = new String[] { "itunes:category", "itunes:image" };
        for (String tag : podcastTags2) {
            int index = result.indexOf("<" + tag + " ");
            assertTrue(index != -1 && index < itemIndex);
        }

        String[] episodeTags = new String[] { "guid", "link", "pubDate", "title", "itunes:author", "itunes:summary" };
        for (String tag : episodeTags) {
            int index = result.indexOf("<" + tag + ">", itemIndex);
            assertTrue(index != -1);
        }

        int index = result.indexOf("<enclosure ", itemIndex);
        assertTrue(index != -1);

        // TODO --- test against http://validator.w3.org/feed/#validate_by_input
    }

    /**
     * Test.
     * @throws MalformedURLException
     */
    @Test
    public void createXmlShouldWorkWithoutEpisodes() throws MalformedURLException {
        instance.title = "Title";
        instance.description = "Description";
        instance.link = new URL("http://foo");
        instance.language = "en_US";
        instance.iTunesAuthor = "Author";
        instance.iTunesCategory = "Music";
        instance.iTunesExplicit = true;
        instance.iTunesImageUrl = "";

        String result = instance.createXml();

        int itemIndex = result.indexOf("<item>");
        assertTrue(itemIndex == -1);

        String[] podcastTags = new String[] { "title", "description", "link", "language",
            "itunes:author", "itunes:explicit", "itunes:owner", "itunes:name", "itunes:email", "itunes:summary" };
        for (String tag : podcastTags) {
            int index = result.indexOf("<" + tag + ">");
            assertTrue(index != -1);
        }

        String[] podcastTags2 = new String[] { "itunes:category", "itunes:image" };
        for (String tag : podcastTags2) {
            int index = result.indexOf("<" + tag + " ");
            assertTrue(index != -1);
        }

        // title, link, itunes:author and itunes:summary also belong to the podcast itself,
        // so we don't check for their non-existence here
        String[] episodeTags = new String[] { "guid", "pubDate" };
        for (String tag : episodeTags) {
            int index = result.indexOf("<" + tag + ">", itemIndex);
            assertTrue(index == -1);
        }

        int index = result.indexOf("<enclosure ", itemIndex);
        assertTrue(index == -1);

        // TODO --- test against http://validator.w3.org/feed/#validate_by_input
    }
}
