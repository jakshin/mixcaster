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

package jakshin.mixcaster.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple in-memory cache.
 *
 * Expired items are removed from the cache when they're requested,
 * or manually by calling scrub().
 */
public class MemoryCache<K, V> {
    /**
     * Creates a new instance of the class.
     * @param cacheTimeSeconds How long items should be cached before expiring.
     */
    public MemoryCache(int cacheTimeSeconds) {
        this.cacheTimeSeconds = cacheTimeSeconds;
    }

    /**
     * Adds an item to the cache.
     * Overwrites any value previously stored with the given key.
     *
     * @param key The lookup key.
     * @param value The value to cache.
     */
    public synchronized void put(@NotNull K key, @NotNull V value) {
        var item = new CachedItem<V>(System.nanoTime(), value);
        this.items.put(key, item);
    }

    /**
     * Gets a value from the cache, if it's present and hasn't expired.
     * If the item is present but expired, it's quietly removed.
     *
     * @param key The lookup key.
     * @return The cached value, or null.
     */
    @Nullable
    public synchronized V get(@NotNull K key) {
        CachedItem<V> existing = this.items.get(key);

        if (existing != null) {
            long cachedForSeconds = (System.nanoTime() - existing.cachedAt) / 1_000_000_000;

            if (cachedForSeconds <= this.cacheTimeSeconds) {
                return existing.value;
            }
            else {
                // eject the expired item from the cache
                this.items.remove(key);
            }
        }

        return null;
    }

    /**
     * Scrubs any expired items from the cache.
     * @return Whether any expired items were found and removed.
     */
    public synchronized boolean scrub() {
        long now = System.nanoTime();
        long cacheTimeNanoSecs = this.cacheTimeSeconds * 1_000_000_000L;
        return this.items.entrySet().removeIf(entry -> now - entry.getValue().cachedAt > cacheTimeNanoSecs);
    }

    /** How long items should be cached before expiring. */
    private final int cacheTimeSeconds;

    /** Our backing store for cached items. */
    private final Map<K, CachedItem<V>> items = new ConcurrentHashMap<>();

    /** A way to store a timestamp and arbitrary other value together. */
    private static record CachedItem<V>(long cachedAt, V value) { }
}
