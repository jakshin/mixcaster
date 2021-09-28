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

import java.net.URL;

/**
 * The application's version info.
 */
public final class AppVersion {
    /** The application's version, in raw format: "dev" or major.minor.patch. */
    public static final String raw;

    /** The application's version, ready for display, with a leading "v" if major.minor.patch. */
    public static final String display;

    static {
        URL resourceUrl = AppVersion.class.getResource("AppVersion.class");
        raw = (resourceUrl != null && resourceUrl.toString().startsWith("jar:"))
                ? AppVersion.class.getPackage().getImplementationVersion()
                : "dev";  // Running in an IDE, presumably
        display = "dev".equals(raw) ? "(development version)" : "v" + raw;
    }

    /**
     * Private constructor to prevent instantiation.
     * This class's properties are all static, and it shouldn't be instantiated.
     */
    private AppVersion() {
        // nothing here
    }
}
