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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jakshin.mixcaster.logging.Logging.*;

/**
 * A thingy which understands how to parse HTTP Range headers,
 * and also how to translate a logical range into a specific range of bytes for a given file size.
 */
class ByteRangeParser {
    /**
     * Parses the given Range header into a byte range.
     * The returned range represents the logical range requested,
     * and therefore may have -1 in either start or end, but not both.
     *
     * @param rangeHeader The HTTP Range header's value (expected to be like "bytes=...").
     * @return A logical byte range.
     */
    @Nullable
    ByteRange parse(@NotNull String rangeHeader) throws HttpException {
        if (rangeHeader.isEmpty()) {
            return null;  // no Range header
        }

        if (!rangeHeader.startsWith("bytes=")) {
            logger.log(WARNING, "Unknown range type in Range header: {0}", rangeHeader);
            return null;  // we don't understand the requested range type
        }

        if (rangeHeader.contains(",")) {
            // we don't handle requests for multiple ranges
            throw new HttpException(500, String.format("Unsupported range header: %s", rangeHeader));
        }

        // parse the header
        rangeHeader = rangeHeader.substring("bytes=".length());
        int dashIndex = rangeHeader.indexOf('-');

        if (dashIndex == -1 || rangeHeader.indexOf('-', dashIndex + 1) != -1) {
            logger.log(WARNING, "Invalid Range header: {0}", rangeHeader);
            return null;  // invalid range
        }

        String startStr = rangeHeader.substring(0, dashIndex).trim();
        String endStr = rangeHeader.substring(dashIndex + 1).trim();

        if (startStr.isEmpty() && endStr.isEmpty()) {
            logger.log(WARNING, "Invalid Range header: {0}", rangeHeader);
            return null;  // invalid range, consisting of just "-"
        }

        long start = -1L;
        long end = -1L;

        try {
            if (!startStr.isEmpty()) {
                start = Long.parseLong(startStr);
            }

            if (!endStr.isEmpty()) {
                end = Long.parseLong(endStr);
            }
        }
        catch (NumberFormatException ex) {
            logger.log(WARNING, "Invalid number in Range header: {0}", rangeHeader);
            return null;  // invalid range
        }

        // validate
        if (start > end && end >= 0) {
            // the start of the range must be at or before the end of the range
            logger.log(WARNING, "Invalid Range header: {0}", rangeHeader);
            return null;  // invalid range
        }

        if (start < 0 && end == 0) {
            // ignore "-0"
            logger.log(WARNING, "Invalid Range header: {0}", rangeHeader);
            return null;  // invalid range
        }

        // everything looks good here
        return new ByteRange(start, end);
    }

    /**
     * Translates a logical range as parsed from an HTTP header, which may have -1 in either start or end,
     * into a physical range for a given file size, with specific first and last bytes, neither of which is negative.
     *
     * @param range The range to translate.
     * @param fileSize The file size applicable in this case.
     * @return A translated version of the range.
     */
    @Nullable
    ByteRange translate(@Nullable final ByteRange range, long fileSize) throws HttpException {
        if (range == null) return null;

        if (fileSize == 0) {
            // Apache 2.2 ignores Range request headers when a 0-length file is requested;
            // that seems like it might be contrary to the RFC, but eh, let's do the same anyway
            logger.log(DEBUG, "Ignoring Range header in a request for an empty file");
            return null;
        }

        long start = range.start();
        long end = range.end();

        if (start >= 0) {
            if (start >= fileSize) {
                throw new HttpException(416, "Requested Range Not Satisfiable");
            }

            long fixedEnd = (end == -1 || end >= fileSize) ? fileSize - 1 : end;
            return new ByteRange(start, fixedEnd);
        }
        else if (end >= 0) {
            // end actually contains the number of bytes to retrieve from the end of the file
            long fixedStart = (end < fileSize) ? fileSize - end : 0;
            return new ByteRange(fixedStart, fileSize - 1);
        }
        else {
            // this shouldn't happen, because of validation in parse(), so we treat it as an internal error
            throw new HttpException(500, String.format("Invalid byte range %d-%d", start, end));
        }
    }
}
