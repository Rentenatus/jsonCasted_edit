/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Enumeration representing the parse state for EditNode elements during
 * on-the-fly type parsing.
 *
 * <p>
 * This enum is <strong>independent</strong> from {@link EditStatus}, which is used for
 * validation. ParseState tracks the progress of type parsing, while EditStatus
 * tracks validation/assignment results.
 * </p>
 *
 * <p>
 * Each {@code ParseState} constant defines the current state of a node in the
 * parsing pipeline:
 * </p>
 * <ul>
 * <li>{@link #NONE} - node has never been parsed (e.g., after loading)</li>
 * <li>{@link #EDITED} - node has been edited and needs re-parsing</li>
 * <li>{@link #PENDING} - node is in the parse queue waiting for processing</li>
 * <li>{@link #DONE} - node has been successfully parsed</li>
 * </ul>
 *
 * <p>
 * State transitions:
 * </p>
 * <ul>
 * <li>NONE -&gt; EDITED: when node is edited (e.g., setName(), setValue())</li>
 * <li>EDITED -&gt; PENDING: when node is added to the parse queue</li>
 * <li>PENDING -&gt; DONE: when parser successfully processes the node</li>
 * <li>DONE -&gt; EDITED: when node is edited again</li>
 * <li>PENDING -&gt; EDITED: when parser aborts (e.g., due to errors)</li>
 * </ul>
 *
 * @author Janusch Rentenatus
 */
public enum ParseState {

    /**
     * The node has never been parsed (e.g., after loading from file).
     * This is the initial state for all nodes.
     */
    NONE(0, "NONE", "none"),

    /**
     * The node has been edited (e.g., renamed, value changed, child added/removed).
     * It needs to be re-parsed to update its type information.
     */
    EDITED(1, "EDITED", "edited"),

    /**
     * The node is in the parse queue waiting for processing.
     * This state indicates that the parser thread will process it soon.
     */
    PENDING(2, "PENDING", "pending"),

    /**
     * The node has been successfully parsed.
     * Type information is up-to-date.
     */
    DONE(3, "DONE", "done");

    // --- Integer values for each literal ---
    public static final int NONE_VALUE = 0;
    public static final int EDITED_VALUE = 1;
    public static final int PENDING_VALUE = 2;
    public static final int DONE_VALUE = 3;

    /**
     * Internal array of all enumerators.
     */
    private static final ParseState[] VALUES_ARRAY = new ParseState[]{
        NONE, EDITED, PENDING, DONE
    };

    /**
     * Public unmodifiable list of all enumerators.
     */
    public static final List<ParseState> VALUES
            = Collections.unmodifiableList(Arrays.asList(VALUES_ARRAY));

    /**
     * Returns the enumerator with the specified literal string.
     *
     * @param literal the literal string
     * @return matching enumerator, or {@code null} if none found
     */
    public static ParseState get(String literal) {
        for (ParseState result : VALUES_ARRAY) {
            if (result.toString().equals(literal)) {
                return result;
            }
        }
        return null;
    }

    /**
     * Returns the enumerator with the specified name.
     *
     * @param name the name
     * @return matching enumerator, or {@code null} if none found
     */
    public static ParseState getByName(String name) {
        for (ParseState result : VALUES_ARRAY) {
            if (result.getName().equals(name)) {
                return result;
            }
            if (result.getLiteral().equals(name)) {
                return result;
            }
        }
        return null;
    }

    /**
     * Returns the enumerator with the specified integer value.
     *
     * @param value the integer value
     * @return matching enumerator, or {@code null} if none found
     */
    public static ParseState get(int value) {
        switch (value) {
            case NONE_VALUE:
                return NONE;
            case EDITED_VALUE:
                return EDITED;
            case PENDING_VALUE:
                return PENDING;
            case DONE_VALUE:
                return DONE;
            default:
                return null;
        }
    }

    // --- Internal fields ---
    private final int value;
    private final String name;
    private final String literal;

    /**
     * Private constructor for enum constants.
     */
    private ParseState(int value, String literal, String name) {
        this.value = value;
        this.name = name;
        this.literal = literal;
    }

    /**
     * @return integer value of the enumerator
     */
    public int getValue() {
        return value;
    }

    /**
     * @return name of the enumerator
     */
    public String getName() {
        return name;
    }

    /**
     * @return literal string of the enumerator
     */
    public String getLiteral() {
        return literal;
    }

    /**
     * Returns the literal string representation of the enumerator.
     *
     * @return literal string
     */
    @Override
    public String toString() {
        return literal;
    }

    /**
     * Checks if this state indicates that parsing is not yet started or completed.
     *
     * @return true if this is NONE or EDITED, false otherwise
     */
    public boolean needsParsing() {
        return this == NONE || this == EDITED;
    }

    /**
     * Checks if this state indicates that parsing is in progress or pending.
     *
     * @return true if this is PENDING, false otherwise
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * Checks if this state indicates that parsing is complete.
     *
     * @return true if this is DONE, false otherwise
     */
    public boolean isDone() {
        return this == DONE;
    }

    /**
     * Checks if this state indicates that the node has been edited.
     *
     * @return true if this is EDITED, false otherwise
     */
    public boolean isEdited() {
        return this == EDITED;
    }

    /**
     * Checks if this state indicates that parsing has never been attempted.
     *
     * @return true if this is NONE, false otherwise
     */
    public boolean isNone() {
        return this == NONE;
    }

}
