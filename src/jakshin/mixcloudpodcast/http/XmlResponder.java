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

package jakshin.mixcloudpodcast.http;

import jakshin.mixcloudpodcast.ApplicationException;
import jakshin.mixcloudpodcast.download.DownloadQueue;
import jakshin.mixcloudpodcast.mixcloud.MixcloudFeed;
import jakshin.mixcloudpodcast.mixcloud.MixcloudScraper;
import jakshin.mixcloudpodcast.rss.PodcastRSS;
import java.io.IOException;
import java.io.Writer;

/**
 * Responds to an HTTP request for RSS XML.
 */
public class XmlResponder {
    /**
     * Responds to the RSS XML request.
     *
     * @param request The incoming HTTP request.
     * @param writer A writer which can be used to output the response.
     * @throws IOException
     */
    void respond(HttpRequest request, Writer writer) throws IOException, ApplicationException {
        String feedTitle = this.getSecondToLastComponentOfUrl(request.url);
        // TODO handle null/empty as 403
        System.out.println("Feed Title: " + feedTitle);  // TODO logging; in cache class?

        FeedCache cache = FeedCache.getInstance();
        MixcloudFeed feed = cache.getFromCache(feedTitle);

        if (feed == null) {
            // kick off a scraper
            String mixcloudFeedUrl = String.format("https://www.mixcloud.com/%s/", feedTitle);
            MixcloudScraper scraper = new MixcloudScraper();
            feed = scraper.scrape(mixcloudFeedUrl);

            // cache the MixcloudFeed instance
            cache.addToCache(feed);
        }

        PodcastRSS rss = feed.createRSS(request.host());
        String rssXml = rss.toString();

        DownloadQueue downloads = DownloadQueue.getInstance();
        int downloadCount = downloads.queueSize();

        // TODO logging
        if (downloadCount == 0) {
            String msg = feed.tracks.isEmpty() ? "No tracks to download" : "All tracks have already been downloaded";
            System.out.println(msg);
        }
        else {
            System.out.println(String.format("Starting download of %d tracks", downloadCount));
            downloads.processQueue();
        }

        // TODO write response headers via HttpResponse

        writer.write(rssXml);
        writer.flush();
    }

    /**
     * Gets the second-to-last component of a URL. For example: http://foo/bar/ => foo.
     *
     * @param url The URL.
     * @return The URL's second-to-last component, or null if it doesn't have one.
     */
    private String getSecondToLastComponentOfUrl(String url) {
        String[] components = url.split("/");  // trailing empty string not included
        if (components.length < 2) return null;
        return components[components.length - 2];
    }
}
