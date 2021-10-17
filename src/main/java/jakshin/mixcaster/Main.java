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

import jakshin.mixcaster.dlqueue.Download;
import jakshin.mixcaster.dlqueue.DownloadQueue;
import jakshin.mixcaster.http.HttpServer;
import jakshin.mixcaster.http.ServableFile;
import jakshin.mixcaster.install.Installer;
import jakshin.mixcaster.logging.LogCleaner;
import jakshin.mixcaster.logging.Logging;
import jakshin.mixcaster.mixcloud.MixcloudClient;
import jakshin.mixcaster.mixcloud.MixcloudPlaylistException;
import jakshin.mixcaster.mixcloud.MixcloudUserException;
import jakshin.mixcaster.mixcloud.MusicSet;
import jakshin.mixcaster.podcast.Podcast;
import jakshin.mixcaster.podcast.PodcastEpisode;
import jakshin.mixcaster.utils.AppSettings;
import jakshin.mixcaster.utils.AppVersion;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
        // because we use the java.awt.Desktop class (to send files to the trash),
        // the JVM shows a goofy Java icon in macOS's dock; this prevents that
        System.setProperty("apple.awt.UIElement", "true");

        int exitCode = 0;
        Main main = new Main();
        String cmd = (args.length > 0) ? args[0].trim() : "";

        switch (cmd) {
            case "-download" -> exitCode = main.download(args);
            case "-service" -> exitCode = main.runService();
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
     * Downloads a user's music files to a local directory, music_dir by default,
     * mostly like PodcastXmlResponder would do, but with some tweaks and additional options.
     *
     * @param args Command-line arguments, which will be parsed for required info such as
     *             Mixcloud username, and options like episode limits and output directory.
     * @return A code indicating success (0) or failure (1 or 2).
     */
    private int download(@NotNull String[] args) {
        Thread logCleanerThread = null;

        try {
            // initialize settings and logging
            this.init(String.format("Mixcaster %s", AppVersion.display), false);

            logCleanerThread = new Thread(new LogCleaner());
            logCleanerThread.start();

            // parse our command-line arguments
            MusicSet musicSet = MusicSet.of(Arrays.stream(args).filter(s -> !s.startsWith("-")).toList());

            DownloadOptions opts = DownloadOptions.of(Arrays.stream(args)
                    .filter(s -> s.startsWith("-") && !s.equals("-download")).toList());

            String problem = DownloadOptions.validate(opts);
            if (problem != null) {
                logger.log(ERROR, problem);
                return 1;
            }

            if (opts.limit() != null) {
                System.setProperty("episode_max_count", opts.limit());
            }

            // query and download
            String localHostAndPort = System.getProperty("http_hostname") + ":" + System.getProperty("http_port");
            MixcloudClient client = new MixcloudClient(localHostAndPort);

            if (musicSet.musicType() == null) {
                String defaultMusicType = client.queryDefaultView(musicSet.username());
                musicSet = new MusicSet(musicSet.username(), defaultMusicType, null);
            }

            Podcast podcast = client.query(musicSet);

            if (opts.rssPath() != null) {
                String rssPath = opts.rssPath();

                if (rssPath.isBlank()) {
                    String playlist = (musicSet.playlist() == null) ? "" : "." + musicSet.playlist();
                    rssPath = musicSet.username() + "." + musicSet.musicType() + playlist + ".rss.xml";
                }

                logger.log(INFO, "Writing podcast RSS to {0}", rssPath);
                Files.writeString(Paths.get(rssPath), podcast.createXml(), StandardCharsets.UTF_8);
            }

            DownloadQueue queue = DownloadQueue.getInstance();
            for (PodcastEpisode episode : podcast.episodes) {
                String localPath = ServableFile.getLocalPath(episode.enclosureUrl.toString());

                if (opts.outDirPath() != null) {
                    // use the normal filename, but in the requested output directory,
                    // and without putting the file into a subdirectory based on username
                    int pos = episode.enclosureUrl.getPath().lastIndexOf('/');
                    String name = episode.enclosureUrl.getPath().substring(pos + 1);
                    localPath = Path.of(opts.outDirPath(), name).toString();
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
                waitForThreadToFinish(logCleanerThread);
                System.exit(0);
            }
            else {
                String filesStr = (downloadCount == 1) ? "music file" : "music files";
                logger.log(INFO, "Downloading {0} {1} ...", new Object[] { downloadCount, filesStr });
                queue.processQueue(true);
            }

            return 0;
        }
        catch (MusicSet.InvalidInputException | DownloadOptions.InvalidOptionException ex) {
            logger.log(DEBUG, "Invalid command line", ex);
            printUsage();
            return 1;
        }
        catch (MixcloudUserException ex) {
            logger.log(ERROR, "There''s no Mixcloud user with username {0}", ex.username);
            logger.log(DEBUG, "", ex);
            return 2;
        }
        catch (MixcloudPlaylistException ex) {
            logger.log(ERROR, "{0} doesn''t have a \"{1}\" playlist", new String[] {ex.username, ex.playlist});
            logger.log(DEBUG, "", ex);
            return 2;
        }
        catch (Throwable ex) {
            // if we haven't initialized logging, logs just go to stdout
            logger.log(ERROR, "Download failed", ex);
            return 2;
        }
        finally {
            // in case we caught an exception
            waitForThreadToFinish(logCleanerThread);
        }
    }

    /**
     * Starts a minimal HTTP server which serves podcast RSS XML and downloaded music files.
     */
    private int runService() {
        try {
            String serviceVersion = AppVersion.display.startsWith("(")
                    ? AppVersion.display : String.format("(%s)", AppVersion.display);
            this.init(String.format("Mixcaster service starting up %s", serviceVersion), true);

            var executor = new ScheduledThreadPoolExecutor(1);
            executor.scheduleWithFixedDelay(new LogCleaner(), 2, 3600, TimeUnit.SECONDS);

            HttpServer server = new HttpServer();
            server.run();

            return 0;  // we don't actually reach here
        }
        catch (Throwable ex) {
            // if we haven't initialized logging, logs just go to stdout
            logger.log(ERROR, ex, ex::getMessage);
            return 2;
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
        System.out.println("For example: " + self + " -download NTSRadio’s shows -limit=3\n");

        System.out.println("When specifying a playlist, use the last path segment of its Mixcloud URL,");
        System.out.println("e.g. if its URL is www.mixcloud.com/some-user/playlists/some-super-great-music,");
        System.out.println("specify it on the command line as \"some-super-great-music\".\n");

        System.out.println("Options with -download:");
        System.out.println("  -limit=NUM      Download NUM files max (default is episode_max_count setting)");
        System.out.println("  -out=DIRECTORY  Save music files to DIRECTORY (default is music_dir setting)");
        System.out.println("  -rss=PATH/FILE  Write podcast RSS to FILE (overwriting any pre-existing file)");
    }

    /**
     * Initializes application settings and then logging.
     * Logs the given banner text and then any settings validation failures.
     *
     * @param bannerText Text to display as a startup banner in the first log line.
     * @param forService Whether to initialize logging for the service (if false, initialize for manual downloading).
     */
    private void init(String bannerText, boolean forService) throws AppException {
        // this code looks weird; it's like this because we need to initialize settings before logging,
        // but if we fail to initialize settings, we want to try to log that problem
        Exception settingsException = null, loggingException = null;
        List<String> validationFailures = null;

        try {
            validationFailures = AppSettings.initSettings();
        }
        catch (IOException | SecurityException ex) {
            settingsException = ex;
        }

        try {
            Logging.initLogging(forService);
        }
        catch (IOException | SecurityException ex) {
            loggingException = ex;
        }

        // if we haven't initialized logging, logs just go to stdout
        logger.log(INFO, bannerText);

        if (settingsException != null)
            throw new AppException("Couldn't initialize application settings", settingsException);
        else if (loggingException != null)
            throw new AppException("Couldn't initialize logging", loggingException);

        for (String failure : validationFailures) {
            logger.log(WARNING, failure);
        }
    }

    /**
     * Waits for the thread to finish, if it exists.
     * @param thread The thread to wait for.
     */
    private void waitForThreadToFinish(Thread thread) {
        try {
            if (thread != null) {
                thread.join();
            }
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
