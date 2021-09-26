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

package jakshin.mixcaster.http;

/**
 * A range of bytes.
 */
record ByteRange(long start, long end) {
    /**
     * Creates a new instance of the class.
     *
     * @param start The start of the range, inclusive, 0-indexed.
     * @param end   The end of the range, inclusive, 0-indexed.
     */
    ByteRange {
    }

    /**
     * Gets the size of the range, or -1 if either the start or end is not specified.
     * @return The size of the range.
     */
    long size() {
        if (this.start == -1 || this.end == -1) {
            return -1;
        }

        return (end - start) + 1;
    }

}
