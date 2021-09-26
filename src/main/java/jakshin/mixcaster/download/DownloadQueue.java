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

package jakshin.mixcaster.download;

import jakshin.mixcaster.Main;
import jakshin.mixcaster.utils.TimeSpanFormatter;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import static jakshin.mixcaster.logging.Logging.*;

/**
 * Downloads music files from Mixcloud.
 */
public final class DownloadQueue {
    /**
     * Gets the instance of this singleton class.
     * @return The DownloadQueue instance.
     */
    @NotNull
    public static synchronized DownloadQueue getInstance() {
        if (DownloadQueue.instance == null) {
            DownloadQueue.instance = new DownloadQueue();
        }

        return DownloadQueue.instance;
    }

    /**
     * Adds a download to the queue, unless the file has already been queued or downloaded.
     * Each time this is called, processQueue() must also be called, sooner or later.
     *
     * @param download The download to enqueue.
     * @return Whether the download was added to the queue.
     */
    public synchronized boolean enqueue(@NotNull Download download) {
        File localFile = new File(download.localFilePath);

        if (localFile.exists()) {
            logger.log(DEBUG, "File not enqueued (already downloaded): {0}", localFile);
            return false;  // already downloaded
        }

        if (this.waitingDownloads.contains(download) || this.activeDownloads.contains(download)) {
            logger.log(DEBUG, "File already queued: {0}", localFile);
            return false;  // already queued, doesn't yet exist locally
        }

        // if this method is called while the queue is already being processed,
        // any items which were already queued will be downloaded before this newly-added item,
        // because they'll already have been added to the thread pool and removed from waitingDownloads;
        // could maybe address that by using PriorityBlockingQueue instead of LinkedBlockingQueue (which is FIFO)
        this.waitingDownloads.add(download);
        waitingDownloads.sort(this.downloadComparator);

        logger.log(DEBUG, "File enqueued: {0}", localFile);
        return true;
    }

    /**
     * Gets a count of downloads which are waiting to start.
     * Active downloads are not included in the count.
     * Note that all waiting downloads are made active as soon as processQueue() is called,
     * even though it might still take a while for them to actually be downloaded.
     *
     * @return The number of downloads which have been queued since the last call to processQueue().
     */
    public synchronized int queueSize() {
        // this is really only useful for answering the question "should processQueue() be called?"
        return this.waitingDownloads.size();
    }

    /**
     * Processes the download queue.
     * We don't automatically start downloading as items are added to the queue
     * so that the sorting logic can first be applied across the whole set of downloads.
     *
     * @param exitWhenEmpty Whether to exit, via System.exit(), when the queue is empty.
     */
    public synchronized void processQueue(boolean exitWhenEmpty) {
        Download download;
        while ((download = this.waitingDownloads.poll()) != null) {
            this.activeDownloads.add(download);
            this.pool.execute(new DownloadRunnable(download));
        }

        if (exitWhenEmpty) {
            this.exitWhenEmpty = true;
            this.exitIfEmpty();
        }
    }

    /**
     * Removes an active download from the collection which tracks such things.
     * This is called by the DownloadRunnable inner class upon download completion.
     *
     * @param download The download to remove.
     */
    private synchronized void removeActiveDownload(@NotNull Download download) {
        this.activeDownloads.remove(download);

        if (this.exitWhenEmpty) {
            this.exitIfEmpty();
        }
    }

    /**
     * Exits if the queue is empty. This should only be called if exitWhenEmpty is true,
     * and only from synchronized methods (it's not synchronized itself).
     */
    private void exitIfEmpty() {
        if (this.activeDownloads.size() == 0 && this.waitingDownloads.size() == 0) {
            logger.log(DEBUG, "The download queue is empty, exiting as requested");
            System.exit(0);
        }
    }

    /**
     * A thing which can perform a download in a separate thread.
     */
    private class DownloadRunnable implements Runnable {
        /** Creates a new instance of the class. */
        DownloadRunnable(@NotNull Download download) {
            this.download = download;
        }

        /** Performs the download. */
        @Override
        public void run() {
            logger.log(INFO, String.format("Starting download: %s%n    => %s",
                    this.download.remoteUrl, this.download.localFilePath));

            HttpURLConnection conn = null;
            BufferedInputStream in = null;
            FileOutputStream out = null;

            long started = System.currentTimeMillis();
            int totalBytesWritten = 0;
            int percentComplete = 0;

            try {
                // set up the HTTP connection
                URL url = new URL(this.download.remoteUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", Main.config.getProperty("user_agent"));
                conn.setRequestProperty("Referer", this.download.remoteUrl);

                // open the HTTP connection; this code will throw FileNotFoundException on 404s
                in = new BufferedInputStream(conn.getInputStream());

                // download to a *.part file
                File localPartFile = new File(this.download.localFilePath + ".part");
                File localDir = localPartFile.getParentFile();

                if (!localDir.exists() && !localDir.mkdirs()) {
                    throw new IOException(String.format("Could not create directory \"%s\"", localPartFile.getParent()));
                }

                out = new FileOutputStream(localPartFile);
                final byte[] buf = new byte[50_000];

                while (true) {
                    int byteCount = in.read(buf, 0, buf.length);
                    if (byteCount < 0) break;

                    out.write(buf, 0, byteCount);

                    totalBytesWritten += byteCount;  // overflow if file is over 2^31 bytes in length
                    int percentNowComplete = (int) ((totalBytesWritten * 100f) / this.download.remoteLengthBytes);

                    if (percentNowComplete > percentComplete) {
                        // flush to disk each time we've downloaded another 1% of the file
                        percentComplete = percentNowComplete;
                        out.flush();
                        out.getFD().sync();
                    }

                    // show some progress info every so often, directly to stdout, so we don't clutter logs
                    if (percentComplete % 10 == 0 && progressShownAtPercent < percentComplete && percentComplete < 100) {
                        String name = new File(download.localFilePath).getName();
                        System.out.printf("  %d%% %s%n", percentComplete, name);
                        progressShownAtPercent = percentComplete;
                    }
                }

                out.close();
                out = null;

                // set the file's last-modified timestamp to match the value from Mixcloud's server
                if (!localPartFile.setLastModified(this.download.remoteLastModified.getTime())) {
                    String errMsg = String.format("Could not set file's last-modified timestamp (%s)", this.download.localFilePath);
                    throw new IOException(errMsg);
                }

                // rename the *.part file
                Path localPath = Paths.get(this.download.localFilePath);
                Files.deleteIfExists(localPath);
                Files.move(localPartFile.toPath(), localPath, StandardCopyOption.ATOMIC_MOVE);

                // log download time (we don't try to handle clock changes or DST entry/exit)
                long finished = System.currentTimeMillis();
                long secondsTaken = (finished - started) / 1000;
                String timeSpan = TimeSpanFormatter.formatTimeSpan((int) secondsTaken);

                logger.log(INFO, String.format("Finished download: %s%n    => %s in %s",
                        this.download.remoteUrl, this.download.localFilePath, timeSpan));
            }
            catch (IOException ex) {
                String msg = String.format("Aborted download: %s%n    => %s",
                        this.download.remoteUrl, this.download.localFilePath);
                logger.log(ERROR, msg, ex);
            }
            finally {
                removeActiveDownload(this.download);

                if (conn != null) {
                    conn.disconnect();  // doesn't throw
                }

                try {
                    if (in != null) {
                        in.close();
                    }
                }
                catch (IOException ex) {
                    logger.log(WARNING, "Error closing HTTP input stream", ex);
                }

                try {
                    if (out != null) {
                        out.close();
                    }
                }
                catch (IOException ex) {
                    logger.log(WARNING, "Error closing file output stream", ex);
                }
            }
        }

        /** The download. */
        private final Download download;

        /** Keeping track of when we've shown progress on stdout. */
        private int progressShownAtPercent = 0;
    }

    /** The pool of download threads. */
    private final ThreadPoolExecutor pool;

    /** The thing which compares two Download objects for sorting. */
    private final DownloadComparator downloadComparator;

    /** The queue of URLs waiting for download. */
    private final LinkedList<Download> waitingDownloads = new LinkedList<>();

    /** The queue of URLs being downloaded. */
    private final LinkedList<Download> activeDownloads = new LinkedList<>();

    /** Whether to call System.exit() after finishing processing the queue. */
    private boolean exitWhenEmpty = false;

    /** The single instance of this class. */
    private static DownloadQueue instance = null;

    /** Private constructor to prevent instantiation except via getInstance(). */
    private DownloadQueue() {
        String downloadThreadsStr = Main.config.getProperty("download_threads");
        int threads = Integer.parseInt(downloadThreadsStr);  // already validated

        // "threads" threads max (1-50), wait 5s before killing idle threads, don't retain any idle threads
        this.pool = new ThreadPoolExecutor(threads, threads, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        this.pool.allowCoreThreadTimeOut(true);

        boolean downloadOldestFirst = Boolean.parseBoolean(Main.config.getProperty("download_oldest_first"));
        this.downloadComparator = new DownloadComparator(downloadOldestFirst);
    }
}
