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
 * A log-record formatter geared towards output to System.out.
 * It tries to present logged info in a way which is readable in a console.
 */
class SystemOutFormatter extends Formatter {
    /**
     * Formats the given log record and returns the formatted string.
     *
     * @param record The log record to be formatted.
     * @return A string containing the formatted log record.
     */
    @Override
    public String format(LogRecord record) {
        String msg = this.formatMessage(record);
        if (msg == null || msg.isEmpty()) return msg;

        Level level = record.getLevel();
        String prefix = (level == Level.WARNING) ? "Warning: " : "";

        msg = String.format("%s%s%n", prefix, msg.trim());

        if (level == Level.SEVERE) {
            StringBuilder sb = new StringBuilder(500);
            sb.append("Error: ");
            sb.append(msg);

            Throwable thrown = record.getThrown();
            if (thrown != null) {
                sb.append(this.formatThrowable(thrown, false));
                sb.append(String.format("%n"));
            }

            return sb.toString();
        }

        return msg;
    }

    /**
     * Formats a Throwable, including the exception class name and a stack trace,
     * and returns the formatted string.
     *
     * @param ex The Throwable to format.
     * @param isCause Whether this Throwable is the cause of another.
     * @return A string containing the formatted Throwable.
     */
    private String formatThrowable(Throwable ex, boolean isCause) {
        StringBuilder sb = new StringBuilder(500);

        String msg = ex.getMessage();
        if (msg != null) {
            msg = msg.trim();
        }

        String prefix = (isCause) ? "Caused by: " : "";
        sb.append(String.format("%n%s%s: %s%n", prefix, ex.getClass().getCanonicalName(), msg));

        StackTraceElement[] stack = ex.getStackTrace();
        for (StackTraceElement el : stack) {
            sb.append(String.format("    at %s%n", el.toString()));
        }

        Throwable cause = ex.getCause();
        if (cause != null) {
            sb.append(this.formatThrowable(cause, true));
        }

        return sb.toString();
    }
}
