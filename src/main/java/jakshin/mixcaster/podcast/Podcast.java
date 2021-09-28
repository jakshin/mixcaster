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

import jakshin.mixcaster.utils.DateFormatter;
import jakshin.mixcaster.utils.FileLocator;
import jakshin.mixcaster.utils.XmlEntities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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

    /** The language the podcast is in. */
    public String language;

    /** A description of the podcast (4000 chars max for Apple Podcasts). */
    public String description;

    /** The podcast's overall author (individual episodes may have a different author). */
    public String iTunesAuthor;

    /** The podcast's category. */
    public String iTunesCategory;

    /** Whether the podcast contains explicit language. */
    public boolean iTunesExplicit;

    /** The URL of a cover image for the podcast. */
    public URI iTunesImageUrl;

    /** The podcast's owner's name. */
    public String iTunesOwnerName;

    /** The podcast's owner's email address. */
    public String iTunesOwnerEmail;

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
                podcastXmlTemplate = this.loadResource("podcast.xml");
            if (episodeXmlTemplate == null)
                episodeXmlTemplate = this.loadResource("podcastEpisode.xml");
        }

        // podcast properties
        String p = podcastXmlTemplate.toString();
        p = this.replaceTemplateTag(p, "{{podcast.title}}", this.title);
        p = this.replaceTemplateTag(p, "{{podcast.link}}", this.link.toASCIIString());
        p = this.replaceTemplateTag(p, "{{podcast.language}}", this.language.replace("_", "-"));
        p = this.replaceTemplateTag(p, "{{podcast.description}}", this.description);

        p = this.replaceTemplateTag(p, "{{podcast.iTunesAuthor}}", this.iTunesAuthor);
        p = this.replaceTemplateTag(p, "{{podcast.iTunesCategory}}", this.iTunesCategory);
        p = this.replaceTemplateTag(p, "{{podcast.iTunesExplicit}}", this.iTunesExplicit ? "yes" : "no");
        p = this.replaceTemplateTag(p, "{{podcast.iTunesImageUrl}}", this.iTunesImageUrl.toASCIIString());
        p = this.replaceTemplateTag(p, "{{podcast.iTunesOwnerName}}", this.iTunesOwnerName);
        p = this.replaceTemplateTag(p, "{{podcast.iTunesOwnerEmail}}", this.iTunesOwnerEmail);

        // episodes
        StringBuilder episodeXml = new StringBuilder(2048 * this.episodes.size());

        for (PodcastEpisode episode : this.episodes) {
            StringBuilder episodeTitle = new StringBuilder(episode.title.length() + 50);
            episodeTitle.append(episode.title);

            String localPath = FileLocator.getLocalPath(episode.enclosureUrl.toString());
            if (! (new File(localPath).exists())) {
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
     * Loads a resource.
     * Used to load the XML templates, which are expected to be UTF-8 encoded.
     *
     * @param resourceName The resource's file name.
     * @return The resource's content.
     */
    @NotNull
    private StringBuilder loadResource(@NotNull String resourceName) throws IOException {
        StringBuilder sb = new StringBuilder(1024);

        try (InputStream in = this.getClass().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IOException("Could not load resource: " + resourceName);
            }

            final char[] buf = new char[1024];

            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                while (true) {
                    int count = reader.read(buf, 0, buf.length);
                    if (count < 0) break;

                    sb.append(buf, 0, count);
                }
            }
        }

        return sb;
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
