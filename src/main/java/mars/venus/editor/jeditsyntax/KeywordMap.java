/*
 * KeywordMap.java - Fast keyword->id map
 * Copyright (C) 1998, 1999 Slava Pestov
 * Copyright (C) 1999 Mike Dillon
 *
 * You may use and modify this package for any purpose. Redistribution is
 * permitted, in both source and binary form, provided that this notice
 * remains intact in all source distributions of this package.
 */

package mars.venus.editor.jeditsyntax;

import mars.venus.editor.jeditsyntax.tokenmarker.Token;

import javax.swing.text.Segment;
import java.util.ArrayList;

/**
 * A <code>KeywordMap</code> is similar to a hashtable in that it maps keys
 * to values. However, the `keys' are Swing {@link Segment}s. This allows lookups of
 * text substrings without the overhead of creating a new string object.
 * <p>
 * This class is used by {@link mars.venus.editor.jeditsyntax.tokenmarker.MIPSTokenMarker}
 * to map keywords to ids.
 *
 * @author Slava Pestov, Mike Dillon
 * @version $Id: KeywordMap.java,v 1.16 1999/12/13 03:40:30 sp Exp $
 */
public class KeywordMap {
    private final KeywordNode[] buckets;
    private boolean ignoreCase;

    /**
     * Creates a new <code>KeywordMap</code>.
     *
     * @param ignoreCase True if keys are case insensitive
     */
    public KeywordMap(boolean ignoreCase) {
        this(ignoreCase, 52);
    }

    /**
     * Creates a new <code>KeywordMap</code>.
     *
     * @param ignoreCase True if the keys are case insensitive
     * @param capacity   The number of `buckets' to create.
     *                   A value of 52 will give good performance for most maps.
     */
    public KeywordMap(boolean ignoreCase, int capacity) {
        this.ignoreCase = ignoreCase;
        buckets = new KeywordNode[capacity];
    }

    /**
     * Looks up a key.
     *
     * @param text   The text segment
     * @param offset The offset of the substring within the text segment
     * @param length The length of the substring
     * @return The token type for the keyword
     */
    public byte lookup(Segment text, int offset, int length) {
        if (length <= 0) {
            return Token.NULL;
        }
        if (text.array[offset] == '%' && length > 1) {
            return Token.MACRO_ARGUMENT; // added 12/12 M. Sekhavat
        }
        KeywordNode node = buckets[getSegmentMapKey(text, offset, length)];
        while (node != null) {
            if (length != node.keyword.length) {
                node = node.next();
                continue;
            }
            if (SyntaxUtilities.regionMatches(ignoreCase, text, offset, node.keyword())) {
                return node.id();
            }
            node = node.next();
        }
        return Token.NULL;
    }

    /**
     * Adds a key-value mapping.
     *
     * @param keyword The key
     * @param id      The value
     */
    public void add(String keyword, byte id) {
        int key = getStringMapKey(keyword);
        buckets[key] = new KeywordNode(keyword.toCharArray(), id, buckets[key]);
    }

    /**
     * Returns true if the keyword map is set to be case insensitive,
     * false otherwise.
     */
    public boolean getIgnoreCase() {
        return ignoreCase;
    }

    /**
     * Sets if the keyword map should be case insensitive.
     *
     * @param ignoreCase True if the keyword map should be case
     *                   insensitive, false otherwise
     */
    public void setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    protected int getStringMapKey(String string) {
        return (Character.toUpperCase(string.charAt(0)) + Character.toUpperCase(string.charAt(string.length() - 1))) % buckets.length;
    }

    protected int getSegmentMapKey(Segment segment, int offset, int length) {
        return (Character.toUpperCase(segment.array[offset]) + Character.toUpperCase(segment.array[offset + length - 1])) % buckets.length;
    }

    private record KeywordNode(char[] keyword, byte id, KeywordNode next) {}

    @Override
    public String toString() {
        // For debugging purposes
        ArrayList<String> keywords = new ArrayList<>();
        for (KeywordNode bucket : this.buckets) {
            while (bucket != null) {
                keywords.add(new String(bucket.keyword));
                bucket = bucket.next;
            }
        }
        return keywords.toString();
    }
}
