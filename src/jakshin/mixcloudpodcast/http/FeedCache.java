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

package jakshin.mixcloudpodcast.http;

import jakshin.mixcloudpodcast.Main;
import jakshin.mixcloudpodcast.mixcloud.MixcloudFeed;
import java.util.HashMap;

/**
 * A cache for Mixcloud feeds.
 */
class FeedCache {
    /**
     * Gets the instance of this singleton class.
     * @return The FeedCache instance.
     */
    static synchronized FeedCache getInstance() {
        if (FeedCache.instance == null) {
            FeedCache.instance = new FeedCache();
        }

        return FeedCache.instance;
    }

    /**
     * Gets a feed from the cache.
     * Returns null if a feed with the given title isn't in the cache.
     *
     * @param feedTitle The title of the feed to retrieve from the cache.
     * @return The feed, or null if no such feed was cached.
     */
    synchronized MixcloudFeed getFromCache(String feedTitle) {
        CachedFeedInfo existing = this.cachedFeeds.get(feedTitle);

        if (existing != null) {
            if ((System.currentTimeMillis() - existing.cachedTime) / 1000 < this.cacheTimeSeconds) {
                return existing.feed;
            }
            else {
                // eject the expired feed from the cache
                this.cachedFeeds.remove(feedTitle);
            }
        }

        return null;
    }

    /**
     * Adds a feed to the cache.
     * @param feed The feed to cache.
     */
    synchronized void addToCache(MixcloudFeed feed) {
        CachedFeedInfo info = new CachedFeedInfo(feed, System.currentTimeMillis());
        this.cachedFeeds.put(feed.title, info);
    }

    /**
     * Holds info about a cached MixcloudFeed instance.
     */
    private static class CachedFeedInfo {
        /**
         * Creates a new instance of the class.
         *
         * @param feed The feed to be cached.
         * @param cachedTime When the feed was cached.
         */
        CachedFeedInfo(MixcloudFeed feed, long cachedTime) {
            this.feed = feed;
            this.cachedTime = cachedTime;
        }

        /** The cached feed. */
        final MixcloudFeed feed;

        /** When the feed was cached, as milliseconds since 1/1/1970 12:00am UTC. */
        final long cachedTime;
    }

    /** The cached feeds. */
    private final HashMap<String,CachedFeedInfo> cachedFeeds = new HashMap<>(16);

    /** How long to cache feeds for, in seconds. */
    private final int cacheTimeSeconds;

    /** The single instance of this class. */
    private static FeedCache instance = null;

    /** Private constructor to prevent instantiation except via getInstance(). */
    private FeedCache() {
        String cacheTimeSecondsStr = Main.config.getProperty("http_cache_time_seconds");
        this.cacheTimeSeconds = Integer.parseInt(cacheTimeSecondsStr);  // already validated
    }
}
