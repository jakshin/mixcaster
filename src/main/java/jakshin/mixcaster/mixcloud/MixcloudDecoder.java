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

package jakshin.mixcaster.mixcloud;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Decodes Mixcloud data.
 */
class MixcloudDecoder {
    /**
     * Decodes an encoded URL returned by Mixcloud's GraphQL server.
     *
     * @param encoded The encoded URL, from a "streamInfo".
     * @return The decoded URL.
     */
    @NotNull
    String decodeUrl(@NotNull String encoded) {
        String decoded = this.base64Decode(encoded);
        StringBuilder sb = new StringBuilder(decoded.length());

        for (int i = 0; i < decoded.length(); ++i) {
            int n1 = decoded.charAt(i);
            int n2 = KEY.charAt(i % KEY.length());
            sb.append((char) (n1 ^ n2));
        }

        return sb.toString();
    }

    /**
     * Decodes a Base64-encoded string.
     * This is just a thin convenience wrapper around library functionality.
     *
     * @param base64 The Base64-encoded string.
     * @return The decoded string.
     */
    @NotNull
    private String base64Decode(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * From https://github.com/ytdl-org/youtube-dl/blob/master/youtube_dl/extractor/mixcloud.py
     * and https://github.com/jackyNIX/xbmc-mixcloud-plugin/blob/master/default.py.
     */
    private static final String KEY = "IFYOUWANTTHEARTISTSTOGETPAIDDONOTDOWNLOADFROMMIXCLOUD";
}
