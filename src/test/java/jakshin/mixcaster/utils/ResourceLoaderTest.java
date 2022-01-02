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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the ResourceLoader class.
 */
class ResourceLoaderTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void loadResourceAsTextWorks() throws IOException {
        String resource = "install/launchd-plist.xml";

        // this assumes our current working directory is the project directory
        Path resourcePath = Path.of("src/main/resources/jakshin/mixcaster", resource);

        String expected = Files.readString(resourcePath);
        String actual = ResourceLoader.loadResourceAsText(resource, 600).toString();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void loadResourceAsBytesWorks() throws IOException {
        String resource = "http/favicon.ico";

        // this assumes our current working directory is the project directory
        Path resourcePath = Path.of("src/main/resources/jakshin/mixcaster", resource);

        byte[] expected = Files.readAllBytes(resourcePath);
        byte[] actual = ResourceLoader.loadResourceAsBytes(resource, 16_000);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void throwsExceptionIfResourceDoesNotExist() {
        String resource = "does-not-exist";

        assertThatThrownBy(() -> ResourceLoader.loadResourceAsText(resource, 100))
                .isInstanceOf(IOException.class).hasMessageContaining(resource);

        assertThatThrownBy(() -> ResourceLoader.loadResourceAsBytes(resource, 100))
                .isInstanceOf(IOException.class).hasMessageContaining(resource);
    }
}
