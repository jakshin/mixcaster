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

package jakshin.mixcloudpodcast.rss;

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
 * Unit tests for the PodcastRSS class.
 */
public class PodcastRSSTest {
    private PodcastRSS instance = new PodcastRSS();

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
        this.instance = new PodcastRSS();
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
    public void toStringShouldWorkWithEpisodes() throws MalformedURLException {
        String expResult = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<rss xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\" version=\"2.0\">\n" +
            "<channel>\n" +
            "\n" +
            "<title>Title</title>\n" +
            "<description>Description</description>\n" +
            "<link>http://foo</link>\n" +
            "<language>en-US</language>\n" +
            "\n" +
            "<itunes:author>Author</itunes:author> <!-- feed title -->\n" +
            "<itunes:category text=\"Music\" />\n" +
            "<itunes:explicit>no</itunes:explicit>\n" +
            "<itunes:image href=\"\" />\n" +
            "<itunes:owner>\n" +
            "  <itunes:name>{{podcast.iTunesOwnerName}}</itunes:name> <!-- feed title -->\n" +
            "  <itunes:email>{{podcast.iTunesOwnerEmail}}</itunes:email>\n" +
            "</itunes:owner>\n" +
            "<itunes:summary>Description</itunes:summary>\n" +
            "\n" +
            "<item>\n" +
            "  <enclosure url=\"http://foo/foo.mp3\" length=\"123456\" type=\"audio/mpeg\" />\n" +
            "  <guid>http://foo/foo.mp3</guid>\n" +
            "  <link>http://foo/foo.html</link>\n" +
            "  <pubDate>Wed, 6 Apr 2016 04:41:41 GMT</pubDate>\n" +
            "  <title>MP3</title>\n" +
            "  <itunes:author>{{episode.iTunesAuthor}}</itunes:author>\n" +
            "  <itunes:summary>{{episode.iTunesSummary}}</itunes:summary>\n" +
            "</item>\n" +
            "\n" +
            "</channel>\n" +
            "</rss>\n";

        instance.title = "Title";
        instance.description = "Description";
        instance.link = new URL("http://foo");
        instance.language = "en_US";
        instance.iTunesAuthor = "Author";
        instance.iTunesCategory = "Music";
        instance.iTunesImageUrl = "";

        PodcastRSS.PodcastEpisode ep = new PodcastRSS.PodcastEpisode();
        ep.enclosureUrl = new URL("http://foo/foo.mp3");
        ep.enclosureMimeType = "audio/mpeg";
        ep.enclosureLengthBytes = 123456;
        ep.link = new URL("http://foo/foo.html");
        ep.pubDate = new Date(1459917701000L);
        ep.title = "MP3";
        instance.episodes.add(ep);

        String result = instance.toString();
        assertEquals(expResult, result);
    }

    /**
     * Test.
     * @throws MalformedURLException
     */
    @Test
    public void toStringShouldWorkWithoutEpisodes() throws MalformedURLException {
        String expResult = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<rss xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\" version=\"2.0\">\n" +
            "<channel>\n" +
            "\n" +
            "<title>Title</title>\n" +
            "<description>Description</description>\n" +
            "<link>http://foo</link>\n" +
            "<language>en-US</language>\n" +
            "\n" +
            "<itunes:author>Author</itunes:author> <!-- feed title -->\n" +
            "<itunes:category text=\"Music\" />\n" +
            "<itunes:explicit>no</itunes:explicit>\n" +
            "<itunes:image href=\"\" />\n" +
            "<itunes:owner>\n" +
            "  <itunes:name>{{podcast.iTunesOwnerName}}</itunes:name> <!-- feed title -->\n" +
            "  <itunes:email>{{podcast.iTunesOwnerEmail}}</itunes:email>\n" +
            "</itunes:owner>\n" +
            "<itunes:summary>Description</itunes:summary>\n\n\n\n" +
            "</channel>\n" +
            "</rss>\n";

        instance.title = "Title";
        instance.description = "Description";
        instance.link = new URL("http://foo");
        instance.language = "en_US";
        instance.iTunesAuthor = "Author";
        instance.iTunesCategory = "Music";
        instance.iTunesImageUrl = "";

        String result = instance.toString();
        assertEquals(expResult, result);

        String expResult2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<rss xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\" version=\"2.0\">\n" +
            "<channel>\n" +
            "\n" +
            "<title>Title</title>\n" +
            "<description>Description</description>\n" +
            "<link>http://foo</link>\n" +
            "<language>en-US</language>\n" +
            "\n" +
            "<itunes:author>Author</itunes:author> <!-- feed title -->\n" +
            "<itunes:category text=\"Music\" />\n" +
            "<itunes:explicit>yes</itunes:explicit>\n" +
            "<itunes:image href=\"\" />\n" +
            "<itunes:owner>\n" +
            "  <itunes:name>{{podcast.iTunesOwnerName}}</itunes:name> <!-- feed title -->\n" +
            "  <itunes:email>{{podcast.iTunesOwnerEmail}}</itunes:email>\n" +
            "</itunes:owner>\n" +
            "<itunes:summary>Description</itunes:summary>\n\n\n\n" +
            "</channel>\n" +
            "</rss>\n";

        instance.link = new URL("http://foo");
        instance.language = "en_US";
        instance.iTunesExplicit = true;

        String result2 = instance.toString();
        assertEquals(expResult2, result2);
    }
}
