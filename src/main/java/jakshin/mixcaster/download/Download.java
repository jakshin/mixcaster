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

package jakshin.mixcaster.download;

import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.Objects;

/**
 * A single download, either in progress or waiting to start.
 * (Not a record because we exclude remoteUrl when comparing and hashing.)
 */
@SuppressWarnings("ClassCanBeRecord")
public class Download {
    /**
     * Creates a new instance of the class.
     *
     * @param remoteUrl The remote URL to download from.
     * @param remoteLengthBytes The length of the file to be downloaded from the remote URL.
     * @param remoteLastModified When the remote data was last modified, according to the remote server.
     * @param localFilePath The full path of the local file to download to.
     */
    public Download(@NotNull String remoteUrl,
                    long remoteLengthBytes,
                    @NotNull Date remoteLastModified,
                    @NotNull String localFilePath) {

        this.remoteUrl = remoteUrl;
        this.remoteLengthBytes = remoteLengthBytes;
        this.remoteLastModified = new Date(remoteLastModified.getTime());
        this.localFilePath = localFilePath;
    }

    /** The remote URL to download from. */
    @NotNull
    public final String remoteUrl;

    /** The length of the file to be downloaded from the remote URL. */
    public final long remoteLengthBytes;

    /** When the remote data was last modified, according to Mixcloud. */
    @NotNull
    public final Date remoteLastModified;

    /** The full path of the local file to download to. */
    @NotNull
    public final String localFilePath;

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;

        // we don't compare remoteUrl, because Mixcloud stores the same file on many servers;
        // if new properties are added to the class, they should be compared here
        final Download other = (Download) obj;
        return Objects.equals(this.remoteLengthBytes, other.remoteLengthBytes)
            && Objects.equals(this.remoteLastModified, other.remoteLastModified)
            && Objects.equals(this.localFilePath, other.localFilePath);
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        // we don't use remoteUrl, because Mixcloud stores the same file on many servers;
        // if new properties are added to the class, they should be used here
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.remoteLengthBytes);
        hash = 59 * hash + Objects.hashCode(this.remoteLastModified);
        hash = 59 * hash + Objects.hashCode(this.localFilePath);
        return hash;
    }
}
