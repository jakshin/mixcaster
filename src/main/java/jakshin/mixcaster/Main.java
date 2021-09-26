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

import jakshin.mixcaster.download.Download;
import jakshin.mixcaster.download.DownloadQueue;
import jakshin.mixcaster.http.HttpServer;
import jakshin.mixcaster.install.Installer;
import jakshin.mixcaster.logging.Logging;
import jakshin.mixcaster.mixcloud.MixcloudClient;
import jakshin.mixcaster.mixcloud.MixcloudPlaylistException;
import jakshin.mixcaster.mixcloud.MixcloudUserException;
import jakshin.mixcaster.podcast.Podcast;
import jakshin.mixcaster.podcast.PodcastEpisode;
import jakshin.mixcaster.utils.AppVersion;
import jakshin.mixcaster.utils.FileLocator;
import jakshin.mixcaster.utils.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.Properties;

import static jakshin.mixcaster.logging.Logging.*;

/**
 * The main application class, which runs on launch.
 * It spins off threads to perform actions such as downloading from Mixcloud, serving HTTP requests, etc.
 */
public class Main {
    /**
     * The application's entry point.
     * @param args The command line arguments.
     */
    public static void main(@NotNull String[] args) {
        int exitCode = 0;
        Main main = new Main();
        String cmd = (args.length > 0) ? args[0].trim() : "";

        switch (cmd) {
            case "-download" -> exitCode = main.download(args);
            case "-service" -> main.runService();
            case "-install" -> exitCode = main.installService();
            case "-uninstall" -> exitCode = main.uninstallService();
            case "-version" -> main.printVersion();
            default -> {
                // command was omitted, or something we don't understand (including -h or --help)
                main.printVersion();
                main.printUsage();
            }
        }

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Properties which may be used across the application.
     */
    public static final Properties config = Main.initConfig();

    /**
     * Downloads a user's music files to a local directory, music_dir by default,
     * mostly like PodcastXmlResponder would do, but with some tweaks and additional options.
     *
     * @param args Command-line arguments, which will be parsed for required info such as
     *             Mixcloud username, and options like episode limits and output directory.
     * @return A code indicating success (0) or failure (1 or 2).
     */
    private int download(@NotNull String[] args) {
        try {
            // initialize logging (if this fails, the program will show error info on stdout and abort)
            Logging.initialize(false);
            logger.log(INFO, "Mixcaster {0}", AppVersion.display);

            // parse our command-line arguments
            String musicType = null;
            String username = null;
            String playlist = null;
            String maxEpisodes = null;
            String outPath = null;
            String rssPath = null;
            boolean badArgUsage = false;

            for (String arg : args) {
                if (arg.equals("-download"))
                    //noinspection UnnecessaryContinue
                    continue;
                else if (arg.startsWith("-max-episodes="))
                    maxEpisodes = arg.substring("-max-episodes=".length());
                else if (arg.startsWith("-out="))
                    outPath = arg.substring("-out=".length());
                else if (arg.startsWith("-rss="))
                    rssPath = arg.substring("-rss=".length());
                else if (arg.equalsIgnoreCase("-rss"))
                    rssPath = "";  // empty string -> use default
                else if (arg.equalsIgnoreCase("stream")
                        || arg.equalsIgnoreCase("shows")
                        || arg.equalsIgnoreCase("history")
                        || arg.equalsIgnoreCase("favorites")
                        || arg.equalsIgnoreCase("playlist"))
                    musicType = arg.toLowerCase();
                else if (arg.equals("uploads"))
                    musicType = "shows";
                else if (arg.equals("listens"))
                    musicType = "history";
                else if (arg.equals("playlists"))
                    musicType = "playlist";
                else if (username == null || username.isBlank())
                    username = arg;
                else if ("playlist".equals(musicType) && (playlist == null || playlist.isBlank()))
                    playlist = arg;
                else {
                    badArgUsage = true;
                    break;
                }
            }

            // validate command-line arguments
            try {
                if (maxEpisodes != null && !maxEpisodes.isBlank()) {
                    int max = Integer.parseInt(maxEpisodes);
                    if (max > 0) Main.config.setProperty("episode_max_count", maxEpisodes);
                }
            }
            catch (NumberFormatException e) {
                badArgUsage = true;
            }

            if (username == null || username.isBlank()
                    || ("playlist".equals(musicType) && (playlist == null || playlist.isBlank())) ) {
                badArgUsage = true;
            }

            if (badArgUsage) {
                printUsage();
                return 1;
            }

            // more checks and fix-ups for command-line arguments
            MixcloudClient client = null;

            if (musicType == null || musicType.isBlank()) {
                // use the user's default view
                client = new MixcloudClient();
                musicType = client.queryDefaultView(username);
            }

            if ("".equals(outPath)) {
                outPath = null;
            }

            if (outPath != null) {
                if (rssPath != null) {
                    // if we're downloading to an arbitrary directory, outside our server's content root,
                    // i.e. the music_dir, we can't give a valid path to the files in RSS
                    System.out.println("Sorry, the -out and -rss options can't be used together");
                    return 1;
                }

                if (! outPath.endsWith("/")) outPath += "/";
                if (outPath.startsWith("~/")) {
                    // expand tilde so -out=~/foo will work (the shell won't expand that tilde itself)
                    outPath = System.getProperty("user.home") + outPath.substring(1);
                }

                if (! Files.exists(Paths.get(outPath))) {
                    System.out.println("Output directory doesn't exist: " + outPath);
                    return 1;
                }
            }

            if ("".equals(rssPath)) {
                // Use a default filename, in the working directory; username might still be possessive here
                rssPath = (musicType.equals("playlist"))
                        ? username + "." + musicType + "." + playlist + ".rss.xml"
                        : username + "." + musicType + ".rss.xml";
            }
            else if (rssPath != null && rssPath.startsWith("~/")) {
                rssPath = System.getProperty("user.home") + rssPath.substring(1);
            }

            if (username.endsWith("'s") || username.endsWith("’s") || username.endsWith("‘s")) {
                // un-possessive the username
                username = username.substring(0, username.length() - 2);
            }

            // warn if we failed to load configuration from our properties file
            this.warnAboutConfigError();

            // query and download
            if (client == null) client = new MixcloudClient();
            Podcast podcast = client.query(username, musicType, playlist);

            if (rssPath != null) {
                logger.log(INFO, "Writing podcast RSS to {0}", rssPath);
                FileUtils.writeStringToFile(rssPath, podcast.createXml(), "UTF-8");
            }

            var queue = DownloadQueue.getInstance();
            for (PodcastEpisode episode : podcast.episodes) {
                String localPath = FileLocator.getLocalPath(episode.enclosureUrl.toString());

                if (outPath != null) {
                    // use the normal filename, but in the requested output directory,
                    // and without putting the file into a subdirectory based on username
                    int pos = episode.enclosureUrl.getPath().lastIndexOf('/');
                    String name = episode.enclosureUrl.getPath().substring(pos + 1);
                    localPath = outPath + name;
                }

                Download download = new Download(episode.enclosureMixcloudUrl.toString(),
                        episode.enclosureLengthBytes, episode.enclosureLastModified, localPath);
                queue.enqueue(download);
            }

            // ApolloClient and/or its OkHttp instance can still have running non-daemon threads,
            // which will keep the app from exiting naturally now or when all downloads are complete,
            // so we either call System.exit() manually, or ask DownloadQueue to do so when it's done

            int downloadCount = queue.queueSize();
            if (downloadCount == 0) {
                logger.log(INFO, podcast.episodes.isEmpty() ?
                        "Nothing to download" : "All music files have already been downloaded");
                System.exit(0);
            }
            else {
                String filesStr = (downloadCount == 1) ? "music file" : "music files";
                logger.log(INFO, "Downloading {0} {1} ...", new Object[] { downloadCount, filesStr });
                queue.processQueue(true);
            }

            return 0;
        }
        catch (MixcloudUserException ex) {
            logger.log(ERROR, String.format("There's no Mixcloud user with username %s", ex.username));
            logger.log(DEBUG, "", ex);
            return 2;
        }
        catch (MixcloudPlaylistException ex) {
            logger.log(ERROR, String.format("%s doesn't have a \"%s\" playlist", ex.username, ex.playlist));
            logger.log(DEBUG, "", ex);
            return 2;
        }
        catch (Throwable ex) {
            logger.log(ERROR, "Download failed", ex);
            return 2;
        }
    }

    /**
     * Starts a minimal HTTP server which serves podcast RSS XML and downloaded music files.
     */
    private void runService() {
        try {
            // initialize logging (if this fails, the program will show error info on stdout and abort)
            Logging.initialize(true);
            logger.log(INFO, "Mixcaster {0} starting up", AppVersion.display);

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
     * The service is started immediately, via launchd.
     *
     * @return A code indicating success (0) or failure (1 or 2).
     */
    private int installService() {
        Installer installer = new Installer();
        return installer.install();
    }

    /**
     * Uninstalls the launchd service definition.
     * The service is stopped if it's running, via launchd.
     *
     * @return A code indicating success (0) or failure (1 or 2).
     */
    private int uninstallService() {
        Installer installer = new Installer();
        return installer.uninstall();
    }

    /**
     * Prints version information.
     */
    private void printVersion() {
        System.out.printf("Mixcaster %s%n", AppVersion.display);
        System.out.println("https://github.com/jakshin/mixcaster");
    }

    /**
     * Prints usage information.
     */
    private void printUsage() {
        String self = System.getenv("MIXCASTER_SELF_NAME");

        if (self == null || self.isBlank()) {
            Path path = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());

            if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                self = "java -jar " + path.getFileName();
            else
                self = "mixcaster";  // Default
        }

        System.out.println();
        System.out.println("Usage: " + self + " <command>\n");

        System.out.println("Available commands:");
        System.out.println("  -download   Download a Mixcloud user's stream, shows, history, etc.");
        System.out.println("  -service    Run as a service, and accept HTTP requests for podcast RSS feeds");
        System.out.println("  -install    Install as a launchd user agent (launchd will run it with -service)");
        System.out.println("  -uninstall  Uninstall launchd user agent");
        System.out.println("  -version    Show version information and exit\n");

        System.out.println("When using the -download command, additional parameters are required,");
        System.out.println("and additional options are available.\n");

        System.out.println("Usage with -download:");
        System.out.println("  " + self + " -download <username> [stream|shows|favorites|history] [options]");
        System.out.println("  " + self + " -download <username> playlist <playlist> [options]\n");

        System.out.println("You may make the username a possessive, so the command line reads more naturally.");
        System.out.println("For example: " + self + " -download NTSRadio’s shows -max-episodes=3\n");

        System.out.println("When specifying a playlist, use the last path segment of its Mixcloud URL,");
        System.out.println("e.g. if its URL is www.mixcloud.com/some-user/playlists/some-super-great-music,");
        System.out.println("specify it on the command line as \"some-super-great-music\".\n");

        System.out.println("Options with -download:");
        System.out.println("  -max-episodes=NUM  Allow NUM episodes, overriding the episode_max_count config");
        System.out.println("  -out=DIRECTORY     Save music files to DIRECTORY, overriding the music_dir config");
        System.out.println("  -rss=FILE          Write podcast RSS to the given path/file (overwrite existing)");
    }

    /**
     * Logs a warning if we failed to load configuration settings from our properties file.
     * To be useful, this should be called after the logger has been initialized, obviously.
     */
    private void warnAboutConfigError() {
        if (Main.errorLoadingProperties != null) {
            String msg = String.format("Problem loading mixcaster-settings.properties (%s)",
                    Main.errorLoadingProperties.getMessage());
            logger.log(WARNING, msg, Main.errorLoadingProperties);
        }
    }

    /**
     * Initializes the global configuration by reading mixcaster-settings.properties.
     */
    private static Properties initConfig() {
        // set up default values; there should be a 1-to-1 correspondence between values here and in the properties file
        Properties defaults = new Properties();
        defaults.setProperty("download_oldest_first", "false");
        defaults.setProperty("download_threads", "3");            // must be an int in [1-50]
        defaults.setProperty("episode_max_count", "25");          // must be an int > 0
        defaults.setProperty("http_cache_time_seconds", "3600");  // must be an int >= 0
        defaults.setProperty("http_hostname", "localhost");
        defaults.setProperty("http_port", "6499");                // must be an int in [1024-65535]
        defaults.setProperty("log_max_count", "10");              // must be an int > 0
        defaults.setProperty("log_dir", "~/Library/Logs/Mixcaster");
        defaults.setProperty("log_level", "ALL");
        defaults.setProperty("music_dir", "~/Music/Mixcloud");
        defaults.setProperty("subscribed_to", "");                // whitespace-delimited list of usernames
        defaults.setProperty("user_agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)" +
                " AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.63 Safari/537.36");

        // populate the properties from disk, falling back to the hard-coded defaults above
        Properties cfg = new Properties(defaults);

        try {
            // load our properties file, if we can find it next to the jar or in any parent directory
            // (if we don't find the properties file, we'll silently carry on with default settings)
            Path path = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());

            while (path.getNameCount() > 0) {
                Path propsPath = Paths.get(path.toString(), "mixcaster-settings.properties");
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
            Main.validateIntegerProperty(cfg, "episode_max_count", 1, Integer.MAX_VALUE);
            Main.validateIntegerProperty(cfg, "http_cache_time_seconds", 0, Integer.MAX_VALUE);
            Main.validateIntegerProperty(cfg, "http_port", 1024, 65535);
            Main.validateIntegerProperty(cfg, "log_max_count", 1, Integer.MAX_VALUE);

            // validate string properties
            Main.validateStringProperty(cfg, "log_level",
                    new String[] { "ERROR", "WARNING", "INFO", "DEBUG", "ALL" });
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
     * removing it from the properties object if it does not.
     *
     * @param cfg The properties object.
     * @param propertyName The name of the property to check.
     * @param minValue The minimum allowed value.
     * @param maxValue The maximum allowed value.
     */
    private static void validateIntegerProperty(@NotNull Properties cfg,
                                                @NotNull String propertyName,
                                                int minValue,
                                                int maxValue) {
        try {
            String value = cfg.getProperty(propertyName);
            int num = Integer.parseInt(value);
            if (minValue <= num && num <= maxValue) return;  // things are A-OK
        }
        catch (NumberFormatException ex) {
            // fall through
        }

        // remove the errant value from the properties file, so the hard-coded valid default will be used
        cfg.remove(propertyName);
    }

    /**
     * Validates that the given property contains a string which is recognized as meaningful,
     * removing it from the properties object if it is not.
     *
     * @param cfg The properties object.
     * @param propertyName The name of the property to check.
     * @param validValues A list of valid potential values.
     */
    @SuppressWarnings("SameParameterValue")
    private static void validateStringProperty(@NotNull Properties cfg,
                                               @NotNull String propertyName,
                                               @NotNull String[] validValues) {
        String value = cfg.getProperty(propertyName);

        for (String validValue : validValues) {
            if (validValue.equalsIgnoreCase(value)) {
                return;  // this is a valid value
            }
        }

        // remove the errant value from the properties file, so the hard-coded valid default will be used
        cfg.remove(propertyName);
    }

    /** An exception which occurred while loading our properties file, or null if things are okay. */
    private static Exception errorLoadingProperties;
}
