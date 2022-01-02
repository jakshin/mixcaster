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

package jakshin.mixcaster.stale.attributes;

import jakshin.mixcaster.mixcloud.MusicSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the WatchesAttr class.
 */
class WatchesAttrTest {
    Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile("mix-wat-", null);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void works() throws IOException {
        var attr = new WatchesAttr(tempFile);
        assertThat(attr.isSupported()).isTrue();
        assertThat(attr.exists()).isFalse();

        List<MusicSet> sets = new ArrayList<>();
        sets.add(new MusicSet("foo", null, null));
        attr.setValue(sets);
        assertThat(attr.exists()).isTrue();

        List<MusicSet> value = attr.getValue();
        assertThat(value).isEqualTo(sets);

        sets.add(new MusicSet("bar's", "shows", null));
        sets.add(new MusicSet("baz", "playlist", "some-music"));
        attr.setValue(sets);

        value = attr.getValue();
        assertThat(value).isEqualTo(sets);
    }
}
