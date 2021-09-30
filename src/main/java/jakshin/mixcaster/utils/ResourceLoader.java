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

import jakshin.mixcaster.Main;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * A thing that loads resources.
 */
public final class ResourceLoader {
    /**
     * Loads a text resource, which is expected to be UTF-8 encoded.
     *
     * @param resourcePath The path and name of the resource, e.g. "http/foo.txt".
     * @param capacityNeeded The capacity needed to hold the resource's text in memory.
     *                       The impact of setting this too low is extra memory allocation+copy cycles.
     * @return The resource's content.
     */
    @NotNull
    public static StringBuilder loadResourceAsText(@NotNull String resourcePath,
                                                   int capacityNeeded) throws IOException {

        StringBuilder sb = new StringBuilder(capacityNeeded);

        try (InputStream in = Main.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Could not load resource: " + resourcePath);
            }

            int bufSize = Math.min(8192, capacityNeeded);
            final char[] buf = new char[bufSize];

            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                while (true) {
                    int count = reader.read(buf, 0, buf.length);
                    if (count < 0) break;

                    sb.append(buf, 0, count);
                }
            }
        }

        return sb;
    }

    /**
     * Loads a raw resource, as a byte array.
     *
     * @param resourcePath The path and name of the resource, e.g. "http/foo.bin".
     * @param capacityNeeded The capacity needed to hold the resource's bytes in memory.
     *                       The impact of setting this too low is extra memory allocation+copy cycles.
     * @return The resource's content.
     */
    @NotNull
    public static byte[] loadResourceAsBytes(@NotNull String resourcePath,
                                             int capacityNeeded) throws IOException {

        ByteArrayOutputStream ba = new ByteArrayOutputStream(capacityNeeded);

        try (InputStream in = Main.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Could not load resource: " + resourcePath);
            }

            int bufSize = Math.min(8192, capacityNeeded);
            final byte[] buf = new byte[bufSize];

            while (true) {
                int count = in.read(buf);
                if (count < 0) break;

                ba.write(buf, 0, count);
            }
        }

        return ba.toByteArray();
    }

    /**
     * Private constructor to prevent instantiation.
     * This class's methods are all static, and it shouldn't be instantiated.
     */
    private ResourceLoader() {
        // nothing here
    }
}
