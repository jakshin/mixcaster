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

/**
 * A representation of a byte range, which has at least a start OR an end, and maybe both.
 * The range is inclusive.
 */
class ByteRange {
    /**
     * Creates a new instance of the class by parsing the given Range request header.
     *
     * @param byteRangeHeader An HTTP Range request header. Expected to start with "bytes=".
     * @throws NumberFormatException
     */
    ByteRange(String rangeHeader) throws HttpException, NumberFormatException {
        if (rangeHeader == null || rangeHeader.isEmpty()) {
            return;  // no Range header; that's okay, just leave both start and end null
        }

        // abort if we don't support the range requested
        if (!rangeHeader.startsWith("bytes=")) {
            // we don't understand the requested range type
            throw new HttpException(500, String.format("Unexpected range header: %s", rangeHeader));
        }

        if (rangeHeader.contains(",")) {
            // we don't handle requests for multiple ranges
            throw new HttpException(500, String.format("Unsupported range header: %s", rangeHeader));
        }

        // parse the header
        rangeHeader = rangeHeader.substring("bytes=".length());
        int dashIndex = rangeHeader.indexOf('-');

        if (dashIndex == -1 || rangeHeader.indexOf('-', dashIndex + 1) != -1) {
            // invalid range
            throw new HttpException(500, String.format("Invalid range header: %s", rangeHeader));
        }

        String startStr = rangeHeader.substring(0, dashIndex);
        String endStr = rangeHeader.substring(dashIndex + 1);

        if (!startStr.isEmpty()) {
            this.start = new Long(startStr);
        }

        if (!endStr.isEmpty()) {
            this.end = new Long(endStr);
        }

        // validate
        if (this.start != null && this.end != null && this.start.longValue() > this.end.longValue()) {
            // the start of the range must be before the end of the range;
            // we allow it to be equal, i.e. retrieving just one byte
            throw new HttpException(500, String.format("Invalid range header: %s", rangeHeader));
        }
    }

    /** The first byte desired, 0-indexed, or null if the range only specified the last byte desired. */
    Long start;

    /** The last byte desired, 0-indexed, or null if the range only specified the first byte desired. */
    Long end;
}
