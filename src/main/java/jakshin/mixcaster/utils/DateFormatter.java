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
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Provides date/time formatting in a standard format required by RSS and HTTP.
 */
public final class DateFormatter {
    /**
     * Formats the given date.
     *
     * Note that the date/time format used by this class doesn't include milliseconds,
     * so formatting a date with this function will effectively discard that information.
     *
     * @param date The date to format.
     * @return A formatted date string, or an empty string if the date is null.
     */
    @NotNull
    public static String format(@Nullable Date date) {
        if (date == null) {
            return "";
        }

        synchronized (formatter) {
            return formatter.format(date);
        }
    }

    /**
     * Parses the given string into a Date object.
     *
     * Note that the date/time format understood by this class doesn't include milliseconds,
     * so passing a Date through format() and then parse() will result in a date/time
     * which has been truncated to the second.
     *
     * @param dateStr The date/time string to parse.
     * @return A date object matching the parsed string.
     */
    @NotNull
    public static Date parse(@NotNull String dateStr) throws ParseException {
        synchronized (formatter) {
            return formatter.parse(dateStr);
        }
    }

    /** The thingy which actually formats and parses dates. */
    private final static SimpleDateFormat formatter;

    static {
        // configure the formatter
        var sdf = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        formatter = sdf;
    }

    /**
     * Private constructor to prevent instantiation.
     * This class's methods are all static, and it shouldn't be instantiated.
     */
    private DateFormatter() {
        // nothing here
    }
}
