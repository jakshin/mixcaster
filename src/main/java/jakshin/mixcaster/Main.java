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

import jakshin.mixcaster.dlqueue.DownloadQueue;
import jakshin.mixcaster.download.DownloadOptions;
import jakshin.mixcaster.download.Downloader;
import jakshin.mixcaster.http.HttpServer;
import jakshin.mixcaster.install.Installer;
import jakshin.mixcaster.logging.LogCleaner;
import jakshin.mixcaster.logging.Logging;
import jakshin.mixcaster.mixcloud.MusicSet;
import jakshin.mixcaster.stale.StaleFileRemover;
import jakshin.mixcaster.utils.AppSettings;
import jakshin.mixcaster.utils.AppVersion;
import jakshin.mixcaster.watch.Watcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Serial;
import java.nio.file.*;
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
            case "-download" -> exitCode = main.download(args);  // can call System.exit() itself
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

            // download
            int result = new Downloader(true).download(args, false);
            DownloadQueue queue = DownloadQueue.getInstance();

            if (queue.activeDownloadCount() == 0) {
                // ApolloClient's OkHttp instance's non-daemon threads will prevent a natural exit,
                // and DownloadQueue isn't going force the app to exit, so we need to do so here
                waitForThreadToFinish(logCleanerThread);
                System.exit(result);
            }

            return result;
        }
        catch (MusicSet.InvalidInputException | DownloadOptions.InvalidOptionException ex) {
            logger.log(DEBUG, "Invalid command line", ex);
            printUsage();
            return 1;
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

            var cleaner = new ScheduledThreadPoolExecutor(1);
            cleaner.scheduleWithFixedDelay(new LogCleaner(), 2, 3600, TimeUnit.SECONDS);

            Watcher.start(10);

            var remover = new StaleFileRemover();
            remover.start(600, 3600, TimeUnit.SECONDS);

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
        System.out.println("  " + self + " -download <mixcloud-url> [options]");
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
    private void init(String bannerText, boolean forService) throws InitException {
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
            throw new InitException("Couldn't initialize application settings", settingsException);
        else if (loggingException != null)
            throw new InitException("Couldn't initialize logging", loggingException);

        for (String failure : validationFailures) {
            logger.log(WARNING, failure);
        }
    }

    /**
     * Yikes, we weren't able to fully initialize the application.
     */
    private static class InitException extends Exception {
        /**
         * Constructs a new exception with the specified detail message and cause.
         *
         * @param message The detail message (which is saved for later retrieval by the Throwable.getMessage() method).
         * @param cause The cause (which is saved for later retrieval by the Throwable.getCause() method).
         *              (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
         */
        InitException(@NotNull String message, @Nullable Throwable cause) {
            super(message, cause);
        }

        /** Serialization version number.
            This should be updated whenever the class definition changes. */
        @Serial
        private static final long serialVersionUID = 1L;
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
