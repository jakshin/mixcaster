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

import jakshin.mixcloudpodcast.Main;
import java.io.*;
import java.net.*;
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
    public void setUp() throws MalformedURLException {
        this.instance = new Podcast();
        this.instance.title = "Title";
        this.instance.description = "Description";
        this.instance.link = new URL("http://example.com");
        this.instance.language = "en_US";
        this.instance.iTunesAuthor = "Author";
        this.instance.iTunesCategory = "Music";
        this.instance.iTunesExplicit = true;
        this.instance.iTunesImageUrl = "http://example.com/image.jpg";
        this.instance.iTunesOwnerEmail = "nobody@example.com";
        this.instance.iTunesOwnerName = "Owner";
    }

    /** Scaffolding. */
    @After
    public void tearDown() {
    }

    /** Test. */
    @Test
    public void createXmlShouldWorkWithEpisodes() throws IOException, MalformedURLException {
        PodcastEpisode ep = new PodcastEpisode();
        ep.enclosureUrl = new URL("http://example.com/episode1.mp3");
        ep.enclosureMimeType = "audio/mpeg";
        ep.enclosureLengthBytes = 123456;
        ep.link = new URL("http://example.com/episode1.html");
        ep.pubDate = new Date();
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

        if (!this.validateFeed(result)) {
            fail("The generated RSS does not validate");
        }
    }

    /** Test. */
    @Test
    public void createXmlShouldWorkWithoutEpisodes() throws IOException {
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

        if (!this.validateFeed(result)) {
            fail("The generated RSS does not validate");
        }
    }

    /**
     * Utility method which validates a podcast RSS feed against http://validator.w3.org/feed/.
     * @param rssXml The RSS XML to validate.
     * @return Whether or not the RSS XML validates.
     * @throws IOException
     */
    private boolean validateFeed(String rssXml) throws IOException {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;

        try {
            URL url = new URL("http://validator.w3.org/feed/check.cgi");
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", Main.config.getProperty("user_agent"));
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            out = conn.getOutputStream();
            try (OutputStreamWriter writer = new OutputStreamWriter(out, "utf-8")) {
                String encoded = URLEncoder.encode(rssXml, "utf-8");
                writer.write("rawdata=" + encoded + "&manual=1");
                writer.flush();
            }

            StringBuilder sb = new StringBuilder(100_000);

            in = conn.getInputStream();
            try (Reader reader = new InputStreamReader(in, "utf-8")) {
                final char[] buf = new char[10_000];

                while (true) {
                    int charCount = reader.read(buf, 0, buf.length);
                    if (charCount < 0) break;

                    sb.append(buf, 0, charCount);
                }
            }

            return (sb.indexOf("<h2>Congratulations!</h2>") != -1);  // <h2>Sorry</h2> on validation failure
        }
        finally {
            if (conn != null) {
                conn.disconnect();
            }

            try {
                if (in != null) {
                    in.close();
                }
            }
            catch (IOException ex) {
                // oh well
            }

            try {
                if (out != null) {
                    out.close();
                }
            }
            catch (IOException ex) {
                // oh well
            }
        }
    }
}
