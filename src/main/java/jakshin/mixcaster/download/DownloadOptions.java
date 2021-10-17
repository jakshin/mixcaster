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

package jakshin.mixcaster.download;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Options available for command-line downloads.
 *
 * @param limit The -limit option. If non-null, it'll contain a positive non-zero integer.
 * @param outDirPath The -out option, containing a path to a directory (or null).
 * @param rssPath The -rss option, containing a path/filename. Null means don't write RSS,
 *                blank means write it to the default filename.
 */
public record DownloadOptions(@Nullable String limit, @Nullable String outDirPath, @Nullable String rssPath) {
    /**
     * Creates a new instance.
     */
    public DownloadOptions {
        if (limit != null) {
            checkLimitOption(limit);
        }

        outDirPath = expandPathOption("-out", outDirPath, false);
        rssPath = expandPathOption("-rss", rssPath, true);
    }

    /**
     * Creates a new instance.
     * @param optionArgs Arguments containing options, formatted like "-foo" or "-foo=bar".
     */
    @Contract("_ -> new")
    @NotNull
    public static DownloadOptions of(@NotNull List<String> optionArgs) throws InvalidOptionException {
        String limit = null, out = null, rss = null;

        for (String opt : optionArgs) {
            String[] parts = opt.split("=", 2);
            String name = parts[0];
            String value = (parts.length == 2) ? parts[1] : "";

            switch (name) {
                case "-limit" -> limit = value;
                case "-out" -> out = value;
                case "-rss" -> rss = value;
                default -> throw new InvalidOptionException("Unrecognized option: " + name);
            }
        }

        return new DownloadOptions(limit, out, rss);
    }

    /**
     * Validates options in detail, including their interactions with each other.
     *
     * @param opts The DownloadOptions instance to be validated.
     * @return A string noting a problem with the options, or null if they're A-OK.
     */
    @Nullable
    public static String validate(@NotNull DownloadOptions opts) {
        if (opts.outDirPath != null) {
            if (opts.rssPath != null) {
                // if we're downloading to an arbitrary directory, outside our server's content root,
                // i.e. the music_dir, there aren't valid URLs for the files, that we can put in the RSS
                return "Sorry, the -out and -rss options can't be used together";
            }

            if (! Files.isDirectory(Paths.get(opts.outDirPath))) {
                return "Output directory doesn't exist: " + opts.outDirPath;
            }
        }

        String separator = System.getProperty("file.separator");
        if (opts.rssPath != null && opts.rssPath.contains(separator)) {
            Path rssDirPath = Paths.get(opts.rssPath).getParent();

            if (! Files.isDirectory(rssDirPath)) {
                return "RSS output directory doesn't exist: " + rssDirPath;
            }
        }

        return null;
    }

    /**
     * Something's wrong with an option.
     */
    public static class InvalidOptionException extends RuntimeException {
        /**
         * Constructs a new exception with the specified detail message.
         * @param message The detail message (which is saved for later retrieval by the Throwable.getMessage() method).
         */
        InvalidOptionException(@NotNull String message) {
            super(message);
        }

        /**
         * Constructs a new exception with the specified detail message and cause.
         * @param message The detail message (which is saved for later retrieval by the Throwable.getMessage() method).
         * @param cause The cause (which is saved for later retrieval by the Throwable.getCause() method).
         *              (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
         */
        InvalidOptionException(@NotNull String message, @Nullable Throwable cause) {
            super(message, cause);
        }

        /** Serialization version number.
            This should be updated whenever the class definition changes. */
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * Ensures the string parses as a positive, non-zero integer.
     * @param value The string to check.
     */
    private void checkLimitOption(@NotNull String value) throws InvalidOptionException {
        try {
            int val = Integer.parseInt(value);
            if (val <= 0) {
                throw new InvalidOptionException("Invalid limit: " + value);
            }
        }
        catch (NumberFormatException ex) {
            throw new InvalidOptionException("Invalid limit: [" + value + "]", ex);
        }
    }

    /**
     * Ensures the given -out or -rss option value is valid, and expands its leading tilde if needed.
     *
     * @param name The option's name.
     * @param value The option's value.
     * @param allowBlank Whether the option can have a blank value.
     * @return The validated, expanded option value.
     */
    private String expandPathOption(@NotNull String name, @Nullable String value, boolean allowBlank)
            throws InvalidOptionException {

        if (value == null) return null;

        if (value.isBlank() && !allowBlank) {
            String msg = String.format("The %s option requires a value", name);
            throw new InvalidOptionException(msg);
        }

        if (value.startsWith("~/")) {
            // expand tilde so -foo=~/bar will work (the shell won't expand that tilde itself)
            value = System.getProperty("user.home") + value.substring(1);
        }

        return value;
    }
}
