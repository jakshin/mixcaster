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

package jakshin.mixcaster.podcast;

import jakshin.mixcaster.http.ServableFile;
import jakshin.mixcaster.utils.DateFormatter;
import jakshin.mixcaster.utils.ResourceLoader;
import jakshin.mixcaster.utils.XmlEntities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;

/**
 * A representation of a podcast, which can be serialized to RSS XML.
 * XML format is based on info at https://help.apple.com/itc/podcasts_connect/#/itcb54353390.
 */
public final class Podcast {
    /**
     * Timestamp of when this object was created and populated,
     * i.e. the time at which its data was known to be current.
     */
    public final long createdAt = System.currentTimeMillis();

    /** The Mixcloud user whose info is embedded in this podcast. */
    public String userID;

    /** The podcast's title. */
    public String title;

    /** The podcast's web page's URL (for human consumption, not the URL of the RSS XML). */
    public URI link;

    /** A description of the podcast (4000 chars max for Apple Podcasts). */
    public String description;

    /** The podcast's overall author/owner (individual episodes may have a different author). */
    public String iTunesAuthorAndOwnerName;

    /** The URL of a cover image for the podcast. */
    public URI iTunesImageUrl;

    /** Podcast episodes. */
    public final List<PodcastEpisode> episodes = new LinkedList<>();

    /**
     * Creates an XML string containing the podcast's RSS feed, including all of its episodes.
     * @return RSS XML.
     */
    @NotNull
    public String createXml() throws IOException {
        // load our template resources, if we haven't already
        synchronized (Podcast.class) {
            if (podcastXmlTemplate == null)
                podcastXmlTemplate = ResourceLoader.loadResourceAsText("podcast/podcast.xml", 1000);
            if (episodeXmlTemplate == null)
                episodeXmlTemplate = ResourceLoader.loadResourceAsText("podcast/podcastEpisode.xml", 1000);
        }

        // podcast properties
        String p = podcastXmlTemplate.toString();
        p = this.replaceTemplateTag(p, "{{podcast.title}}", this.title);
        p = this.replaceTemplateTag(p, "{{podcast.link}}", this.link.toASCIIString());
        p = this.replaceTemplateTag(p, "{{podcast.description}}", this.description.replace("\r\n", "\n"));

        p = this.replaceTemplateTag(p, "{{podcast.iTunesAuthorAndOwnerName}}", this.iTunesAuthorAndOwnerName);
        p = this.replaceTemplateTag(p, "{{podcast.iTunesImageUrl}}", this.iTunesImageUrl.toASCIIString());

        // episodes
        StringBuilder episodeXml = new StringBuilder(2048 * this.episodes.size());

        for (PodcastEpisode episode : this.episodes) {
            StringBuilder episodeTitle = new StringBuilder(episode.title.length() + 50);
            episodeTitle.append(episode.title);

            var file = new ServableFile(episode.enclosureUrl.toString());
            if (! file.exists()) {
                episodeTitle.append(" [DOWNLOADING, CAN'T PLAY YET]");
            }

            String e = episodeXmlTemplate.toString();
            e = this.replaceTemplateTag(e, "{{episode.title}}", episodeTitle.toString());
            e = this.replaceTemplateTag(e, "{{episode.link}}", episode.link.toASCIIString());
            e = this.replaceTemplateTag(e, "{{episode.enclosureUrl}}", episode.enclosureUrl.toASCIIString());
            e = this.replaceTemplateTag(e, "{{episode.enclosureLength}}", Long.toString(episode.enclosureLengthBytes));
            e = this.replaceTemplateTag(e, "{{episode.enclosureMimeType}}", episode.enclosureMimeType);
            e = this.replaceTemplateTag(e, "{{episode.pubDate}}", DateFormatter.format(episode.pubDate));
            e = this.replaceTemplateTag(e, "{{episode.description}}",
                    episode.description.replace("\r\n", "\n").replace("\n", " \n"));

            e = this.replaceTemplateTag(e, "{{episode.iTunesAuthor}}", episode.iTunesAuthor);
            e = this.replaceTemplateTag(e, "{{episode.iTunesDuration}}", episode.iTunesDuration.toString(),
                                    "<itunes:duration>.*</itunes:duration>");
            e = this.replaceTemplateTag(e, "{{episode.iTunesImageUrl}}", episode.iTunesImageUrl.toASCIIString(),
                                    "<itunes:image .* />");

            episodeXml.append(e);
        }

        p = p.replace("{{episodes}}", episodeXml.toString().trim());
        return p;
    }

    /**
     * Utility function which replaces a template tag with the appropriate value.
     * If the replacement value is null, the template is left in place.
     *
     * @param template The string which holds the XML template.
     * @param tag The template tag to replace.
     * @param replacement The value to replace the template tag with.
     * @return The new version of the XML template.
     */
    @NotNull
    private String replaceTemplateTag(@NotNull String template, @NotNull String tag,
                                      @Nullable String replacement) {

        return this.replaceTemplateTag(template, tag, replacement, null);
    }

    /**
     * Utility function which replaces a template tag with the appropriate value.
     * If the replacement value is null, the template is left in place.
     *
     * @param template The string which holds the XML template.
     * @param tag The template tag to replace.
     * @param replacement The value to replace the template tag with.
     * @param removeIfNull A regex string to remove from the template if the replacement is null (optional).
     * @return The new version of the XML template.
     */
    @NotNull
    private String replaceTemplateTag(@NotNull String template, @NotNull String tag,
                                      @Nullable String replacement, @Nullable String removeIfNull) {

        if (replacement != null) {
            return template.replace(tag, this.xmlEntities.escape(replacement));
        }

        if (removeIfNull != null && !removeIfNull.isBlank()) {
            return template.replaceAll(removeIfNull, "");
        }

        return template;
    }

    /** The thingy which can escape XML entities. */
    private final XmlEntities xmlEntities = new XmlEntities();

    /** The XML template for a podcast's overall feed. */
    private static StringBuilder podcastXmlTemplate;

    /** The XML template for a single episode of a podcast. */
    private static StringBuilder episodeXmlTemplate;
}
