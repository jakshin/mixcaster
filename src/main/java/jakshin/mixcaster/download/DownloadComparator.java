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

import java.io.Serializable;
import java.util.Comparator;

/**
 * A thing which can compare two download objects for sorting,
 * based on their respective last-modified dates.
 */
class DownloadComparator implements Comparator<Download>, Serializable {
    /**
     * Creates a new instance of the class.
     * @param oldestFirst Whether to sort older downloads before newer ones.
     */
    DownloadComparator(boolean oldestFirst) {
        this.oldestFirst = oldestFirst;
    }

    /**
     * Compares its two arguments for order. Returns a negative integer, zero, or a positive integer
     * as the first argument is less than, equal to, or greater than the second.
     *
     * @param d1 One Download instance.
     * @param d2 Another Download instance.
     * @return A value indicating the appropriate sort order.
     */
    @Override
    public int compare(Download d1, Download d2) {
        long d1Time = d1.remoteLastModifiedDate.getTime();
        long d2Time = d2.remoteLastModifiedDate.getTime();

        if (d1Time == d2Time) {
            // d1 and d2 were modified at the same time
            return 0;
        }

        int ret;
        if (d1Time < d2Time) {
            // d1 was modified before d2, and should sort before it
            ret = -1;
        }
        else {
            // d1 was modified after d2, and should sort after it
            ret = 1;
        }

        if (!this.oldestFirst) ret = -ret;
        return ret;
    }

    /** Whether to sort older downloads before newer ones. */
    private final boolean oldestFirst;

    /** Serialization version number. */
    private static final long serialVersionUID = 1L;  // update this whenever the class definition changes
}
