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

package jakshin.mixcaster.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the AppSettings class.
 */
class AppSettingsTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void loadsDefaultSettingsIntoSystemProperties() throws IOException {
        String propName = "http_port";
        System.clearProperty(propName);
        assertThat(System.getProperty(propName)).isBlank();

        AppSettings.initSettings();

        assertThat(System.getProperty(propName)).isNotBlank();
    }

    @Test
    void doesNotOverrideExistingValidSystemSettings() throws IOException {
        System.setProperty("http_port", "8080");  // valid
        System.setProperty("log_level", "blah");  // invalid

        AppSettings.initSettings();

        assertThat(System.getProperty("http_port")).isEqualTo("8080");  // keep an existing valid setting
        assertThat(System.getProperty("log_level")).isNotEqualTo("blah").isNotBlank();  // override an invalid one
    }

    @Test
    void loadsValidSettingsFromTheSettingsFileIntoSystemProperties() throws IOException {
        Path settingsFile = Path.of("build/classes/java/mixcaster-settings.properties");

        try {
            var settings = new StringBuilder(8192);
            settings.append("download_oldest_first = true\n")
                    .append("episode_max_count = 3\n")
                    .append("music_dir = ~/foo/bar\n")
                    .append("user_agent = anything is valid\n");
            settings.append("log_level = invalid\n")
                    .append("log_max_count = not-a-number\n")
                    .append("http_port = 123\n")
                    .append("log_dir = not a path\n")
                    .append("http_hostname = \t\n");
            Files.writeString(settingsFile, settings);

            AppSettings.initSettings();

            assertThat(System.getProperty("download_oldest_first")).isEqualTo("true");
            assertThat(System.getProperty("episode_max_count")).isEqualTo("3");
            assertThat(System.getProperty("music_dir")).isEqualTo("~/foo/bar");
            assertThat(System.getProperty("user_agent")).isEqualTo("anything is valid");

            assertThat(System.getProperty("log_level")).isNotEqualTo("invalid");
            assertThat(System.getProperty("log_max_count")).isNotEqualTo("not-a-number");
            assertThat(System.getProperty("http_port")).isNotEqualTo("123");
            assertThat(System.getProperty("log_dir")).startsWith("~/");
            assertThat(System.getProperty("http_hostname")).isNotBlank();
        }
        finally {
            Files.deleteIfExists(settingsFile);
        }
    }
}
