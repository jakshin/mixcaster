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

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * The application's settings. Defaults are hard-coded in this class, which can be overridden
 * by valid values in system properties and/or mixcaster-settings.properties.
 */
public final class AppSettings {
    /**
     * Initializes the application's settings, placing them into system properties.
     * @return A list of settings validation failures.
     */
    @NotNull
    public static List<String> initSettings() throws IOException {
        // load our default settings into system properties,
        // without overriding any settings that are already present and valid
        Properties systemProps = System.getProperties();
        validateProperties(systemProps);

        for (String propertyName : defaults.stringPropertyNames()) {
            if (! systemProps.containsKey(propertyName)) {
                systemProps.setProperty(propertyName, defaults.getProperty(propertyName));
            }
        }

        // load our settings file into system properties, skipping invalid values
        // (if we don't find the settings file, we'll silently carry on with default settings)
        Properties settings = loadSettings();
        validateProperties(settings);

        for (String propertyName : settings.stringPropertyNames()) {
            systemProps.setProperty(propertyName, settings.getProperty(propertyName));
        }

        return Collections.unmodifiableList(validationFailures);
    }

    /**
     * Loads settings from mixcaster-settings.properties, if we find it next to the jar file.
     * @return A Properties object containing settings, which haven't been validated.
     */
    @NotNull
    private static Properties loadSettings() throws IOException {
        Properties settings = new Properties(defaults.size());

        Path path = Paths.get(AppSettings.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        Path propsPath = Paths.get(path.getParent().toString(), "mixcaster-settings.properties");

        if (Files.exists(propsPath)) {
            try (InputStream in = Files.newInputStream(propsPath)) {
                settings.load(in);
            }
        }

        return settings;
    }

    /** Our default settings. */
    private static final Properties defaults = new Properties(16);

    static {
        // there should be a 1-to-1 correspondence between values here and in the properties file,
        // and each value here should also have a line in validateProperties()
        defaults.setProperty("download_oldest_first", "false");   // must be "true" or "false"
        defaults.setProperty("download_threads", "auto");         // must be "auto" or an int in [1-50]
        defaults.setProperty("episode_max_count", "25");          // must be an int > 0
        defaults.setProperty("http_cache_time_seconds", "3600");  // must be an int >= 0
        defaults.setProperty("http_hostname", "localhost");
        defaults.setProperty("http_port", "6499");                // must be an int in [1024-65535]
        defaults.setProperty("log_dir", "~/Library/Logs/Mixcaster");
        defaults.setProperty("log_level", "ALL");
        defaults.setProperty("log_max_count", "10");              // must be an int > 0, values above 1000 are lowered
        defaults.setProperty("music_dir", "~/Music/Mixcloud");
        defaults.setProperty("remove_stale_music_files_after_days", "0");  // int >= 0, 0 means disabled
        defaults.setProperty("subscribed_to", "");                // whitespace-delimited list of usernames, empty is OK
        defaults.setProperty("user_agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)" +
                " AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        defaults.setProperty("watch_interval_minutes", "20");     // minutes, must be an int > 0
    }

    /**
     * Validates the properties in the given Properties object.
     * @param properties The Properties object.
     */
    private static void validateProperties(@NotNull Properties properties) {
        validateEnum(properties, "download_oldest_first", new String[] {"true", "false"});
        if (! "auto".equalsIgnoreCase(properties.getProperty("download_threads")))
            validateInteger(properties, "download_threads", 1, 50);
        validateInteger(properties, "episode_max_count", 1, Integer.MAX_VALUE);
        validateInteger(properties, "http_cache_time_seconds", 0, Integer.MAX_VALUE);
        validateString(properties, "http_hostname");
        validateInteger(properties, "http_port", 1024, 65535);
        validatePath(properties, "log_dir");
        validateEnum(properties, "log_level", new String[] {"ERROR", "WARNING", "INFO", "DEBUG", "ALL"});
        validateInteger(properties, "log_max_count", 1, Integer.MAX_VALUE);
        validatePath(properties, "music_dir");
        // subscribed_to can contain any string or be empty
        validateInteger(properties,"remove_stale_music_files_after_days", 0, Integer.MAX_VALUE);
        validateString(properties, "user_agent");
        validateInteger(properties, "watch_interval_minutes", 1, Integer.MAX_VALUE);

        // keep log_max_count from being too high (FileHandler allocates an array of this size)
        if (properties.containsKey("log_max_count") &&
                Integer.parseInt(properties.getProperty("log_max_count")) > 1000) {
            properties.setProperty("log_max_count", "1000");
        }
    }

    /**
     * Validates that the given property contains one of the given valid values,
     * removing it from the properties object if it does not.
     *
     * @param properties The Properties object.
     * @param propertyName The name of the property to check.
     * @param validValues A list of valid potential values.
     */
    private static void validateEnum(@NotNull Properties properties,
                                     @NotNull String propertyName,
                                     @NotNull String[] validValues) {

        String value = properties.getProperty(propertyName);

        for (String validValue : validValues) {
            if (validValue.equalsIgnoreCase(value)) {
                return;  // this is a valid value
            }
        }

        properties.remove(propertyName);
        if (value != null && !value.isBlank()) {
            String msg = String.format("Property %s has an invalid value: %s", propertyName, value);
            validationFailures.add(msg);
        }
    }

    /**
     * Validates that the given property's value represents an integer within the given range,
     * removing it from the properties object if it does not.
     *
     * @param properties The Properties object.
     * @param propertyName The name of the property to check.
     * @param minValue The minimum allowed value.
     * @param maxValue The maximum allowed value.
     */
    private static void validateInteger(@NotNull Properties properties,
                                        @NotNull String propertyName,
                                        int minValue,
                                        int maxValue) {
        String value = null;

        try {
            value = properties.getProperty(propertyName);
            int num = Integer.parseInt(value);

            if (minValue <= num && num <= maxValue) {
                return;  // things are A-OK
            }

            // the number parses, but it's out of range
        }
        catch (NumberFormatException ex) {
            // fall through
        }

        properties.remove(propertyName);
        if (value != null && !value.isBlank()) {
            String msg = String.format("Integer property %s is invalid or out of range: %s", propertyName, value);
            validationFailures.add(msg);
        }
    }

    /**
     * Validates that the given property's value plausibly represents a path,
     * removing it from the properties object if it does not.
     *
     * @param properties The Properties object.
     * @param propertyName The name of the property to check.
     */
    private static void validatePath(@NotNull Properties properties,
                                     @NotNull String propertyName) {

        String value = properties.getProperty(propertyName);
        if (value != null && (value.startsWith("/") || value.startsWith("~/"))) {
            return;  // valid enough to run with
        }

        properties.remove(propertyName);
        if (value != null && !value.isBlank()) {
            String msg = String.format("Property %s has an invalid value: %s", propertyName, value);
            validationFailures.add(msg);
        }
    }

    /**
     * Validates that the given property contains a string that isn't empty,
     * removing it from the properties object if it does not.
     *
     * @param properties The Properties object.
     * @param propertyName The name of the property to check.
     */
    private static void validateString(@NotNull Properties properties,
                                       @NotNull String propertyName) {

        String value = properties.getProperty(propertyName);
        if (value != null && !value.isBlank()) {
            return;  // valid
        }

        properties.remove(propertyName);
    }

    /**
     * Validation failures encountered while checking existing system properties
     * and/or settings in our properties file.
     */
    private static final List<String> validationFailures = new LinkedList<>();

    /**
     * Private constructor to prevent instantiation.
     * This class's properties are all static, and it shouldn't be instantiated.
     */
    private AppSettings() {
        // nothing here
    }
}
