/*
 * Copyright (c) 2022 Jason Jackson
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

import jakshin.mixcaster.utils.DateFormatter;
import org.jetbrains.annotations.NotNull;

import java.io.StringWriter;
import java.text.ParseException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.fail;

/**
 * Utility functions for use in tests.
 */
public class Utilities {

    @NotNull
    public static Date parseDateHeader(String headerName, String headers) {
        Pattern p = Pattern.compile("\\r\\n" + headerName + ":\\s+[^\\r\\n]+GMT\\r\\n");
        Matcher m = p.matcher(headers);

        if (! m.find()) {
            fail(String.format("Missing the %s response header", headerName));
        }

        int index = m.group().indexOf(':');
        String headerValue = m.group().substring(index + 1).trim();

        try {
            return DateFormatter.parse(headerValue);
        }
        catch (ParseException ex) {
            fail(String.format("Invalid format in the %s response header: %s", headerName, headerValue));
            return new Date();  // we never get here, it's just to keep the compiler happy
        }
    }

    public static void resetStringWriter(@NotNull StringWriter writer) {
        writer.getBuffer().delete(0, writer.getBuffer().length());
    }
}
