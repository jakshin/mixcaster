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

import jakshin.mixcaster.Main;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
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

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() throws URISyntaxException {
        this.instance = new Podcast();
        this.instance.userID = "foo";
        this.instance.link = new URI("http://example.com");
        this.instance.title = "Title";
        this.instance.description = "Podcast description";

        this.instance.iTunesAuthorAndOwnerName = "Author";
        this.instance.iTunesImageUrl = new URI("http://example.com/image.jpg");
    }

    @After
    public void tearDown() {
    }

    @Test
    public void createXmlShouldWorkWithEpisodes() throws IOException, URISyntaxException {
        PodcastEpisode ep = new PodcastEpisode();
        ep.description = "Episode description";
        ep.enclosureLastModified = new Date();
        ep.enclosureLengthBytes = 123456;
        ep.enclosureMimeType = "audio/mpeg";
        ep.enclosureMixcloudUrl = new URI("https://stream11.mixcloud.com/secure/c/m4a/episode1.m4a?sig=foobar");
        ep.enclosureUrl = new URI("http://example.com/episode1.m4a");
        ep.link = new URI("https://www.mixcloud.com/somebody/episode1.html");
        ep.pubDate = new Date();
        ep.title = "Episode 1";
        ep.iTunesAuthor = "Somebody";
        ep.iTunesDuration = 2345;
        ep.iTunesImageUrl = new URI("http://example.com/image.jpg");
        instance.episodes.add(ep);

        String result = instance.createXml();

        int itemIndex = result.indexOf("<item>");
        assertTrue(itemIndex > 0);

        // podcast tags should be present, before the first <item> tag
        String[] podcastTags = new String[] { "title", "link", "language", "description",
                "itunes:author", "itunes:explicit", "itunes:owner", "itunes:name", "itunes:email" };
        for (String tag : podcastTags) {
            int index = result.indexOf("<" + tag + ">");
            assertTrue(index != -1 && index < itemIndex);
        }

        String[] podcastTags2 = new String[] { "itunes:category", "itunes:image" };
        for (String tag : podcastTags2) {
            int index = result.indexOf("<" + tag + " ");
            assertTrue(index != -1 && index < itemIndex);
        }

        // episode tags should be present, after the first <item> tag
        String[] episodeTags = new String[] { "title", "link", "guid", "pubDate", "description",
                "itunes:author", "itunes:duration" };
        for (String tag : episodeTags) {
            int index = result.indexOf("<" + tag + ">", itemIndex);
            assertTrue(index != -1);
        }

        String[] episodeTags2 = new String[] { "enclosure", "itunes:image" };
        for (String tag : episodeTags2) {
            int index = result.indexOf("<" + tag + " ", itemIndex);
            assertTrue(index != -1);
        }

        // the feed should validate (requires a network connection)
        if (!this.validateFeed(result)) {
            fail("The generated RSS does not validate");
        }
    }

    @Test
    public void createXmlShouldWorkWithoutEpisodes() throws IOException {
        String result = instance.createXml();

        int itemIndex = result.indexOf("<item>");
        assertEquals(-1, itemIndex);

        // podcast tags should be present
        String[] podcastTags = new String[] { "title", "link", "language", "description",
                "itunes:author", "itunes:explicit", "itunes:owner", "itunes:name", "itunes:email" };
        for (String tag : podcastTags) {
            int index = result.indexOf("<" + tag + ">");
            assertTrue(index != -1);
        }

        String[] podcastTags2 = new String[] { "itunes:category", "itunes:image" };
        for (String tag : podcastTags2) {
            int index = result.indexOf("<" + tag + " ");
            assertTrue(index != -1);
        }

        // episode tags shouldn't be present; we don't check for the non-existence of tags
        // the podcast itself also has: title, link, description, itunes:author and itunes:image
        String[] episodeTags = new String[] { "guid", "pubDate", "itunes:duration" };
        for (String tag : episodeTags) {
            int index = result.indexOf("<" + tag + ">", itemIndex);
            assertEquals(-1, index);
        }

        int index = result.indexOf("<enclosure ", itemIndex);
        assertEquals(-1, index);

        // the feed should validate (requires a network connection)
        if (!this.validateFeed(result)) {
            fail("The generated RSS does not validate");
        }
    }

    /**
     * Utility method which validates a podcast RSS feed against http://validator.w3.org/feed/.
     *
     * @param rssXml The RSS XML to validate.
     * @return Whether the RSS XML validates or not.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
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
            try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                String encoded = URLEncoder.encode(rssXml, StandardCharsets.UTF_8);
                writer.write("rawdata=" + encoded + "&manual=1");
                writer.flush();
            }

            StringBuilder sb = new StringBuilder(100_000);

            in = conn.getInputStream();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                final char[] buf = new char[10_000];

                while (true) {
                    int charCount = reader.read(buf, 0, buf.length);
                    if (charCount < 0) break;

                    sb.append(buf, 0, charCount);
                }
            }

            return (sb.indexOf("<h2>Congratulations!</h2>") != -1);  // <h2>Sorry</h2> on validation failure
        }
        catch (IOException ex) {
            // ignore temporary problems with W3C's feed validation service
            return ex.getMessage().contains("HTTP response code: 503");
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
                // oh, well
            }

            try {
                if (out != null) {
                    out.close();
                }
            }
            catch (IOException ex) {
                // oh, well
            }
        }
    }
}
