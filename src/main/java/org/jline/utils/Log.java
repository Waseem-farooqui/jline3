/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package org.jline.utils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Internal logger.
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
 * @since 2.0
 */
public final class Log
{
    public static void trace(final Object... messages) {
        log(Level.FINEST, messages);
    }

    public static void debug(final Object... messages) {
        log(Level.FINE, messages);
    }

    public static void info(final Object... messages) {
        log(Level.INFO, messages);
    }

    public static void warn(final Object... messages) {
        log(Level.WARNING, messages);
    }

    public static void error(final Object... messages) {
        log(Level.SEVERE, messages);
    }

    /**
     * Helper to support rendering messages.
     */
    static void render(final PrintStream out, final Object message) {
        if (message.getClass().isArray()) {
            Object[] array = (Object[]) message;

            out.print("[");
            for (int i = 0; i < array.length; i++) {
                out.print(array[i]);
                if (i + 1 < array.length) {
                    out.print(",");
                }
            }
            out.print("]");
        }
        else {
            out.print(message);
        }
    }

    static void log(final Level level, final Object... messages) {
        Logger logger = Logger.getLogger("org.jline");
        Throwable cause = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        for (int i = 0; i < messages.length; i++) {
            // Special handling for the last message if its a throwable, render its stack on the next line
            if (i + 1 == messages.length && messages[i] instanceof Throwable) {
                cause = (Throwable) messages[i];
            }
            else {
                render(ps, messages[i]);
            }
        }
        ps.close();
        LogRecord r = new LogRecord(level, baos.toString());
        r.setThrown(cause);
        logger.log(r);
    }

}