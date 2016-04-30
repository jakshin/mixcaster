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

package jakshin.mixcaster.entities;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Escapes and unescapes XML entities.
 */
public class XmlEntities {
    /**
     * Escapes XML entities in the passed string, returning the result as a new string.
     *
     * @param str The string to escape.
     * @return The escaped string.
     */
    public String escape(String str) {
        if (str == null) return null;

        // this probably doesn't perform very well,
        // but it doesn't matter in our limited use case with small strings
        for (Map.Entry<String,String> entry : charToEntity.entrySet()) {
            str = str.replaceAll(entry.getKey(), entry.getValue());
        }

        return str;
    }

    /**
     * Unescapes XML entities in the passed string, returning the result as a new string.
     *
     * @param str The string to unescape.
     * @return The unescaped string.
     */
    public String unescape(String str) {
        if (str == null) return null;

        for (Map.Entry<String,String> entry : entityToChar.entrySet()) {
            str = str.replaceAll(entry.getKey(), entry.getValue());
        }

        return str;
    }

    /**
     * XML entities (character -> entity).
     * Used for escaping.
     */
    private static final Map<String,String> charToEntity = new LinkedHashMap<>(5); {
        // unescaped character -> escaped entity
        charToEntity.put("&",  "&amp;");  // must be first
        charToEntity.put("<",  "&lt;");
        charToEntity.put(">",  "&gt;");
        charToEntity.put("\"", "&quot;");
        charToEntity.put("'",  "&apos;");
    }

    /**
     * XML entities (entity -> character).
     * Used for unescaping.
     */
    private static final Map<String,String> entityToChar = new LinkedHashMap<>(5); {
        // escaped entity -> unescaped character
        entityToChar.put("&lt;",   "<");
        entityToChar.put("&gt;",   ">");
        entityToChar.put("&quot;", "\"");
        entityToChar.put("&apos;", "'");
        entityToChar.put("&amp;",  "&");  // must be last
    }
}
