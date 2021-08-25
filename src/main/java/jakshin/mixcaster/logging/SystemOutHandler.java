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

package jakshin.mixcaster.logging;

import java.util.logging.*;

/**
 * A StreamHandler which publishes log records to System.out, without ever closing it.
 * This is like ConsoleHandler, but sends log records to System.out instead of System.err.
 */
class SystemOutHandler extends StreamHandler {
    /**
     * Creates a new instance of the class, with the given formatter and logging level.
     *
     * @param formatter Formatter to be used to format output.
     * @param level The log level specifying which message levels will be logged by this Handler.
     *      Message levels lower than this value will be discarded.
     */
    SystemOutHandler(Formatter formatter, Level level) {
        super(System.out, formatter);
        this.setLevel(level);
    }

    /**
     * Formats and publishes a LogRecord.
     * @param record The log event; a null record is silently ignored and is not published.
     */
    @Override
    public void publish(LogRecord record) {
        super.publish(record);
        this.flush();
    }

    /**
     * Flushes, but does not close, the output stream. That is, we do not close System.out.
     * Overrides StreamHandler.close().
     */
    @Override
    public void close() {
        this.flush();
    }
}
