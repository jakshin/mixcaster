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

package jakshin.mixcaster.mixcloud;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the MusicSet record.
 */
@ExtendWith(MockitoExtension.class)
class MusicSetTest {

    @Nested
    @DisplayName("constructor")
    class ConstructorTest {

        @Test
        void allowsPossessiveUsername() {
            var set1 = new MusicSet("jason's", null, null);
            var set2 = new MusicSet("jason’s", null, null);
            var set3 = new MusicSet("jason‘s", null, null);

            assertThat(set1.username()).isEqualTo("jason");
            assertThat(set2.username()).isEqualTo("jason");
            assertThat(set3.username()).isEqualTo("jason");

            var canonical = new MusicSet("jason", null, null);
            assertThat(set1).isEqualTo(canonical);
            assertThat(set2).isEqualTo(canonical);
            assertThat(set3).isEqualTo(canonical);
        }

        @Test
        void throwsIfTheUsernameIsBlank() {
            assertThatThrownBy(() -> new MusicSet("", null, null))
                    .isInstanceOf(MusicSet.InvalidInputException.class);
            assertThatThrownBy(() -> new MusicSet("\t", null, null))
                    .isInstanceOf(MusicSet.InvalidInputException.class);
        }

        @Test
        void allowsValidMusicTypes() {
            // playlists are covered separately below

            var stream = new MusicSet("artist", "stream", null);
            var shows = new MusicSet("artist", "shows", null);
            var favorites = new MusicSet("artist", "favorites", null);
            var history = new MusicSet("artist", "history", null);

            var uploads = new MusicSet("artist", "uploads", null);
            assertThat(uploads).isEqualTo(shows);

            var listens = new MusicSet("artist", "listens", null);
            assertThat(listens).isEqualTo(history);
        }

        @Test
        void throwsIfMusicTypeIsInvalid() {
            // null is an acceptable music type, and means to use the user's default view

            assertThatThrownBy(() -> new MusicSet("artist", "", null))
                    .isInstanceOf(MusicSet.InvalidInputException.class);
            assertThatThrownBy(() -> new MusicSet("artist", "\t", null))
                    .isInstanceOf(MusicSet.InvalidInputException.class);
            assertThatThrownBy(() -> new MusicSet("artist", "blargh", null))
                    .isInstanceOf(MusicSet.InvalidInputException.class);
        }

        @Test
        void  allowsValidPlaylist() {
            var set1 = new MusicSet("artist", "playlist", "some-music");
            var set2 = new MusicSet("artist", "playlists", "some-music");
            assertThat(set2).isEqualTo(set1);
        }

        @Test
        void throwsIfPlaylistIsInvalid() {
            assertThatThrownBy(() -> new MusicSet("artist", "playlist", null))
                    .isInstanceOf(MusicSet.InvalidInputException.class)
                    .hasMessageContaining("Missing");
            assertThatThrownBy(() -> new MusicSet("artist", "playlist", ""))
                    .isInstanceOf(MusicSet.InvalidInputException.class)
                    .hasMessageContaining("Missing");
            assertThatThrownBy(() -> new MusicSet("artist", "playlist", "\t"))
                    .isInstanceOf(MusicSet.InvalidInputException.class)
                    .hasMessageContaining("Missing");

            assertThatThrownBy(() -> new MusicSet("artist", "shows", "some-music"))
                    .isInstanceOf(MusicSet.InvalidInputException.class)
                    .hasMessageContaining("Extra");
        }
    }

    @Nested
    @DisplayName("of()")
    class OfTest {

        @Test
        void throwsIfTheInputListHasTheWrongSize() {
            List<String> empty = List.of();
            assertThatThrownBy(() -> MusicSet.of(empty))
                    .isInstanceOf(MusicSet.InvalidInputException.class)
                    .hasMessageContaining("Wrong number of arguments");

            List<String> tooMany = List.of("one", "two", "three", "four");
            assertThatThrownBy(() -> MusicSet.of(tooMany))
                    .isInstanceOf(MusicSet.InvalidInputException.class)
                    .hasMessageContaining("Wrong number of arguments");
        }

        @Test
        void acceptsMixcloudUrls() {
            MusicSet set = MusicSet.of(List.of("https://www.mixcloud.com/paulvandyk/uploads/"));
            assertThat(set).isEqualTo(new MusicSet("paulvandyk", "shows", null));

            set = MusicSet.of(List.of("https://www.mixcloud.com/paulvandyk/favorites/"));
            assertThat(set).isEqualTo(new MusicSet("paulvandyk", "favorites", null));

            set = MusicSet.of(List.of("https://www.mixcloud.com/paulvandyk/listens/"));
            assertThat(set).isEqualTo(new MusicSet("paulvandyk", "history", null));

            set = MusicSet.of(List.of("https://www.mixcloud.com/newagerage/stream/"));
            assertThat(set).isEqualTo(new MusicSet("newagerage", "stream", null));

            set = MusicSet.of(List.of("https://www.mixcloud.com/ArmadaMusicOfficial/playlists/armada-radio/"));
            assertThat(set).isEqualTo(new MusicSet("ArmadaMusicOfficial", "playlist", "armada-radio"));

            set = MusicSet.of(List.of("https://www.mixcloud.com/maxswineberg/"));  // default view
            assertThat(set).isEqualTo(new MusicSet("maxswineberg", null, null));
        }

        @Test
        void constructs() {
            assertThat(MusicSet.of(List.of("paulvandyk", "shows")))
                    .isEqualTo(new MusicSet("paulvandyk", "shows", null));
            assertThat(MusicSet.of(new ArrayList<>() {{ add("paulvandyk"); add("shows"); add(null); }}))
                    .isEqualTo(new MusicSet("paulvandyk", "shows", null));

            assertThat(MusicSet.of(List.of("paulvandyk", "favorites")))
                    .isEqualTo(new MusicSet("paulvandyk", "favorites", null));

            assertThat(MusicSet.of(List.of("paulvandyk", "history")))
                    .isEqualTo(new MusicSet("paulvandyk", "history", null));

            assertThat(MusicSet.of(List.of("newagerage", "stream")))
                    .isEqualTo(new MusicSet("newagerage", "stream", null));

            assertThat(MusicSet.of(List.of("ArmadaMusicOfficial", "playlist", "armada-radio")))
                    .isEqualTo(new MusicSet("ArmadaMusicOfficial", "playlist", "armada-radio"));

            assertThat(MusicSet.of(List.of("maxswineberg")))  // default view
                    .isEqualTo(new MusicSet("maxswineberg", null, null));
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTest {

        @Test
        void worksForStream() {
            var set = new MusicSet("artist", "stream", null);
            String str = set.toString();

            assertThat(str).isEqualTo("artist's stream");
            assertThat(MusicSet.of(List.of(str.split("\\s")))).isEqualTo(set);
        }

        @Test
        void worksForShows() {
            var set = new MusicSet("artist", "shows", null);
            String str = set.toString();

            assertThat(str).isEqualTo("artist's shows");
            assertThat(MusicSet.of(List.of(str.split("\\s")))).isEqualTo(set);
        }

        @Test
        void worksForFavorites() {
            var set = new MusicSet("artist", "favorites", null);
            String str = set.toString();

            assertThat(str).isEqualTo("artist's favorites");
            assertThat(MusicSet.of(List.of(str.split("\\s")))).isEqualTo(set);
        }

        @Test
        void worksForHistory() {
            var set = new MusicSet("artist", "history", null);
            String str = set.toString();

            assertThat(str).isEqualTo("artist's history");
            assertThat(MusicSet.of(List.of(str.split("\\s")))).isEqualTo(set);
        }

        @Test
        void worksForPlaylist() {
            var set = new MusicSet("artist", "playlist", "some-music");
            String str = set.toString();

            assertThat(str).isEqualTo("artist's playlist some-music");
            assertThat(MusicSet.of(List.of(str.split("\\s")))).isEqualTo(set);
        }

        @Test
        void worksForDefaultView() {
            var set = new MusicSet("artist", null, null);
            String str = set.toString();

            assertThat(str).isEqualTo("artist");
            assertThat(MusicSet.of(List.of(str.split("\\s")))).isEqualTo(set);
        }
    }
}
