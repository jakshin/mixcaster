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

package jakshin.mixcloudpodcast.download;

import java.util.Date;
import java.util.Objects;

/**
 * A single download, either in progress or waiting to start.
 */
public class Download {
    /**
     * Creates a new instance of the class.
     *
     * @param remoteUrl The remote URL to download from.
     * @param remoteLengthBytes The length of the file to be downloaded from the remote URL.
     * @param remoteLastModifiedDate When the remote data was last modified, according to the remote server.
     * @param localFile The full path of the local file to download to.
     */
    public Download(String remoteUrl, int remoteLengthBytes, Date remoteLastModifiedDate, String localFile) {
        this.remoteUrl = remoteUrl;
        this.remoteLengthBytes = remoteLengthBytes;
        this.remoteLastModifiedDate = new Date(remoteLastModifiedDate.getTime());
        this.localFile = localFile;
    }

    /** The remote URL to download from. */
    public final String remoteUrl;

    /** The length of the file to be downloaded from the remote URL. */
    public final int remoteLengthBytes;

    /** When the remote data was last modified, according to Mixcloud. */
    public final Date remoteLastModifiedDate;

    /** The full path of the local file to download to. */
    public final String localFile;

    /**
     * Indicates whether some other object is "equal to" this one.
     * @return The other object.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;

        // if new properties are added to the class, they should be compared here
        final Download other = (Download) obj;
        if (!Objects.equals(this.remoteUrl, other.remoteUrl)) return false;
        if (!Objects.equals(this.remoteLengthBytes, other.remoteLengthBytes)) return false;
        if (!Objects.equals(this.remoteLastModifiedDate, other.remoteLastModifiedDate)) return false;
        if (!Objects.equals(this.localFile, other.localFile)) return false;

        return true;
    }

    /**
     * Returns a hash code value for the object.
     * @return A hash code value for this object.
     */
    @Override
    public int hashCode() {
        // if new properties are added to the class, they should be used here
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.remoteUrl);
        hash = 59 * hash + Objects.hashCode(this.remoteLengthBytes);
        hash = 59 * hash + Objects.hashCode(this.remoteLastModifiedDate);
        hash = 59 * hash + Objects.hashCode(this.localFile);
        return hash;
    }
}
