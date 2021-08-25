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

import java.io.*;
import java.nio.charset.Charset;

/**
 * Utility methods related to files.
 * All methods are static.
 */
public final class FileUtils {
    /**
     * Reads the entire contents of file into a string, using the given character set.
     * This should only be used on smallish files which contain text.
     *
     * @param fileName The file to read from.
     * @param charset The character set to use while reading the file.
     * @return The contents of the file.
     * @throws IOException
     * @throws SecurityException
     */
    public static String readFileIntoString(String fileName, String charset)
            throws IOException, SecurityException {
        if (fileName.isEmpty()) {
            throw new FileNotFoundException("(No such file or directory)");
        }

        File file = new File(fileName);
        StringBuilder sb;

        try (Reader reader = new InputStreamReader(new FileInputStream(file), charset)) {
            sb = new StringBuilder((int) file.length());
            final char[] buf = new char[50_000];

            while (true) {
                int charCount = reader.read(buf, 0, buf.length);
                if (charCount < 0) break;

                sb.append(buf, 0, charCount);
            }

            // the Reader is automatically closed;
            // the FileInputStream is closed by the Reader
        }

        return sb.toString();
    }

    /**
     * Writes a string to a file, replacing any existing contents, using the platform's default character set.
     *
     * @param fileName The file to write to, replacing if it already exists.
     * @param str The string to write to the file.
     * @param charset The character set to use while writing the file.
     * @throws IOException
     * @throws SecurityException
     */
    public static void writeStringToFile(String fileName, String str, String charset)
            throws IOException, SecurityException {
        File file = new File(fileName);

        if (!Charset.isSupported(charset)) {
            // check the charset and throw an exception if it isn't supported, before we open the output file,
            // so we don't overwrite an existing file and/or leave it on disk when OutputStreamWriter gets mad
            throw new UnsupportedEncodingException(charset);
        }

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), charset)) {
            writer.write(str);

            // the Writer is automatically closed, which flushes it first;
            // the FileOutputStream is closed by the Writer
        }
    }

    /**
     * Private constructor to prevent instantiation.
     * This class's methods are all static, and it shouldn't be instantiated.
     */
    private FileUtils() {
        // nothing here
    }
}
