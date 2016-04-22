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

package jakshin.mixcloudpodcast.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Provides date/time formatting in a standard format required by RSS and HTTP.
 */
public class DateFormatter {
    /**
     * Formats the given date.
     *
     * @param date The date to format.
     * @return A formatted date string.
     */
    public static synchronized String format(Date date) {
        return formatter.format(date);
    }

    /** The thingy which actually formats dates. */
    private final static SimpleDateFormat formatter;

    /** Static initialization. */
    static {
        // configure the formatter
        formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /**
     * Private constructor to prevent instantiation.
     * This class's methods are all static, and it shouldn't be instantiated.
     */
    private DateFormatter() {
        // nothing here
    }
}
