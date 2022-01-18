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

package jakshin.mixcaster.download;

import jakshin.mixcaster.TestUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the DownloadOptions class.
 */
class DownloadOptionsTest {
    private Path tempDir;

    @AfterEach
    void tearDown() {
        TestUtilities.removeTempDirectory(tempDir);
    }

    @Test
    void acceptsNullInAllOptions() {
        var opts = new DownloadOptions(null, null, null);

        assertThat(opts.limit()).isNull();
        assertThat(opts.outDirPath()).isNull();
        assertThat(opts.rssPath()).isNull();

        assertThat(DownloadOptions.validate(opts)).isNull();
    }

    @Test
    void acceptsPositiveNonZeroLimitOption() {
        // valid limits
        String max = String.valueOf(Integer.MAX_VALUE);
        List<String> validLimits = List.of("1", max);

        for (String valid : validLimits) {
            var opts = new DownloadOptions(valid, null, null);
            assertThat(opts.limit()).isEqualTo(valid);
        }

        // invalid limits
        String tooBig = String.valueOf(Integer.MAX_VALUE + 1L);
        List<String> invalidLimits = List.of("0", "-1", tooBig, "foo", "");

        for (String invalid : invalidLimits) {
            assertThatThrownBy(() -> new DownloadOptions(invalid, null, null))
                    .isInstanceOf(DownloadOptions.InvalidOptionException.class)
                    .hasMessageContaining("Invalid limit")
                    .hasMessageContaining(invalid);
        }
    }

    @Test
    void expandsOutOption() {
        var relative = new DownloadOptions(null, "foo/bar", null);
        assertThat(relative.outDirPath()).isEqualTo("foo/bar");

        var absolute = new DownloadOptions(null, "/foo/bar", null);
        assertThat(absolute.outDirPath()).isEqualTo("/foo/bar");

        var inHome = new DownloadOptions(null, "~/foo/bar", null);
        assertThat(inHome.outDirPath()).isEqualTo(System.getProperty("user.home") + "/foo/bar");
    }

    @Test
    void expandsRssOption() {
        var here = new DownloadOptions(null, null, "podcast.xml");
        assertThat(here.rssPath()).isEqualTo("podcast.xml");

        var relative = new DownloadOptions(null, null, "foo/podcast.xml");
        assertThat(relative.rssPath()).isEqualTo("foo/podcast.xml");

        var absolute = new DownloadOptions(null, null, "/foo/podcast.xml");
        assertThat(absolute.rssPath()).isEqualTo("/foo/podcast.xml");

        var inHome = new DownloadOptions(null, null, "~/podcast.xml");
        assertThat(inHome.rssPath()).isEqualTo(System.getProperty("user.home") + "/podcast.xml");
    }

    @Test
    void rejectsBlankOutOption() {
        List<String> blanks = List.of("", " ", "\t");

        for (String blank : blanks) {
            assertThatThrownBy(() -> new DownloadOptions(null, blank, null))
                    .isInstanceOf(DownloadOptions.InvalidOptionException.class)
                    .hasMessageContaining("option requires a value");
        }
    }

    @Test
    void acceptsEmptyRssOption() {
        var opts = new DownloadOptions(null, null, "");
        assertThat(opts.rssPath()).isEmpty();
    }

    @Test
    void acceptsAnEmptyListOfOptions() {
        DownloadOptions opts = DownloadOptions.of(List.of());

        assertThat(opts.limit()).isNull();
        assertThat(opts.outDirPath()).isNull();
        assertThat(opts.rssPath()).isNull();

        assertThat(DownloadOptions.validate(opts)).isNull();
    }

    @Test
    void rejectsUnrecognizedOptions() {
        List<String> rejects = List.of("", " ", "\t", "foo", "foo=42");

        for (String reject : rejects) {
            assertThatThrownBy(() -> DownloadOptions.of(List.of(reject)))
                    .isInstanceOf(DownloadOptions.InvalidOptionException.class)
                    .hasMessageContaining("Unrecognized option");
        }
    }

    @Test
    void allowsOptionsMultipleTimes() {
        String str = "-limit -out -rss -limit=1 -out=foo -rss=foo.xml -limit=2 -out=bar -rss=bar.xml";
        List<String> list = List.of(str.split("\\s"));

        var opts = DownloadOptions.of(list);

        assertThat(opts.limit()).isEqualTo("2");
        assertThat(opts.outDirPath()).isEqualTo("bar");
        assertThat(opts.rssPath()).isEqualTo("bar.xml");
    }

    @Test
    void preventsOutAndRssOptionsTogether() {
        var opts = new DownloadOptions(null, "out", "rss.xml");
        assertThat(DownloadOptions.validate(opts)).contains("can't be used together");
    }

    @Test
    void ensuresTheOutputDirectoryExists() throws IOException {
        tempDir = Files.createTempDirectory("mix-dopt-");
        Path notThere = Path.of(tempDir.toString(), "not-there");

        var happy = new DownloadOptions(null, tempDir.toString(), null);
        assertThat(DownloadOptions.validate(happy)).isNull();

        var sad = new DownloadOptions(null, notThere.toString(), null);
        assertThat(DownloadOptions.validate(sad)).contains("Output directory doesn't exist");
        assertThat(DownloadOptions.validate(sad)).contains(notThere.toString());
    }

    @Test
    void ensuresTheRssOutputDirectoryExists() throws IOException {
        tempDir = Files.createTempDirectory("mix-dopt-");
        Path podcastInTempDir = Path.of(tempDir.toString(), "podcast.xml");
        Path notThere = Path.of(tempDir.toString(), "not-there");
        Path podcastNotThere = Path.of(notThere.toString(), "podcast.xml");
        List<String> accepts = List.of("", "podcast.xml", "./podcast.xml", podcastInTempDir.toString());

        for (String accept : accepts) {
            var opts = new DownloadOptions(null, null, accept);
            assertThat(DownloadOptions.validate(opts)).isNull();
        }

        var sad = new DownloadOptions(null, null, podcastNotThere.toString());
        assertThat(DownloadOptions.validate(sad)).contains("RSS output directory doesn't exist");
        assertThat(DownloadOptions.validate(sad)).contains(notThere.toString());
    }
}