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

package jakshin.mixcloudpodcast.mixcloud;

import jakshin.mixcloudpodcast.Main;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import javax.xml.bind.DatatypeConverter;

/**
 * Decodes data scraped from Mixcloud.
 */
class MixcloudDecoder {
    /**
     * Decodes the "play info" data scraped from Mixcloud.
     * If this stops working http://offliberty.com might still work.
     *
     * @param playInfo The "play info" scraped from Mixcloud's m-play-info attribute.
     * @return A string containing the stream URL, i.e. the URL from which the track can be downloaded.
     */
    public String decode(String playInfo) {
        if (playInfo == null) return null;

        // found in https://github.com/jackyNIX/xbmc-mixcloud-plugin/blob/master/default.py
        String magic = "pleasedontdownloadourmusictheartistswontgetpaid";  // yes, I do feel a little guilty.. :-/

        String decoded = this.base64Decode(playInfo);
        StringBuilder sb = new StringBuilder(decoded.length());

        for (int i = 0; i < decoded.length(); ++i) {
            int n1 = (int) decoded.charAt(i);
            int n2 = (int) magic.charAt(i % magic.length());
            char ch = (char) (n1 ^ n2);
            sb.append(ch);
        }

        // e.g. {"stream_url": "https://stream19.mixcloud.com/c/m4a/64/c/b/2/5/4969-e079-4ce5-8b50-95b7364beedf.m4a", ...}
        String regexStr = Main.config.getProperty("stream_url_regex");
        Pattern re = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE);
        Matcher matcher = re.matcher(sb);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Decodes a Base64-encoded string.
     * This is just a thin convenience wrapper around library functionality.
     *
     * @param base64 The Base64-encoded string.
     * @return The decoded string.
     */
    private String base64Decode(String base64) {
        byte[] bytes = DatatypeConverter.parseBase64Binary(base64);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
