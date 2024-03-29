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

package jakshin.mixcaster.dlqueue;

import jakshin.mixcaster.mixcloud.MusicSet;
import jakshin.mixcaster.stale.Freshener;
import jakshin.mixcaster.utils.TimeSpanFormatter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static jakshin.mixcaster.logging.Logging.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;

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
            logger.log(DEBUG, "File already exists: {0}", localFile);
            freshenAttributes(localFile.toPath(), download.inWatchedSet);  // touch the existing file
            return false;  // already downloaded
        }

        if (this.waitingDownloads.contains(download) || this.activeDownloads.contains(download)) {
            logger.log(DEBUG, "File already in queue: {0}", localFile);
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
     * @param callWhenEmpty A callback to invoke when the queue is empty (optional).
     *                      If processQueue() is called multiple times with different callbacks,
     *                      only the last one passed is retained. It's only invoked once.
     *                      It might be invoked immediately on the same thread this was called on,
     *                      or later on a different thread that was handling a download.
     */
    public synchronized void processQueue(@Nullable Runnable callWhenEmpty) {
        Download download;
        while ((download = this.waitingDownloads.poll()) != null) {
            this.activeDownloads.add(download);
            this.pool.execute(new DownloadRunnable(download));
        }

        if (callWhenEmpty != null) {
            this.callWhenEmpty = callWhenEmpty;
            this.callIfEmpty();
        }
    }

    /**
     * Gets a count of downloads that are active now.
     * They may or may not actually be downloading.
     *
     * @return The number of downloads that are currently active.
     */
    public synchronized int activeDownloadCount() {
        return this.activeDownloads.size();
    }

    /**
     * Removes an active download from the collection which tracks such things.
     * This is called by the DownloadRunnable inner class upon download completion.
     *
     * @param download The download to remove.
     */
    private synchronized void removeActiveDownload(@NotNull Download download) {
        this.activeDownloads.remove(download);
        this.callIfEmpty();
    }

    /**
     * Calls the callback passed to processQueue() if the queue is empty.
     */
    private synchronized void callIfEmpty() {
        if (callWhenEmpty != null && activeDownloads.isEmpty() && waitingDownloads.isEmpty()) {
            callWhenEmpty.run();
            callWhenEmpty = null;
        }
    }

    /**
     * Freshens the given file's "lastUsed" and maybe "watches" user-defined attributes.
     *
     * @param path The file whose attributes should be freshened.
     * @param watchedSet The watched music set that required this file to be downloaded (optional).
     */
    private void freshenAttributes(@NotNull Path path, @Nullable MusicSet watchedSet) {
        var freshener = new Freshener();
        freshener.updateLastUsedAttr(path);

        if (watchedSet != null) {
            freshener.updateWatchesAttr(path, watchedSet);
        }
    }

    /**
     * A thing which can perform a download in a separate thread.
     */
    @VisibleForTesting
    class DownloadRunnable implements Runnable {
        /** Creates a new instance of the class. */
        DownloadRunnable(@NotNull Download download) {
            this.download = download;
        }

        /** Performs the download. */
        @Override
        public void run() {
            HttpURLConnection conn = null;

            try {
                long started = System.nanoTime();
                logger.log(INFO, "Starting download: {0}{1}    => {2}",
                        new String[] {this.download.remoteUrl, System.lineSeparator(), this.download.localFilePath});

                // set up the HTTP connection we'll download from
                conn = openConnection(this.download.remoteUrl);  // no actual network connection yet
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", System.getProperty("user_agent"));
                conn.setRequestProperty("Referer", this.download.remoteUrl);

                // set up the destination directory we'll download into
                File localPartFile = new File(this.download.localFilePath + ".part");
                File localDir = localPartFile.getParentFile();

                synchronized (DownloadRunnable.class) {
                    if (!localDir.isDirectory() && !localDir.mkdirs()) {
                        throw new IOException(String.format("Could not create directory \"%s\"", localPartFile.getParent()));
                    }
                }

                if (Files.isSymbolicLink(localPartFile.toPath())) {
                    // remove the symlink; if it's created again before we open our output stream below,
                    // an IOException will be thrown: "Too many levels of symbolic links (NOFOLLOW_LINKS specified)"
                    Files.deleteIfExists(localPartFile.toPath());
                }

                // open the HTTP connection; this code will throw FileNotFoundException on 404s
                try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
                     OutputStream out = Files.newOutputStream(localPartFile.toPath(),
                             CREATE, SYNC, TRUNCATE_EXISTING, WRITE, NOFOLLOW_LINKS)) {

                    freshenAttributes(localPartFile.toPath(), this.download.inWatchedSet);  // ASAP after file creation

                    long totalBytesWritten = 0;
                    final byte[] buf = new byte[65536];  // 64 KiB

                    while (true) {
                        int byteCount = in.read(buf, 0, buf.length);
                        if (byteCount < 0) break;

                        out.write(buf, 0, byteCount);

                        // show some progress info every so often, directly to stdout, so we don't clutter logs
                        totalBytesWritten += byteCount;
                        int percentComplete = (int) ((totalBytesWritten * 100f) / this.download.remoteLengthBytes);

                        if (percentComplete % 10 == 0 && percentComplete < 100 && progressShownAtPercent < percentComplete) {
                            String name = new File(download.localFilePath).getName();
                            System.out.printf("  %d%% %s%n", percentComplete, name);
                            progressShownAtPercent = percentComplete;
                        }
                    }
                }

                // set the file's last-modified timestamp to match the value from Mixcloud's server
                if (!localPartFile.setLastModified(this.download.remoteLastModified.getTime())) {
                    String errMsg = String.format("Could not set file's last-modified timestamp (%s)",
                                                   this.download.localFilePath);
                    throw new IOException(errMsg);
                }

                // rename the *.part file
                Path localPath = Paths.get(this.download.localFilePath);
                Files.deleteIfExists(localPath);
                Files.move(localPartFile.toPath(), localPath, StandardCopyOption.ATOMIC_MOVE);

                // log download time
                long elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000;
                String timeSpan = TimeSpanFormatter.formatTimeSpan((int) elapsedSeconds);

                logger.log(INFO, () -> String.format("Finished download: %s%n    => %s in %s",
                                       this.download.remoteUrl, this.download.localFilePath, timeSpan));
            }
            catch (Throwable ex) {
                logger.log(ERROR, ex, () -> String.format("Aborted download: %s%n    => %s",
                                            this.download.remoteUrl, this.download.localFilePath));

                if (conn != null) {
                    // we don't close the underlying connection after a successful download,
                    // so it can be reused, but do disconnect and discard it after failure,
                    // to avoid potential problems with the next download that tries to use it;
                    // see https://stackoverflow.com/a/2010103 and https://stackoverflow.com/a/1441491
                    conn.disconnect();  // doesn't throw
                }
            }
            finally {
                // we're done with this download, whether it worked or not
                removeActiveDownload(this.download);
            }
        }

        /**
         * Returns an object that represents a connection to a remote URL.
         * @param spec A string representation of the remote URL.
         */
        @NotNull
        @VisibleForTesting
        HttpURLConnection openConnection(@NonNls @NotNull String spec) throws IOException {
            // this doesn't actually establish a network connection;
            // the returned object's getInputStream() does that
            var url = new URL(spec);
            return (HttpURLConnection) url.openConnection();
        }

        /** The download. */
        private final Download download;

        /** Keeping track of when we've shown progress on stdout. */
        private int progressShownAtPercent;
    }

    /** The pool of download threads. */
    private final ThreadPoolExecutor pool;

    /** The thing which compares two Download objects for sorting. */
    private final DownloadComparator downloadComparator;

    /** The queue of URLs waiting for download. */
    private final LinkedList<Download> waitingDownloads = new LinkedList<>(); //NOPMD - suppressed LooseCoupling

    /** The queue of URLs being downloaded. */
    private final LinkedList<Download> activeDownloads = new LinkedList<>();  //NOPMD - suppressed LooseCoupling

    /**
     * An optional callback to invoke when the queue has been fully processed and is empty.
     * This is unset as soon as it's called, so it's only ever called once.
     */
    @Nullable
    private Runnable callWhenEmpty;

    /** The single instance of this class. */
    private static DownloadQueue instance;

    /** Private constructor to prevent instantiation except via getInstance(). */
    private DownloadQueue() {
        String downloadThreadsStr = System.getProperty("download_threads");
        int threads = "auto".equalsIgnoreCase(downloadThreadsStr)
                ? Runtime.getRuntime().availableProcessors()
                : Integer.parseInt(downloadThreadsStr);  // already validated

        // "threads" threads max (1-50), wait 5s before killing idle threads, don't retain any idle threads
        this.pool = new ThreadPoolExecutor(threads, threads, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        this.pool.allowCoreThreadTimeOut(true);

        boolean downloadOldestFirst = Boolean.parseBoolean(System.getProperty("download_oldest_first"));
        this.downloadComparator = new DownloadComparator(downloadOldestFirst);
    }
}
