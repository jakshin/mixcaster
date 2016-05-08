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

package jakshin.mixcaster;

import jakshin.mixcaster.download.DownloadQueue;
import jakshin.mixcaster.http.HttpServer;
import jakshin.mixcaster.logging.Logging;
import jakshin.mixcaster.mixcloud.MixcloudFeed;
import jakshin.mixcaster.mixcloud.MixcloudScraper;
import jakshin.mixcaster.podcast.Podcast;
import jakshin.mixcaster.utils.FileUtils;
import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.*;
import static jakshin.mixcaster.logging.Logging.*;

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
                main.printVersion();
                main.printUsage(false);
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
    public static final String version = "0.7.9";

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
            // initialize logging (if this fails, the program will show error info on stdout and abort)
            Logging.initialize(false);
            logger.log(INFO, "Mixcaster v{0}", Main.version);

            // check our arguments
            if (mixcloudFeedUrl == null || mixcloudFeedUrl.isEmpty()) {
                logger.log(ERROR, "No Mixcloud feed URL given");
                this.printUsage(true);
                return;
            }

            Pattern re = Pattern.compile("^https://www.mixcloud.com/([a-z_-]+)/?$", Pattern.CASE_INSENSITIVE);
            Matcher matcher = re.matcher(mixcloudFeedUrl);

            if (!matcher.matches()) {
                logger.log(ERROR, "\"{0}\" is not a Mixcloud feed URL", mixcloudFeedUrl);
                this.printUsage(true);
                return;
            }

            // warn if we failed to load configuration from our properties file
            this.warnAboutConfigError();

            // scrape
            MixcloudFeed feed;

            try {
                MixcloudScraper scraper = new MixcloudScraper();
                feed = scraper.scrape(mixcloudFeedUrl);
            }
            catch (FileNotFoundException ex) {
                logger.log(ERROR, "The Mixcloud server returned 404 for the feed URL");
                return;
            }

            String fileName = matcher.group(1) + ".podcast.xml";
            logger.log(INFO, "Writing podcast RSS to {0}", fileName);
            Podcast podcast = feed.createPodcast(null);
            FileUtils.writeStringToFile(fileName, podcast.createXml(), "UTF-8");

            // process any new downloads
            DownloadQueue downloads = DownloadQueue.getInstance();
            int downloadCount = downloads.queueSize();

            if (downloadCount == 0) {
                String msg = feed.tracks.isEmpty() ? "No tracks to download" : "All tracks have already been downloaded";
                logger.log(INFO, msg);
            }
            else {
                String tracksStr = (downloadCount == 1) ? "track" : "tracks";
                logger.log(INFO, "Downloading {0} {1} ...", new Object[] { downloadCount, tracksStr });
                downloads.processQueue();
            }
        }
        catch (Throwable ex) {
            logger.log(ERROR, "Scrape failed", ex);
        }
    }

    /**
     * Starts a minimal HTTP server which serves podcast RSS XML and downloaded music files.
     */
    private void runService() {
        System.out.println("WARNING: the service doesn't actually work yet");

        try {
            // initialize logging (if this fails, the program will show error info on stdout and abort)
            Logging.initialize(true);
            logger.log(INFO, "Mixcaster v{0} starting up", Main.version);

            // warn if we failed to load configuration from our properties file
            this.warnAboutConfigError();

            // run the service
            HttpServer server = new HttpServer();
            server.run();
        }
        catch (Throwable ex) {
            logger.log(ERROR, ex.getMessage(), ex);
        }
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
        System.out.printf("Mixcaster v%s%n", Main.version);
    }

    /**
     * Prints usage information.
     * @param newLine Whether to print a line break before the usage info.
     */
    private void printUsage(boolean withLineBreak) {
        Path path = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        String jarPath = path.getFileName().toString();
        if (!jarPath.toLowerCase(Locale.ROOT).endsWith(".jar")) jarPath = "Mixcaster.jar";

        if (withLineBreak) {
            System.out.println();
        }

        System.out.println("Usage: java -jar " + jarPath + " <command>\n");
        System.out.println("Available commands:");
        System.out.println("  -scrape <url>: Scrape a Mixcloud feed URL (https://www.mixcloud.com/FeedName)");
        System.out.println("  -service:      Run as a service, and accept HTTP requests for podcast feeds");
        System.out.println("  -install:      Install as a launchd service (launchd will run it with -service)");
        System.out.println("  -uninstall:    Uninstall launchd service");
        System.out.println("  -version:      Show version information and exit");
        System.out.println("  -usage:        Show this usage information and exit");
    }

    /**
     * Logs a warning if we failed to load configuration settings from our properties file.
     * To be useful, this should be called after the logger has been initialized, obviously.
     */
    private void warnAboutConfigError() {
        if (Main.errorLoadingProperties != null) {
            String msg = String.format("Problem loading configuration from Mixcaster.properties (%s)",
                    Main.errorLoadingProperties.getMessage());
            logger.log(WARNING, msg, Main.errorLoadingProperties);
        }
    }

    /**
     * Initializes the global configuration by reading Mixcaster.properties.
     */
    private static Properties initConfig() {
        // set up default values; there should be a 1-to-1 correspondence between values here and in the properties file
        Properties defaults = new Properties();
        defaults.setProperty("download_oldest_first", "false");
        defaults.setProperty("download_threads", "3");            // must be an int in [1-50]
        defaults.setProperty("http_cache_time_seconds", "3600");  // must be an int >= 0
        defaults.setProperty("http_hostname", "localhost");
        defaults.setProperty("http_port", "25683");               // must be an int in [1024-65535]
        defaults.setProperty("log_max_count", "20");              // must be an int >= 0
        defaults.setProperty("log_dir", "~/Library/Logs/Mixcaster");
        defaults.setProperty("log_level", "ALL");
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
                Path propsPath = Paths.get(path.toString(), "Mixcaster.properties");
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
            Main.validateIntegerProperty(cfg, "log_max_count", 0, Integer.MAX_VALUE);

            // validate string properties
            Main.validateStringProperty(cfg, "log_level", new String[] { "ERROR", "WARNING", "INFO", "DEBUG", "ALL" });
        }
        catch (IOException ex) {
            // our logger isn't initialized yet, so we save the exception for later logging,
            // and carry on with defaults
            Main.errorLoadingProperties = ex;
            return defaults;
        }

        return cfg;
    }

    /**
     * Validates that the given property contains a string representing an integer within the given range,
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

    /**
     * Validates that the given property contains a string which is recognized as meaningful,
     * removing it from the properties object if it is not.
     *
     * @param cfg The properties object.
     * @param propertyName The name of the property to check.
     * @param validValues A list of valid potential values.
     * @return Whether or not the property's value was valid.
     */
    private static boolean validateStringProperty(Properties cfg, String propertyName, String[] validValues) {
        String value = cfg.getProperty(propertyName);
        boolean valid = false;  // pessimism

        for (String validValue : validValues) {
            if (validValue.equalsIgnoreCase(value)) {
                valid = true;
                break;
            }
        }

        if (!valid) {
            // remove the errant value from the properties file, so the hard-coded valid default will be used
            cfg.remove(propertyName);
        }

        return valid;
    }

    /** An exception which occurred while loading our properties file, or null if things are okay. */
    private static Exception errorLoadingProperties;
}
