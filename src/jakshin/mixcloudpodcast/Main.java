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

package jakshin.mixcloudpodcast;

import jakshin.mixcloudpodcast.download.DownloadQueue;
import jakshin.mixcloudpodcast.http.HttpServer;
import jakshin.mixcloudpodcast.mixcloud.MixcloudFeed;
import jakshin.mixcloudpodcast.mixcloud.MixcloudScraper;
import jakshin.mixcloudpodcast.rss.PodcastRSS;
import jakshin.mixcloudpodcast.utils.FileUtils;
import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.*;

/**
 * The main application class, which runs on launch.
 * It spins off threads to perform actions such as scraping the Mixcloud website, serving HTTP requests, etc.
 */
public class Main {
    /**
     * The application's entry point.
     * @param args The command line arguments.
     */
    public static void main(String[] args) {
        Main main = new Main();
        String cmd = "";
        String option = null;

        if (args.length > 0) {
            cmd = args[0].trim();
            if (args.length > 1) option = args[1].trim();
        }

        switch (cmd) {
            case "-scrape":
                main.scrape(option);  // option = URL to scrape
                break;
            case "-service":
                main.runService();
                break;
            case "-install":
                main.installService();
                break;
            case "-uninstall":
                main.uninstallService();
                return;
            case "-version":
                main.printVersion();
                break;
            case "-usage":
            default:
                main.printUsage();
                break;
        }
    }

    /**
     * Properties which may be used across the application.
     */
    public static final Properties config = Main.initConfig();

    /**
     * The application's version number.
     */
    public static final String version = "0.6.4";

    /**
     * Scrapes the given Mixcloud feed URL, also downloading any tracks which haven't already been downloaded.
     * This is basically an interactive run of the same things that happen when the service is running
     * and a podcast's RSS XML file is requested over HTTP, except the RSS is written to a file
     * in the current working directory.
     *
     * @param mixcloudFeedUrl The Mixcloud feed URL to scrape.
     */
    private void scrape(String mixcloudFeedUrl) {
        try {
            System.out.println(String.format("MixcloudPodcast v%s", Main.version));

            if (mixcloudFeedUrl.isEmpty()) {
                System.out.println("Error: No Mixcloud feed URL given");
                System.out.println();
                this.printUsage();
                return;
            }

            Pattern re = Pattern.compile("^https://www.mixcloud.com/([a-z_-]+)/?$", Pattern.CASE_INSENSITIVE);
            Matcher matcher = re.matcher(mixcloudFeedUrl);

            if (!matcher.matches()) {
                System.out.println(String.format("Error: \"%s\" is not a Mixcloud feed URL", mixcloudFeedUrl));
                System.out.println("Mixcloud feed URLs look like https://www.mixcloud.com/FeedName/");
                return;
            }

            MixcloudFeed feed;
            PodcastRSS rss;

            try {
                System.out.println(String.format("Scraping %s ...", mixcloudFeedUrl));
                MixcloudScraper scraper = new MixcloudScraper();
                feed = scraper.scrape(mixcloudFeedUrl);
                rss = feed.createRSS(null);
            }
            catch (FileNotFoundException ex) {
                // TODO logging
                System.out.println("Error: The Mixcloud server returned 404 for the feed URL");
                return;
            }

            String rssFileName = matcher.group(1) + ".podcast.xml";
            System.out.println(String.format("Writing podcast RSS to %s", rssFileName));
            FileUtils.writeStringToFile(rssFileName, rss.toString(), "UTF-8");

            DownloadQueue downloads = DownloadQueue.getInstance();
            int downloadCount = downloads.queueSize();

            if (downloadCount == 0) {
                String msg = feed.tracks.isEmpty() ? "No tracks to download" : "All tracks have already been downloaded";
                System.out.println(msg);
            }
            else {
                String tracksStr = (downloadCount == 1) ? "track" : "tracks";
                System.out.println(String.format("Downloading %d %s ...", downloadCount, tracksStr));
                downloads.processQueue();
            }
        }
        catch (Throwable ex) {
            // TODO logging
            System.out.println(String.format("Error: An unexpected error occurred (%s)", ex.getClass().getCanonicalName()));
            System.out.println(ex.getMessage());
        }
    }

    /**
     * Starts a minimal HTTP server which serves podcast RSS XML and downloaded music files.
     */
    private void runService() {
        System.out.println("WARNING: the service doesn't actually work yet");
        HttpServer server = new HttpServer();
        server.run();
    }

    /**
     * Installs a launchd service definition.
     * The service is started immediately.
     */
    private void installService() {
        // TODO implement installation
        System.out.println("Not implemented yet");
    }

    /**
     * Uninstalls the launchd service definition.
     * The service is stopped if it's running.
     */
    private void uninstallService() {
        // TODO implement uninstallation
        System.out.println("Not implemented yet");
    }

    /**
     * Prints version information.
     */
    private void printVersion() {
        System.out.printf("MixcloudPodcast v%s%n", Main.version);
    }

    /**
     * Prints usage information.
     */
    private void printUsage() {
        Path path = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        String jarPath = path.getFileName().toString();
        if (!jarPath.toLowerCase(Locale.ROOT).endsWith(".jar")) jarPath = "MixcloudPodcast.jar";

        System.out.println("Usage: java -jar " + jarPath + " <command>\n");
        System.out.println("Available commands:");
        System.out.println("  -scrape <url>: Scrape a single Mixcloud feed URL");
        System.out.println("  -service:      Run as a service, and accept HTTP requests for podcast feeds");
        System.out.println("  -install:      Install as a launchd service (launchd will run it with -service)");
        System.out.println("  -uninstall:    Uninstall launchd service");
        System.out.println("  -version:      Show version information and exit");
        System.out.println("  -usage:        Show this usage information and exit");
    }

    /**
     * Initializes the global configuration by reading MixcloudPodcast.properties.
     */
    private static Properties initConfig() {
        // set up default values; there should be a 1-to-1 correspondence between values here and in the properties file
        Properties defaults = new Properties();
        defaults.setProperty("download_oldest_first", "false");
        defaults.setProperty("download_threads", "3");           // must be an int in [1-50]
        defaults.setProperty("http_cache_time_seconds", "600");  // must be an int >= 0
        defaults.setProperty("http_hostname", "localhost");
        defaults.setProperty("http_port", "25683");              // must be an int in [1024-65535]
        defaults.setProperty("music_dir", "~/Music/Mixcloud");
        defaults.setProperty("stream_url_regex", "\"stream_url\":\\s*\"([^\"]+)\"");
        defaults.setProperty("track_regex", "<span\\s+class\\s*=\\s*\"play-button\"([^>]+)>");
        defaults.setProperty("user_agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.9; rv:45.0) Gecko/20100101 Firefox/45.0");

        // populate the properties from disk, falling back to the hard-coded defaults above
        Properties cfg = new Properties(defaults);

        try {
            // load the configuration from disk
            Path path = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());

            while (path.getNameCount() > 0) {
                Path propsPath = Paths.get(path.toString(), "MixcloudPodcast.properties");
                if (!Files.exists(propsPath)) {
                    path = path.getParent();
                    continue;
                }

                try (InputStream in = new FileInputStream(propsPath.toString())) {
                    cfg.load(in);
                }

                break;
            }

            // validate numeric properties
            Main.validateIntegerProperty(cfg, "download_threads", 1, 50);
            Main.validateIntegerProperty(cfg, "http_cache_time_seconds", 0, Integer.MAX_VALUE);
            Main.validateIntegerProperty(cfg, "http_port", 1024, 65535);
        }
        catch (IOException ex) {
            // TODO logging
            String errMsg = ex.getClass().getCanonicalName() + ": " + ex.getMessage();
            System.out.println("Error loading configuration properties: " + errMsg);
            return defaults;
        }

        return cfg;
    }

    /**
     * Validates that the given property is a string representing an integer within the given range,
     * removing it from the properties object if it is not.
     *
     * @param cfg The properties object.
     * @param propertyName The name of the property to check.
     * @param minValue The minimum allowed value.
     * @param maxValue The maximum allowed value.
     * @return Whether or not the property's value was valid.
     */
    private static boolean validateIntegerProperty(Properties cfg, String propertyName, int minValue, int maxValue) {
        String value = cfg.getProperty(propertyName);
        boolean valid = false;  // pessimism

        try {
            int num = Integer.parseInt(value);
            if (minValue <= num && num <= maxValue) valid = true;
        }
        catch (NumberFormatException ex) {
            valid = false;
        }

        if (!valid) {
            // remove the errant value from the properties file, so the hard-coded valid default will be used
            cfg.remove(propertyName);
        }

        return valid;
    }
}
