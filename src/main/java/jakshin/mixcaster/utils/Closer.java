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

import java.io.Closeable;
import java.io.IOException;

import static jakshin.mixcaster.logging.Logging.WARNING;
import static jakshin.mixcaster.logging.Logging.logger;

/**
 * A safe, boilerplate-reducing way to close things that might need closing.
 */
public final class Closer {
    /**
     * Convenience method which closes something that can be closed.
     *
     * It's a good idea to manually flush instances of Flushable before calling this,
     * to reduce the likelihood of an IOException occurring here.
     *
     * @param thing The thing to be closed.
     * @param description A description of the thing which will be closed.
     */
    public static void close(@Nullable Closeable thing, @NotNull String description) {
        if (thing == null) return;

        try {
            thing.close();
        }
        catch (IOException ex) {
            logger.log(WARNING, ex, () -> String.format("Failed to close %s", description));
        }
    }

    /**
     * Private constructor to prevent instantiation.
     * This class's methods are all static, and it shouldn't be instantiated.
     */
    private Closer() {
        // nothing here
    }
}
