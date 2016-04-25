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
import jakshin.mixcloudpodcast.utils.DateFormatter;
import java.io.IOException;
import java.io.Writer;
import java.text.ParseException;
import java.util.Date;

/**
 * Responds to an HTTP request for RSS XML.
 */
public class XmlResponder {
    /**
     * Responds to the RSS XML request.
     *
     * @param request The incoming HTTP request.
     * @param writer A writer which can be used to output the response.
     * @throws ApplicationException
     * @throws HttpException
     * @throws IOException
     */
    void respond(HttpRequest request, Writer writer) throws ApplicationException, HttpException, IOException {
        String feedName = this.getSecondToLastComponentOfUrl(request.url);
        if (feedName == null || feedName.isEmpty()) {
            throw new HttpException(403, "Forbidden");  // 404 would also be a fine choice
        }

        System.out.println("Feed Name: " + feedName);  // XXX logging; in cache class?

        // get a feed, either from cache or by scraping
        FeedCache cache = FeedCache.getInstance();
        MixcloudFeed feed = cache.getFromCache(feedName);

        if (feed == null) {
            // kick off a scraper
            String mixcloudFeedUrl = String.format("https://www.mixcloud.com/%s/", feedName);
            MixcloudScraper scraper = new MixcloudScraper();
            feed = scraper.scrape(mixcloudFeedUrl);

            // cache the MixcloudFeed instance
            cache.addToCache(feedName, feed);
        }

        // handle If-Modified-Since
        HttpHeaderWriter headerWriter = new HttpHeaderWriter();

        try {
            if (request.headers.containsKey("If-Modified-Since")) {
                Date ifModifiedSince = DateFormatter.parse(request.headers.get("If-Modified-Since"));
                Date scraped = new Date(feed.scraped.getTime() / 1000 * 1000);  // truncate milliseconds for comparison

                if (ifModifiedSince.compareTo(scraped) >= 0) {
                    headerWriter.sendNotModifiedHeaders(writer);
                    return;
                }
            }
        }
        catch (ParseException ex) {
            // XXX logging
            System.out.println(ex.getClass().getCanonicalName() + ": " + ex.getMessage());
            // continue without If-Modified-Since handling
        }

        // build the RSS XML
        PodcastRSS rss = feed.createRSS(request.host());
        String rssXml = rss.toString();

        // kick off any downloads from Mixcloud which are now needed
        DownloadQueue downloads = DownloadQueue.getInstance();
        int downloadCount = downloads.queueSize();

        // XXX logging
        if (downloadCount == 0) {
            String msg = feed.tracks.isEmpty() ? "No tracks to download" : "All tracks have already been downloaded";
            System.out.println(msg);
        }
        else {
            String tracksStr = (downloadCount == 1) ? "track" : "tracks";
            System.out.println(String.format("Starting download of %d %s", downloadCount, tracksStr));
            downloads.processQueue();
        }

        // send the response headers
        headerWriter.sendSuccessHeaders(writer, feed.scraped, "application/xml", rssXml.length());

        // send the RSS XML, if needed; note that we always send the whole thing,
        // as we don't expect to receive a Range header for this type of request
        if (!request.isHead()) {
            writer.write(rssXml);
            writer.flush();
        }
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
