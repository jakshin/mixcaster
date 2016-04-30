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
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Downloads music files from Mixcloud.
 */
public final class DownloadQueue {
    /**
     * Gets the instance of this singleton class.
     * @return The DownloadQueue instance.
     */
    public static synchronized DownloadQueue getInstance() {
        if (DownloadQueue.instance == null) {
            DownloadQueue.instance = new DownloadQueue();
        }

        return DownloadQueue.instance;
    }

    /**
     * Attempts to add a download to the queue.
     * If the file has already been downloaded, it's not added to the queue.
     *
     * @param download The download to enqueue.
     * @return Whether the download was added to the queue; if not added, it's already been downloaded.
     */
    public synchronized boolean enqueue(Download download) {
        File localFile = new File(download.localFilePath);
        if (localFile.exists()) {
            return false;  // already downloaded
        }

        if (this.waitingDownloads.contains(download) || this.activeDownloads.contains(download)) {
            return true;  // already queued, doesn't yet exist locally
        }

        // if this method is called while the queue is already being processed,
        // any items which were already queued will be downloaded before this newly-added item,
        // because they'll already have been added to the thread pool and removed from waitingDownloads;
        // could maybe address that by using PriorityBlockingQueue instead of LinkedBlockingQueue (which is FIFO)
        this.waitingDownloads.add(download);
        Collections.sort(waitingDownloads, this.downloadComparator);

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
     */
    public synchronized void processQueue() {
        Download download;
        while ((download = this.waitingDownloads.poll()) != null) {
            this.activeDownloads.add(download);
            this.pool.execute(new DownloadRunnable(download));
        }
    }

    /**
     * Removes an active download from the collection which tracks such things.
     * This is called by the DownloadRunnable inner class upon download completion.
     *
     * @param download The download to remove.
     */
    private synchronized void removeActiveDownload(Download download) {
        this.activeDownloads.remove(download);
    }

    /**
     * A thing which can perform a download in a separate thread.
     */
    private class DownloadRunnable implements Runnable {
        /** Creates a new instance of the class. */
        DownloadRunnable(Download download) {
            this.download = download;
        }

        /** Performs the download. */
        @Override
        public void run() {
            String msg = String.format("Starting download: %s%n    => %s",
                    this.download.remoteUrl, this.download.localFilePath);
            System.out.println(msg);

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

                // download the track to a *.part file
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
                }

                out.close();
                out = null;

                // set the file's last-modified timestamp to match the value from Mixcloud's server
                if (!localPartFile.setLastModified(this.download.remoteLastModifiedDate.getTime())) {
                    String errMsg = String.format("Could not set file's last-modified timestamp (%s)", this.download.localFilePath);
                    throw new IOException(errMsg);
                }

                // rename the *.part file
                Path localPath = Paths.get(this.download.localFilePath);
                Files.deleteIfExists(localPath);
                Files.move(localPartFile.toPath(), localPath, StandardCopyOption.ATOMIC_MOVE);

                long finished = System.currentTimeMillis();
                long secondsTaken = (finished - started) / 1000;

                msg = String.format("Finished download: %s%n    => %s in %s",
                        this.download.remoteUrl, this.download.localFilePath, formatTimeSpan((int) secondsTaken));
                System.out.println(msg);
            }
            catch (IOException ex) {
                // XXX logging
                System.out.println(ex.getClass().getCanonicalName() + ": " + ex.getMessage());
                ex.printStackTrace(); // XXX remove (logging instead)
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
                    // XXX logging
                }

                try {
                    if (out != null) {
                        out.close();
                    }
                }
                catch (IOException ex) {
                    // XXX logging
                }
            }
        }

        /** The download. */
        private final Download download;
    }

    /**
     * Formats a span of time, given in seconds, as h:mm:ss or m:ss (0:ss if less than one minute in length).
     *
     * @param seconds The time span in seconds.
     * @return A formatted representation of the time span.
     */
    private String formatTimeSpan(int seconds) {
        // XXX move this code into logging class
        int minutes = seconds / 60;
        seconds %= 60;

        if (minutes >= 60) {
            int hours = minutes / 60;
            minutes %= 60;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    /** The pool of download threads. */
    private final ThreadPoolExecutor pool;

    /** The thing which compares two Download objects for sorting. */
    private final DownloadComparator downloadComparator;

    /** The queue of URLs waiting for download. */
    private final LinkedList<Download> waitingDownloads = new LinkedList<>();

    /** The queue of URLs being downloaded. */
    private final LinkedList<Download> activeDownloads = new LinkedList<>();

    /** The single instance of this class. */
    private static DownloadQueue instance = null;

    /** Private constructor to prevent instantiation except via getInstance(). */
    private DownloadQueue() {
        String downloadThreadsStr = Main.config.getProperty("download_threads");
        int threads = Integer.parseInt(downloadThreadsStr);  // already validated

        // "threads" threads max (1-50), wait 5s before killing idle threads, don't retain any idle threads
        this.pool = new ThreadPoolExecutor(threads, threads, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        this.pool.allowCoreThreadTimeOut(true);

        boolean downloadOldestFirst = Boolean.valueOf(Main.config.getProperty("download_oldest_first"));
        this.downloadComparator = new DownloadComparator(downloadOldestFirst);
    }
}
