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

import org.jetbrains.annotations.NotNull;
import java.io.Serial;

/**
 * An HTTP exception that occurred while trying to handle a request for podcast RSS.
 */
class PodcastHttpException extends HttpException {
    /**
     * Creates a new instance.
     * @param explanation An explanation of what went wrong. Visible to the user.
     */
    PodcastHttpException(@NotNull String explanation) {
        super(404, "Not Found");
        this.explanation = explanation;
    }

    /**
     * Creates a new instance.
     * @param explanation An explanation of what went wrong. Visible to the user.
     * @param cause The cause of the problem.
     */
    PodcastHttpException(@NotNull String explanation, @NotNull Throwable cause) {
        super(404, "Not Found", cause);
        this.explanation = explanation;
    }

    /**
     * Gets an explanation of what went wrong.
     * @return The explanation, ready to display.
     */
    @NotNull
    String getExplanation() {
        return explanation;
    }

    /** An explanation of what went wrong. */
    private final String explanation;

    /** Serialization version number.
        Update this whenever the class definition changes. */
    @Serial
    private static final long serialVersionUID = 1L;
}
