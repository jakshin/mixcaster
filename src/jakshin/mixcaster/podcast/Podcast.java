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

import jakshin.mixcaster.entities.XmlEntities;
import jakshin.mixcaster.utils.DateFormatter;
import java.io.*;
import java.net.URL;
import java.util.*;

/**
 * A representation of a podcast, which can be serialized to RSS XML.
 * XML format is based on info at https://itunespartner.apple.com/en/podcasts/overview.
 */
public final class Podcast {
    /**
     * Creates an XML string containing the podcast's RSS feed, including all of its episodes.
     * @return RSS XML.
     */
    public String createXml() {
        // initialize, if we haven't already
        if (this.podcastXmlTemplate == null)
            this.podcastXmlTemplate = this.loadResource("podcast.xml");
        if (this.episodeXmlTemplate == null)
            this.episodeXmlTemplate = this.loadResource("podcastEpisode.xml");

        // podcast properties;
        // none of the links/URLs are expected to require URL-encoding
        String p = this.podcastXmlTemplate.toString();
        p = this.replaceTemplateTag(p, "{{podcast.title}}", this.title);
        p = this.replaceTemplateTag(p, "{{podcast.description}}", this.description);
        p = this.replaceTemplateTag(p, "{{podcast.link}}", this.link.toString());
        p = this.replaceTemplateTag(p, "{{podcast.language}}", this.language.replace("_", "-"));
        p = this.replaceTemplateTag(p, "{{podcast.iTunesAuthor}}", this.iTunesAuthor);
        p = this.replaceTemplateTag(p, "{{podcast.iTunesCategory}}", this.iTunesCategory);
        p = this.replaceTemplateTag(p, "{{podcast.iTunesExplicit}}", this.iTunesExplicit ? "yes" : "no");
        p = this.replaceTemplateTag(p, "{{podcast.iTunesImageUrl}}", this.iTunesImageUrl);
        p = this.replaceTemplateTag(p, "{{podcast.iTunesOwnerName}}", this.iTunesOwnerName);
        p = this.replaceTemplateTag(p, "{{podcast.iTunesOwnerEmail}}", this.iTunesOwnerEmail);

        // episodes;
        // none of the links/URLs are expected to require URL-encoding
        StringBuilder episodeXml = new StringBuilder(1024 * this.episodes.size());

        for (PodcastEpisode episode : this.episodes) {
            String e = this.episodeXmlTemplate.toString();
            e = this.replaceTemplateTag(e, "{{episode.enclosureUrl}}", episode.enclosureUrl.toString());
            e = this.replaceTemplateTag(e, "{{episode.enclosureLength}}", Integer.toString(episode.enclosureLengthBytes));
            e = this.replaceTemplateTag(e, "{{episode.enclosureMimeType}}", episode.enclosureMimeType);
            e = this.replaceTemplateTag(e, "{{episode.link}}", episode.link.toString());
            e = this.replaceTemplateTag(e, "{{episode.pubDate}}", DateFormatter.format(episode.pubDate));
            e = this.replaceTemplateTag(e, "{{episode.title}}", episode.title);
            e = this.replaceTemplateTag(e, "{{episode.iTunesAuthor}}", episode.iTunesAuthor);
            e = this.replaceTemplateTag(e, "{{episode.iTunesSummary}}", episode.iTunesSummary);

            episodeXml.append(e);
        }

        p = p.replace((CharSequence) "{{episodes}}", (CharSequence) episodeXml.toString().trim());
        return p;
    }

    /** The podcast's title. */
    public String title;

    /** A description of the podcast. */
    public String description;

    /**
     * The podcast's web page's URL (for human consumption, not the URL of the RSS XML).
     * Shouldn't require URL-encoding.
     */
    public URL link;

    /** The language the podcast is in. */
    public String language;

    /** The podcast's overall author (individual episodes may have a different author. */
    public String iTunesAuthor;

    /** The podcast's category. */
    public String iTunesCategory;

    /** Whether the podcast contains explicit language. */
    public boolean iTunesExplicit;

    /**
     * The URL of a cover image for the podcast.
     * Shouldn't require URL-encoding.
     */
    public String iTunesImageUrl;

    /** The podcast's owner's name. */
    public String iTunesOwnerName;

    /** The podcast's owner's email address. */
    public String iTunesOwnerEmail;

    /** Podcast episodes. */
    public final List<PodcastEpisode> episodes = new LinkedList<>();

    /**
     * Loads a resource.
     * Used to load the XML templates, which are expected to be UTF-8 encoded.
     *
     * @param resourceName The resource's file name.
     * @return The resource's content.
     */
    private StringBuilder loadResource(String resourceName) {
        StringBuilder sb = new StringBuilder(1024);

        try (InputStream in = this.getClass().getResourceAsStream(resourceName)) {
            final char[] buf = new char[1024];

            try (Reader reader = new InputStreamReader(in, "UTF-8")) {
                while (true) {
                    int count = reader.read(buf, 0, buf.length);
                    if (count < 0) break;

                    sb.append(buf, 0, count);
                }
            }
        }
        catch (IOException ex) {
            // this can only happen if one of the implicit closes fails
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
    private String replaceTemplateTag(String template, String tag, String replacement) {
        if (replacement == null) return template;
        return template.replace((CharSequence) tag, (CharSequence) this.xmlEntities.escape(replacement));
    }

    /** The thingy which can escape XML entities. */
    private final XmlEntities xmlEntities = new XmlEntities();

    /** The XML template for a podcast's overall feed. */
    private StringBuilder podcastXmlTemplate;

    /** The XML template for a single episode of a podcast. */
    private StringBuilder episodeXmlTemplate;
}
