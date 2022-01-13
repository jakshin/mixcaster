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

package jakshin.mixcaster.logging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.*;

/**
 * Provides logging services to the application.
 * This initializes a global logger and makes it available to any code which needs it.
 */
public final class Logging {
    /**
     * Initializes the global logger.
     * @param forService Whether to initialize for logging by the service (if false, initialize for manual downloading).
     */
    public static synchronized void initLogging(boolean forService) throws IOException {
        if (initialized) return;
        initialized = true;

        // reset system defaults and set up logging to stdout
        LogManager.getLogManager().reset();
        logger.setLevel(Level.ALL);

        SystemOutHandler soh = new SystemOutHandler(new SystemOutFormatter(), Level.INFO);
        logger.addHandler(soh);

        // create the log directory if it doesn't already exist
        String logDirPath = Logging.getLogDirSetting();
        File logDir = new File(logDirPath);

        if (!logDir.exists() && !logDir.mkdirs() && !logDir.exists()) {
            throw new IOException(String.format("Unable to create logging directory \"%s\"", logDirPath));
        }

        // set up file logging;
        // note that LogCleaner also knows the log file name patterns below
        int logCount = Logging.getLogMaxCountSetting();
        FileHandler fh;

        if (forService) {
            // create a new log file every time the service starts, rotate log files at ~1 MB
            fh = new FileHandler(logDirPath + "service.%g.log", 1_000_000, logCount, false);
        }
        else {
            // create a new log file for every download run, with no size limit
            fh = new FileHandler(logDirPath + "download.%g.log", 0, logCount, false);
        }

        fh.setFormatter(new LogFileFormatter());
        fh.setLevel(Logging.getLogLevelSetting());
        logger.addHandler(fh);
    }

    /**
     * The global logger. You may use this directly; when calling its log() and related methods,
     * you can and should pass one of the static Levels defined below.
     */
    public static final Logger logger = Logger.getLogger("mixcaster");

    /** Error logging level. */
    public static final Level ERROR = Level.SEVERE;

    /** Warning logging level. */
    public static final Level WARNING = Level.WARNING;

    /** Info logging level. */
    public static final Level INFO = Level.INFO;

    /** Debug logging level. */
    public static final Level DEBUG = Level.FINE;

    /** Whether logging has been initialized or not. */
    private static boolean initialized;

    @VisibleForTesting
    static synchronized void resetLogging() {
        LogManager.getLogManager().reset();
        initialized = false;
    }

    /**
     * Gets the log_max_count configuration setting.
     * @return log_max_value, converted to an int.
     */
    private static int getLogMaxCountSetting() {
        String countStr = System.getProperty("log_max_count");
        return Integer.parseInt(countStr);  // already validated
    }

    /**
     * Gets the log_dir configuration setting.
     * @return log_dir, as a string, with a trailing slash.
     */
    @NotNull
    private static String getLogDirSetting() {
        String logDirStr = System.getProperty("log_dir");

        if (logDirStr.startsWith("~/")) {
            logDirStr = System.getProperty("user.home") + logDirStr.substring(1);
        }

        if (!logDirStr.isEmpty() && !logDirStr.endsWith("/")) {
            logDirStr += "/";
        }

        return logDirStr;
    }

    /**
     * Gets the log_level configuration setting.
     * @return log_level, as a Level object.
     */
    @NotNull
    private static Level getLogLevelSetting() {
        String logLevelStr = System.getProperty("log_level").toUpperCase(Locale.ENGLISH);  // already validated
        if (logLevelStr.equals("ERROR")) {
            logLevelStr = "SEVERE";
        }
        else if (logLevelStr.equals("DEBUG")) {
            logLevelStr = "FINE";
        }

        return Level.parse(logLevelStr);
    }

    /**
     * Private constructor to prevent instantiation.
     * This class's methods are all static, and it shouldn't be instantiated.
     */
    private Logging() {
        // nothing here
    }
}
