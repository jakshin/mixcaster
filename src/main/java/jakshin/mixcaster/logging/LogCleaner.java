/*
 * Copyright (c) 2021 Jason Jackson
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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static jakshin.mixcaster.logging.Logging.*;

/**
 * Cleans old log files out of our logging directory.
 */
@SuppressWarnings("ClassCanBeRecord")
public class LogCleaner implements Runnable {
    /**
     * Creates a new instance.
     * @param delayMillis How long to delay before starting to look for and delete log files.
     */
    public LogCleaner(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    /**
     * Cleans out the log directory.
     */
    @Override
    public void run() {
        try {
            if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }

            File logDir = openLogDir();
            List<FileAndTimestamp> logs = listLogFiles(logDir);

            if (logs != null) {
                List<String> locks = listLockFiles(logDir);
                trashLogFiles(logs, locks);
            }
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.log(DEBUG, "Log cleanup was interrupted", ex);
        }
    }

    /**
     * Opens our log directory.
     * @return A File object representing our log directory.
     */
    @NotNull
    @Contract(" -> new")
    private File openLogDir() {
        String logDirPath = System.getProperty("log_dir");
        if (logDirPath.startsWith("~/")) {
            logDirPath = System.getProperty("user.home") + logDirPath.substring(1);
        }

        return new File(logDirPath);
    }

    /**
     * Reports whether a file is one of our log files, based on its name.
     * @param filename The file's name.
     */
    private boolean isLogFile(@NotNull String filename) {
        return filename.matches("(download|service)\\.\\d+\\.log(\\.\\d+)?");
    }

    /**
     * Loads a list of log files in our log directory, sorted by last-modified date (oldest first).
     * @return A list of logs, or null if there are fewer than max_log_count.
     */
    @Nullable
    private List<FileAndTimestamp> listLogFiles(@NotNull File logDir) {
        // find log files in the log directory (ignoring any files that aren't our logs)
        File[] logFileArray = logDir.listFiles((dir, filename) -> isLogFile(filename));

        int logMaxCount = Integer.parseInt(System.getProperty("log_max_count"));
        if (logFileArray == null || logFileArray.length <= logMaxCount) {
            return null;
        }

        // load each file's last-modified date once, to minimize disk IO and avoid any chance
        // of IllegalArgumentException due to a file's last-modified changing during the sort
        List<FileAndTimestamp> logs = new ArrayList<>(logFileArray.length);
        for (File file : logFileArray) {
            logs.add(new FileAndTimestamp(file, file.lastModified()));
        }

        // sort the log files by last-modified date, with the oldest logs first
        logs.sort((a, b) -> {
            long diff = a.timestamp - b.timestamp;
            if (diff > 0) return 1;
            if (diff < 0) return -1;
            return 0;
        });

        return logs;
    }

    /**
     * Loads a list of lock files in our log directory.
     * These files indicate that their corresponding log files are still in use.
     *
     * @return A list of lock file names.
     */
    @NotNull
    private List<String> listLockFiles(@NotNull File logDir) {
        File[] lockFileArray = logDir.listFiles((dir, filename) -> filename.endsWith(".lck"));
        return (lockFileArray == null) ? List.of()
                : Arrays.stream(lockFileArray).map(File::getName).collect(Collectors.toList());
    }

    /**
     * Trashes or deletes log files, oldest first, until there are fewer the configured limit,
     * or all remaining log files are still in use.
     *
     * @param logs A list of log files.
     * @param locks A list of lock files.
     */
    private void trashLogFiles(@NotNull List<FileAndTimestamp> logs, @NotNull List<String> locks) {
        boolean deleteDownloadLogs = true,
                deleteServiceLogs = true;

        int needToDelete = logs.size() - Integer.parseInt(System.getProperty("log_max_count"));
        int handled = 0;

        for (var fileAndTimestamp : logs) {
            File file = fileAndTimestamp.file;
            String logFileName = file.getName();
            String lockFileName = logFileName + ".lck";

            if (locks.contains(lockFileName)) {
                if (logFileName.startsWith("download."))
                    deleteDownloadLogs = false;
                else
                    deleteServiceLogs = false;

                logger.log(DEBUG, "Found a lockfile, keeping newer logs of the same type: {0}", lockFileName);
                if (!deleteDownloadLogs && !deleteServiceLogs) break;
            }

            if ((! deleteDownloadLogs && logFileName.startsWith("download.")) ||
                (! deleteServiceLogs && logFileName.startsWith("service."))) continue;

            if (! trashLogFile(file)) {
                if (file.delete()) {
                    logger.log(DEBUG, "Deleted old log file: {0}", logFileName);
                }
                else {
                    logger.log(WARNING, "Unable to remove old log file: {0}", logFileName);
                }
            }

            // count the file as handled, even if we didn't successfully remove it,
            // so a transient problem deleting a log doesn't cause us to remove another newer log
            // that we normally wouldn't have removed
            if (++handled >= needToDelete) break;
        }
    }

    /**
     * Moves the given file to the trash.
     *
     * @param file The file to trash.
     * @return Whether the file was successfully trashed.
     */
    private boolean trashLogFile(@NotNull File file) {
        try {
            if (! Desktop.isDesktopSupported()) return false;
            if (! Desktop.getDesktop().moveToTrash(file)) return false;

            String logFileName = file.getName();
            logger.log(DEBUG, "Moved old log file to the trash: {0}", logFileName);
            return true;
        }
        catch (IllegalArgumentException ex) {
            return true;  // the specified file doesn't exist
        }
        catch (UnsupportedOperationException ex) {
            return false;
        }
    }

    /** Holds a file and its last-modified date. */
    private static record FileAndTimestamp(File file, long timestamp) { }

    /** How long to delay before starting to look for and delete log files. */
    private final long delayMillis;
}
