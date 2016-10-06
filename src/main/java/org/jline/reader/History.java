/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package org.jline.reader;

import java.time.Instant;
import java.util.ListIterator;

/**
 * Console history.
 *
 * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.3
 */
public interface History extends Iterable<History.Entry>
{

    /**
     * Initialize the history for the given reader.
     */
    void init(LineReader reader);

    /**
     * Load history.
     */
    void load();

    /**
     * Save history.
     */
    void save();

    /**
     * Purge history.
     */
    void purge();


    int size();

    boolean isEmpty();

    int index();

    int first();

    int last();

    String get(int index);

    void add(String line);

    void add(Instant time, String line);

    //
    // Entries
    //
    
    interface Entry
    {
        int index();

        Instant time();

        String line();
    }

    ListIterator<Entry> iterator(int index);

    ListIterator<Entry> iterator();

    //
    // Navigation
    //

    String current();

    boolean previous();

    boolean next();

    boolean moveToFirst();

    boolean moveToLast();

    boolean moveTo(int index);

    void moveToEnd();
}
