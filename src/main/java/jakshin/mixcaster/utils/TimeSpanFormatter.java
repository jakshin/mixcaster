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

package jakshin.mixcaster.utils;

import org.jetbrains.annotations.NotNull;

/**
 * Provides formatting of time spans in an easily-readable format.
 */
public final class TimeSpanFormatter {
    /**
     * Formats a span of time, given in seconds, as h:mm:ss or m:ss (0:ss if less than one minute).
     *
     * @param seconds The time span in seconds.
     * @return A formatted representation of the time span.
     */
    @NotNull
    public static String formatTimeSpan(int seconds) {
        if (seconds < 0) {
            return "-" + formatTimeSpan(-seconds);
        }
        
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

    /**
     * Private constructor to prevent instantiation.
     * This class's methods are all static, and it shouldn't be instantiated.
     */
    private TimeSpanFormatter() {
        // nothing here
    }
}
