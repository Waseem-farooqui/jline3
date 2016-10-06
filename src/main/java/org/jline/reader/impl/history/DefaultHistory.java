/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package org.jline.reader.impl.history;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.utils.Log;

import static org.jline.reader.LineReader.HISTORY_IGNORE;
import static org.jline.reader.impl.ReaderUtils.*;

/**
 * {@link History} using a file for persistent backing.
 * <p/>
 * Implementers should install shutdown hook to call {@link DefaultHistory#save}
 * to save history to disk.
 */
public class DefaultHistory implements History {

    public static final int DEFAULT_HISTORY_SIZE = 500;

    private final List<Entry> itemsToAppend = new LinkedList<>();
    private final LinkedList<Entry> items = new LinkedList<>();

    private LineReader reader;

    private int offset = 0;
    private int index = 0;

    public DefaultHistory() {
    }

    public DefaultHistory(LineReader reader) {
        this.reader = reader;
    }

    private Path getPath() {
        Object obj = reader != null ? reader.getVariables().get(LineReader.HISTORY_FILE) : null;
        if (obj instanceof Path) {
            return (Path) obj;
        } else if (obj instanceof File) {
            return ((File) obj).toPath();
        } else if (obj != null) {
            return Paths.get(obj.toString());
        } else {
            return null;
        }
    }

    @Override
    public void init(LineReader reader) {
        this.reader = reader;
    }

    public void load() {
        Path path = getPath();
        if (path != null) {
            try {
                if (Files.exists(path)) {
                    Log.trace("Loading history from: ", path);
                    try (BufferedReader reader = Files.newBufferedReader(path)) {
                        reader.lines().forEach(l -> {
                            int idx = l.indexOf(':');
                            Instant instant = Instant.ofEpochMilli(Long.parseLong(l.substring(0, idx)));
                            String line = unescape(l.substring(idx + 1));
                            internalAdd(instant, line);
                        });
                    }
                }
            } catch (IOException e) {
                Log.info("Error reloading history file: ", path, e);
            }
        }
    }

    public void purge() {
        offset = 0;
        index = 0;
        items.clear();
        itemsToAppend.clear();

        Path path = getPath();
        if (path != null) {
            try {
                Log.trace("Purging history from: ", path);
                Files.deleteIfExists(path);
            } catch (IOException e) {
                Log.warn("Failed to delete history file: ", path, e);
            }
        }
    }

    public void save() {
        Path path = getPath();
        if (path != null) {
            try {
                Log.trace("Flushing history");
                Files.createDirectories(path.toAbsolutePath().getParent());
                if (reader.isSet(LineReader.Option.HISTORY_APPEND)) {
                    try (BufferedWriter writer = Files.newBufferedWriter(path.toAbsolutePath(),
                                StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
                        for (Entry entry : itemsToAppend) {
                            writer.append(format(entry));
                        }
                    }
                } else {
                    Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), path.getFileName().toString(), ".tmp");
                    try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardOpenOption.WRITE)) {
                        for (Entry entry : this) {
                            writer.append(format(entry));
                        }
                    }
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                Log.debug("Error saving history file: ", path, e);
            }
        }
        itemsToAppend.clear();
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int index() {
        return offset + index;
    }

    public int first() {
        return offset;
    }

    public int last() {
        return first() + size() - 1;
    }

    private String format(Entry entry) {
        return Long.toString(entry.time().toEpochMilli()) + ":" + escape(entry.line()) + "\n";
    }

    public String get(final int index) {
        return items.get(index - offset).line();
    }

    @Override
    public void add(String item) {
        add(Instant.now(), item);
    }

    @Override
    public void add(Instant time, String line) {
        Objects.requireNonNull(time);
        Objects.requireNonNull(line);

        if (getBoolean(reader, LineReader.DISABLE_HISTORY, false)) {
            return;
        }
        if (isSet(reader, LineReader.Option.HISTORY_IGNORE_SPACE) && line.startsWith(" ")) {
            return;
        }
        if (isSet(reader, LineReader.Option.HISTORY_REDUCE_BLANKS)) {
            line = line.trim();
        }
        if (isSet(reader, LineReader.Option.HISTORY_IGNORE_DUPS)) {
            if (!items.isEmpty() && line.equals(items.getLast().line())) {
                return;
            }
        }
        if (matchPatterns(getString(reader, HISTORY_IGNORE, ""), line)) {
            return;
        }
        internalAdd(time, line);
        if (isSet(reader, LineReader.Option.HISTORY_APPEND) && isSet(reader, LineReader.Option.HISTORY_INCREMENTAL)) {
            save();
        }
    }

    protected boolean matchPatterns(String patterns, String line) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < patterns.length(); i++) {
            char ch = patterns.charAt(i);
            if (ch == '\\') {
                ch = patterns.charAt(++i);
                sb.append(ch);
            } else if (ch == ':') {
                sb.append('|');
            } else if (ch == '*') {
                sb.append('.').append('*');
            }
        }
        return line.matches(sb.toString());
    }

    protected void internalAdd(Instant time, String line) {
        Entry entry = new EntryImpl(last() + 1, time, line);
        items.add(entry);
        itemsToAppend.add(entry);
        maybeResize();
    }

    private void maybeResize() {
        while (size() > getInt(reader, LineReader.HISTORY_SIZE, DEFAULT_HISTORY_SIZE)) {
            items.removeFirst();
            offset++;
        }
        index = size();
    }

    public ListIterator<Entry> iterator(int index) {
        return items.listIterator(index - offset);
    }

    public ListIterator<Entry> iterator() {
        return items.listIterator(0);
    }

    private static class EntryImpl
            implements Entry {
        private final int index;
        private final Instant time;
        private final String line;

        public EntryImpl(int index, Instant time, String line) {
            this.index = index;
            this.time = time;
            this.line = line;
        }

        public int index() {
            return index;
        }

        public Instant time() {
            return time;
        }

        public String line() {
            return line;
        }

        @Override
        public String toString() {
            return String.format("%d: %s", index, line);
        }
    }

    //
    // Navigation
    //

    /**
     * This moves the history to the last entry. This entry is one position
     * before the moveToEnd() position.
     *
     * @return Returns false if there were no history iterator or the history
     * index was already at the last entry.
     */
    public boolean moveToLast() {
        int lastEntry = size() - 1;
        if (lastEntry >= 0 && lastEntry != index) {
            index = size() - 1;
            return true;
        }

        return false;
    }

    /**
     * Move to the specified index in the history
     */
    public boolean moveTo(int index) {
        index -= offset;
        if (index >= 0 && index < size()) {
            this.index = index;
            return true;
        }
        return false;
    }

    /**
     * Moves the history index to the first entry.
     *
     * @return Return false if there are no iterator in the history or if the
     * history is already at the beginning.
     */
    public boolean moveToFirst() {
        if (size() > 0 && index != 0) {
            index = 0;
            return true;
        }
        return false;
    }

    /**
     * Move to the end of the history buffer. This will be a blank entry, after
     * all of the other iterator.
     */
    public void moveToEnd() {
        index = size();
    }

    /**
     * Return the content of the current buffer.
     */
    public String current() {
        if (index >= size()) {
            return "";
        }
        return items.get(index).line();
    }

    /**
     * Move the pointer to the previous element in the buffer.
     *
     * @return true if we successfully went to the previous element
     */
    public boolean previous() {
        if (index <= 0) {
            return false;
        }
        index--;
        return true;
    }

    /**
     * Move the pointer to the next element in the buffer.
     *
     * @return true if we successfully went to the next element
     */
    public boolean next() {
        if (index >= size()) {
            return false;
        }
        index++;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : this) {
            sb.append(e.toString()).append("\n");
        }
        return sb.toString();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\n':
                    sb.append('\\');
                    sb.append('n');
                    break;
                case '\\':
                    sb.append('\\');
                    sb.append('\\');
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        return sb.toString();
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\':
                    ch = s.charAt(++i);
                    if (ch == 'n') {
                        sb.append('\n');
                    } else {
                        sb.append(ch);
                    }
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        return sb.toString();
    }

}

