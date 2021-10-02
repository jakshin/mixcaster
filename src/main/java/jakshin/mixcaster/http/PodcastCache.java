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

package jakshin.mixcaster.http;

import jakshin.mixcaster.podcast.Podcast;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cache for podcast objects we've generated, so if they're requested again
 * in a short period of time, we don't need to re-generate them.
 */
final class PodcastCache {
    /**
     * Gets the instance of this singleton class.
     * @return The PodcastCache instance.
     */
    @NotNull
    static synchronized PodcastCache getInstance() {
        if (PodcastCache.instance == null) {
            PodcastCache.instance = new PodcastCache();
        }

        return PodcastCache.instance;
    }

    /**
     * Gets a podcast from the cache.
     * Returns null if a matching podcast isn't in the cache.
     */
    @Nullable
    synchronized Podcast getFromCache(@NotNull String username, @NotNull String musicType, @Nullable String playlist) {
        String key = buildCacheKey(username, musicType, playlist);
        Podcast existing = this.cached.get(key);

        if (existing != null) {
            int cacheTimeSeconds = Integer.parseInt(System.getProperty("http_cache_time_seconds"));

            // using a wall-clock timestamp instead of a monotonic clock like System.nanoTime() here,
            // because we also use it to handle If-Modified-Since, and using multiple timestamps seems overkill,
            // since over/under-caching isn't a big deal in this case
            if ((System.currentTimeMillis() - existing.createdAt) / 1000 < cacheTimeSeconds) {
                return existing;
            }
            else {
                // eject the expired podcast from the cache
                this.cached.remove(key);
            }
        }

        return null;
    }

    /**
     * Adds a podcast to the cache.
     */
    synchronized void addToCache(@NotNull String username, @NotNull String musicType, @Nullable String playlist,
                                 @NotNull Podcast podcastToCache) {
        String key = buildCacheKey(username, musicType, playlist);
        this.cached.put(key, podcastToCache);
    }

    /**
     * Builds a cache key using the username, and music type or playlist name.
     */
    private String buildCacheKey(@NotNull String username, @NotNull String musicType, @Nullable String playlist) {
        String detail = (musicType.equals("playlist")) ? playlist : musicType;
        return String.format("%s's %s", username, detail);
    }

    /** The cached podcasts. */
    private final Map<String,Podcast> cached = new ConcurrentHashMap<>();

    /** The single instance of this class. */
    private static PodcastCache instance;

    /** Private constructor to prevent instantiation except via getInstance(). */
    private PodcastCache() {
        // nothing here
    }
}
