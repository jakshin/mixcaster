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

import jakshin.mixcaster.*;
import jakshin.mixcaster.download.Download;
import jakshin.mixcaster.download.DownloadQueue;
import jakshin.mixcaster.entities.HtmlEntities;
import jakshin.mixcaster.utils.TimeSpanFormatter;
import jakshin.mixcaster.utils.TrackLocator;
import java.io.*;
import java.net.*;
import java.util.Date;
import java.util.Locale;
import java.util.regex.*;
import java.util.zip.*;
import static jakshin.mixcaster.logging.Logging.*;

/**
 * Scrapes info from Mixcloud artist and track pages.
 */
public class MixcloudScraper {
    /**
     * Scrapes the given Mixcloud feed URL.
     * All tracks found are queued for download if they don't already exist locally,
     * but the download queue is not processed.
     *
     * @param urlStr The Mixcloud feed URL to scrape, e.g. "https://www.mixcloud.com/DJoseSolis/".
     *      Valid Mixcloud feed URLs should not be or need to be URL-encoded.
     * @return An object containing info about the feed.
     * @throws ApplicationException
     * @throws IOException
     * @throws MalformedURLException
     */
    public MixcloudFeed scrape(String urlStr) throws ApplicationException, IOException, MalformedURLException {
        logger.log(INFO, "Scraping {0} ...", urlStr);
        long started = System.currentTimeMillis();

        // download the feed's web page
        StringBuilder sb = this.downloadWebPage(urlStr);

        // extract podcast-wide properties
        MixcloudFeed feed = new MixcloudFeed();
        feed.scraped = new Date();
        feed.url = urlStr;
        feed.title = this.getMetaTagContent("og:title", sb, urlStr);
        feed.imageUrl = this.getMetaTagContent("og:image", sb, urlStr);
        feed.description = this.getMetaTagContent("og:description", sb, urlStr);
        feed.locale = this.getMetaTagContent("og:locale", sb, urlStr);

        // extract all tracks, i.e. podcast episodes;
        // Mixcloud lists tracks most-recent-first, so that's the order in which they'll be added to the feed
        int trackCount = 0;
        Pattern re = Pattern.compile(Main.config.getProperty("track_regex"));
        Matcher matcher = re.matcher(sb);

        while (matcher.find()) {
            String tag = matcher.group();

            String mPlayInfo = this.getAttributeValue("m-play-info", tag, urlStr);
            String musicUrl = this.decoder.decode(mPlayInfo);

            if (musicUrl == null) {
                String msg = String.format("Unable to decode m-play-info from %s", urlStr);
                throw new ApplicationException(msg, mPlayInfo);
            }

            String trackWebPageUrl = this.makeUrlAbsolute(this.getAttributeValue("m-url", tag, urlStr), urlStr);
            MusicUrlHeaderData data = this.getMusicUrlHeaderData(musicUrl);

            MixcloudFeed.Track track = new MixcloudFeed.Track();
            track.title = this.getAttributeValue("m-title", tag, urlStr);
            track.summary = this.scrapeTrackSummary(trackWebPageUrl);
            track.webPageUrl = trackWebPageUrl;
            track.musicUrl = musicUrl;
            track.musicContentType = data.contentType;
            track.musicLengthBytes = data.contentLengthBytes;
            track.musicLastModifiedDate = data.lastModifiedDate;
            track.ownerName = this.getAttributeValue("m-owner-name", tag, urlStr);

            String localUrl = TrackLocator.getLocalUrl(null, feed.url, track.webPageUrl, track.musicUrl);
            String localPath = TrackLocator.getLocalPath(localUrl);

            Download download = new Download(track.musicUrl, track.musicLengthBytes, track.musicLastModifiedDate, localPath);
            DownloadQueue.getInstance().enqueue(download);

            feed.tracks.add(track);
            trackCount++;
        }

        // log, with time taken (we don't try to handle clock changes or DST entry/exit)
        long finished = System.currentTimeMillis();
        long secondsTaken = (finished - started) / 1000;
        String timeSpan = TimeSpanFormatter.formatTimeSpan((int) secondsTaken);
        logger.log(INFO, "Finished scraping {0} in {1}", new Object[] { urlStr, timeSpan });

        String tracksStr = (trackCount == 1) ? "track" : "tracks";
        logger.log(INFO, "Found {0} {1} in the feed", new Object[] { trackCount, tracksStr });

        return feed;
    }

    /**
     * Scrapes the track summary from the given Mixcloud track URL.
     *
     * @param urlStr The Mixcloud track URL to scrape,
     *      e.g. "https://www.mixcloud.com/DJoseSolis/the-official-trance-podcast-episode-199/".
     *      Should not be or need to be URL-encoded.
     * @return The track's summary, or null if the relevant meta tag can't be found in the page.
     * @throws IOException
     * @throws MalformedURLException
     */
    private String scrapeTrackSummary(String urlStr) throws IOException, MalformedURLException {
        // download the track's web page
        StringBuilder sb = this.downloadWebPage(urlStr);

        // extract the track's summary
        return this.getMetaTagContent("og:description", sb, urlStr);
    }

    /**
     * Downloads a web page.
     *
     * @param urlStr The URL of the web page to download.
     *      Should not be or need to be URL-encoded, since we only download Mixcloud web pages.
     * @return The web page's contents.
     * @throws IOException
     * @throws MalformedURLException
     */
    private StringBuilder downloadWebPage(String urlStr) throws IOException, MalformedURLException {
        HttpURLConnection conn = null;
        InputStream in = null;

        try {
            logger.log(DEBUG, "Downloading URL: {0}", urlStr);

            // set up the HTTP connection
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", Main.config.getProperty("user_agent"));
            conn.setRequestProperty("Referer", urlStr);
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate");

            // create the appropriate stream wrapper based on the encoding type;
            // this code will throw FileNotFoundException on 404s
            String encoding = conn.getContentEncoding();

            if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
                in = new GZIPInputStream(conn.getInputStream());
            }
            else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
                in = new InflaterInputStream(conn.getInputStream(), new Inflater(true));
            }
            else {
                in = conn.getInputStream();
            }

            // is an encoding specified in the Content-Type HTTP header?
            String charset = this.getCharacterSet(conn.getContentType(), "utf-8");

            // download the web page
            StringBuilder sb = new StringBuilder(250_000);  // Mixcloud pages are kinda big...

            try (Reader reader = new InputStreamReader(in, charset)) {
                final char[] buf = new char[10_000];

                while (true) {
                    int charCount = reader.read(buf, 0, buf.length);
                    if (charCount < 0) break;

                    sb.append(buf, 0, charCount);
                }

                // the connection's InputStream is closed by the reader
            }

            return sb;
        }
        finally {
            if (conn != null) {
                conn.disconnect();
            }

            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * A container for a few tidbits of data about the file found at a music URL.
     */
    private static class MusicUrlHeaderData {
        /** The value of the Content-Type header. */
        String contentType;

        /** The value of the Content-Length header. */
        int contentLengthBytes;

        /** The value of the Last-Modified header. */
        Date lastModifiedDate;
    }

    /**
     * Gets HTTP header data associated with a music URL.
     * This performs a HEAD request on the URL, and returns the values of the Content-Length and Last-Modified headers.
     *
     * @param urlStr The music URL. Should not be or need to be URL-encoded.
     * @return Some HTTP header data associated with the URL.
     * @throws ApplicationException
     * @throws IOException
     * @throws MalformedURLException
     */
    private MusicUrlHeaderData getMusicUrlHeaderData(String urlStr)
            throws ApplicationException, IOException, MalformedURLException {
        HttpURLConnection conn = null;

        try {
            logger.log(DEBUG, "Getting HEAD of URL: {0}", urlStr);

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("User-Agent", Main.config.getProperty("user_agent"));
            conn.setRequestProperty("Referer", urlStr);

            String contentType = conn.getContentType();
            if (!contentType.startsWith("audio/") && !contentType.startsWith("video/")) {
                throw new ApplicationException(String.format("Unexpected Content-Type header: %s", contentType));
            }

            int length = conn.getContentLength();
            if (length < 0) {
                throw new ApplicationException("The content length is not known, or is greater than Integer.MAX_VALUE");
            }

            long lastModified = conn.getLastModified();
            if (lastModified == 0) {
                throw new ApplicationException("The last-modified date/time is not known");
            }

            MusicUrlHeaderData data = new MusicUrlHeaderData();
            data.contentType = contentType;
            data.contentLengthBytes = length;
            data.lastModifiedDate = new Date(lastModified);
            return data;
        }
        finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Gets the character set specified in the given Content-Type header,
     * falling back to the passed default if the header doesn't specify a character set.
     *
     * @param contentTypeHeader The Content-Type header to parse.
     * @return The character set specified in the header, or the passed default value.
     */
    private String getCharacterSet(String contentTypeHeader, String defaultValue) {
        // text/html; charset=utf-8
        if (contentTypeHeader != null) {
            final String attr = "charset=";
            int start = contentTypeHeader.toLowerCase(Locale.ROOT).indexOf(attr);

            if (start != -1) {
                start += attr.length();
                int end = contentTypeHeader.indexOf(' ', start);
                if (end == -1) end = contentTypeHeader.length();

                return contentTypeHeader.substring(start, end);
            }
        }

        return defaultValue;
    }

    /**
     * Returns the content of the given meta tag in the given HTML,
     * or null if a meta tag with the given value in its `property` attribute can't be found.
     *
     * @param property The meta tag's name, i.e. the `property` attribute value to search for.
     * @param html The HTML to search.
     * @param urlStr The URL from which the HTML was retrieved (only used for logging).
     * @return The meta tag's value, i.e. the value of its `content` attribute, or null.
     */
    private String getMetaTagContent(String property, CharSequence html, String urlStr) {
        // Mixcloud uses double quotes around HTML attribute values
        String regexStr = "<meta\\s+property\\s*=\\s*\"" + property + "\"\\s+content\\s*=\\s*\"([^\"]+)\"";

        String content = this.getRegexMatch(regexStr, html, 1);

        if (content == null) {
            String msg = String.format("Meta tag with property=\"%s\" not found%n    in %s", property, urlStr);
            logger.log(WARNING, msg);
            return null;
        }

        String normalized = this.normalizeLineBreaks(content.trim());
        return this.htmlEntities.unescape(normalized);
    }

    /**
     * Returns the value of the given attribute in the given HTML tag,
     * or null if a property with the given name can't be found.
     *
     * @param property The attribute's name.
     * @param html The HTML tag to search.
     * @param urlStr The URL from which the HTML tag was retrieved (only used for logging).
     * @return The attribute's value, or null.
     */
    private String getAttributeValue(String attributeName, CharSequence htmlTag, String urlStr) {
        // Mixcloud uses double quotes around HTML attribute values
        String regexStr = attributeName + "\\s*=\\s*\"([^\"]+)\"";

        String value = this.getRegexMatch(regexStr, htmlTag, 1);

        if (value == null) {
            String replacement = String.format("\"%n\t");
            String tag = htmlTag.toString().replaceAll("\"\\s+", replacement);
            String msg = String.format("Attribute \"%s\" not found%n    in tag %s%n    from %s",
                    attributeName, tag, urlStr);
            logger.log(WARNING, msg);
            return null;
        }

        String normalized = this.normalizeLineBreaks(value.trim());
        return this.htmlEntities.unescape(normalized);
    }

    /**
     * Returns the first match for the given regular expression in the given haystack, case-insensitively.
     *
     * @param regexStr The regular expression to match.
     * @param haystack The character sequence in which to find the regular expression.
     * @param group The capture group to return, or 0 to return the whole match.
     * @return The matching string, or null if a match cannot be found.
     */
    private String getRegexMatch(String regexStr, CharSequence haystack, int group) {
        Pattern re = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE);
        Matcher matcher = re.matcher(haystack);

        if (matcher.find()) {
            return matcher.group(group);
        }

        return null;  // regex not matched
    }

    /**
     * Normalizes line breaks in a string to the platform's default line separator.
     *
     * @param str The string to normalize.
     * @return The normalized string.
     */
    private String normalizeLineBreaks(String str) {
        if (str == null) return null;
        return str.replaceAll("\\r\\n|\\r|\\n", System.getProperty("line.separator"));
    }

    /**
     * Makes a URL absolute, including the protocol and host.
     * You need to pass the "context" of the URL, i.e. the URL of the web page on which it appears.
     *
     * @param urlStr The URL to make absolute.
     * @param contextUrl The URL of the web page containing the URL to be made absolute. Must be absolute itself.
     * @return The absolute form of the passed URL.
     */
    private String makeUrlAbsolute(String urlStr, String contextUrl) {
        if (urlStr.length() == 0) {
            return contextUrl;
        }
        else if (urlStr.startsWith("https://") || urlStr.startsWith("http://")) {
            return urlStr;  // already absolute
        }
        else if (urlStr.charAt(0) == '/') {
            // absolute with respect to the context's host
            if (contextUrl.endsWith("/")) contextUrl = contextUrl.substring(0, contextUrl.length() - 1);
            String[] parts = contextUrl.split("/"); // http[s]:, empty, host, ...
            return parts[0] + "//" + parts[2] + urlStr;
        }
        else {
            // relative to the context
            if (!contextUrl.endsWith("/")) contextUrl += "/";
            return contextUrl + urlStr;
        }
    }

    /** The thingy which can decode "m-play-info" attributes. */
    private final MixcloudDecoder decoder = new MixcloudDecoder();

    /** The thingy which can unescape HTML5 entities. */
    private final HtmlEntities htmlEntities = new HtmlEntities();
}
