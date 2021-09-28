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

package jakshin.mixcaster.http;

import jakshin.mixcaster.mixcloud.MixcloudException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static jakshin.mixcaster.logging.Logging.INFO;
import static jakshin.mixcaster.logging.Logging.logger;

/**
 * Base class for all responders, each of which handles a certain type of HTTP request.
 * This exists just to hold some shared code.
 */
class Responder {
    /**
     * Delegates a request to PodcastXmlResponder, if it looks like a Mixcloud URL.
     * (Long ago, I tried redirecting to podcast.xml, but iTunes didn't handle that well.)
     *
     * @param request The incoming HTTP request.
     * @param writer A writer which can be used to output the response.
     * @param out An output stream which can be used to output the response.
     * @return Whether the request was delegated to PodcastXmlResponder.
     */
    boolean delegateToPodcastXmlResponder(@NotNull HttpRequest request, @NotNull Writer writer, @NotNull OutputStream out)
            throws HttpException, InterruptedException, IOException, MixcloudException, TimeoutException, URISyntaxException {

        String path = request.path.substring(1);  // strip the leading slash, to avoid empty string in components[0]
        String[] components = path.split("/");  // trailing empty string not included
        String last = components[components.length - 1];
        String penultimate = (components.length >= 2) ? components[components.length - 2] : "";

        if (
                (components.length == 1 && components[0].indexOf('.') == -1) ||
                (components.length == 2 && nonPlaylistMusicTypes.contains(last)) ||
                (components.length == 3 && ("playlist".equals(penultimate) || "playlists".equals(penultimate)))
        ) {
            logger.log(INFO, "Request path looks Mixcloud-like, delegating: {0}", request.path);

            String xmlUrl = request.path;
            if (xmlUrl.endsWith("/")) xmlUrl = xmlUrl.substring(0, xmlUrl.length() - 1);
            xmlUrl += ".xml";

            var xmlRequest = new HttpRequest(request, xmlUrl);
            new PodcastXmlResponder().respond(xmlRequest, writer, out);
            return true;
        }

        return false;
    }

    /** Protected constructor to prevent instantiation except by subclasses. */
    protected Responder() {
        // nothing here
    }

    /** Music types and their synonyms, excluding playlist/playlists. */
    private static final List<String> nonPlaylistMusicTypes = List.of(
            "stream", "shows", "favorites", "history", "uploads", "listens"
    );
}
