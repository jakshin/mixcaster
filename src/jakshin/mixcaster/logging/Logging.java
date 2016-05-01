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

import java.io.File;
import java.io.IOException;
import java.util.logging.*;

/**
 * Provides logging services to the application.
 * This initializes a global logger and makes it available to any code which needs it.
 */
public class Logging {
    /**
     * Initializes the global logger.
     *
     * @param forService Whether to initialize for logging by the service (if false, initialize for manual scraping).
     * @throws IOException
     */
    public static void initialize(boolean forService) throws IOException {
        if (initialized) return;
        initialized = true;

        // TODO log dir should be configurable
        String logDirStr = System.getProperty("user.home") + "/Library/Logs/Mixcaster";  // no slash at end
        File logDir = new File(logDirStr);

        if (!logDir.exists() && !logDir.mkdirs()) {
            throw new IOException(String.format("Unable to create logging directory %s", logDirStr));
        }

        LogManager.getLogManager().reset();
        logger.setLevel(Level.ALL);

        // TODO log count shoud be configurable
        FileHandler fh = (forService)
                ? new FileHandler(logDirStr + "/service.log", 10_000, 20, true)  // TODO limit -> 1_000_000
                : new FileHandler(logDirStr + "/scrape.log", 0, 20, false);  // one log per scrape, keep 20 logs max
        fh.setFormatter(new LogFileFormatter());
        fh.setLevel(Level.ALL);  // TODO logging level should be configurable
        logger.addHandler(fh);

        SystemOutHandler soh = new SystemOutHandler(new SystemOutFormatter(), Level.INFO);
        logger.addHandler(soh);
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
    private static boolean initialized = false;

    /**
     * Private constructor to prevent instantiation.
     * This class's methods are all static, and it shouldn't be instantiated.
     */
    private Logging() {
        // nothing here
    }
}
