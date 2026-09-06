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
 * Enumeration representing edit status states for EditNode elements.
 *
 * <p>
 * Each {@code EditStatus} constant defines the validation/assignment state of a node:
 * </p>
 * <ul>
 * <li>{@link #STATELESS} - no specific edit state (default)</li>
 * <li>{@link #OKAY} - node is valid and type assignment successful</li>
 * <li>{@link #WARNING} - node has potential issues but is functional</li>
 * <li>{@link #ERROR} - node has critical validation or assignment errors</li>
 * </ul>
 *
 * <p>
 * Utility methods are provided to retrieve an enumerator by literal string, by name, or by integer value. A public
 * unmodifiable list of all values is also available via {@link #VALUES}.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public enum EditStatus {

    /**
     * No specific edit state - node is stateless.
     */
    STATELESS(0, "STATELESS", "stateless"),
    
    /**
     * Node is valid and type assignment was successful.
     */
    OKAY(1, "OKAY", "okay"),
    
    /**
     * Node has potential issues but is functional.
     */
    WARNING(2, "WARNING", "warning"),
    
    /**
     * Node has critical validation or assignment errors.
     */
    ERROR(3, "ERROR", "error");

    // --- Integer values for each literal ---
    public static final int STATELESS_VALUE = 0;
    public static final int OKAY_VALUE = 1;
    public static final int WARNING_VALUE = 2;
    public static final int ERROR_VALUE = 3;

    /**
     * Internal array of all enumerators.
     */
    private static final EditStatus[] VALUES_ARRAY = new EditStatus[]{
        STATELESS, OKAY, WARNING, ERROR
    };

    /**
     * Public unmodifiable list of all enumerators.
     */
    public static final List<EditStatus> VALUES
            = Collections.unmodifiableList(Arrays.asList(VALUES_ARRAY));

    /**
     * Returns the enumerator with the specified literal string.
     *
     * @param literal the literal string
     * @return matching enumerator, or {@code null} if none found
     */
    public static EditStatus get(String literal) {
        for (EditStatus result : VALUES_ARRAY) {
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
    public static EditStatus getByName(String name) {
        for (EditStatus result : VALUES_ARRAY) {
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
    public static EditStatus get(int value) {
        switch (value) {
            case STATELESS_VALUE:
                return STATELESS;
            case OKAY_VALUE:
                return OKAY;
            case WARNING_VALUE:
                return WARNING;
            case ERROR_VALUE:
                return ERROR;
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
    private EditStatus(int value, String literal, String name) {
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
     * Checks if this status indicates an error state.
     *
     * @return true if this is ERROR, false otherwise
     */
    public boolean isError() {
        return this == ERROR;
    }

    /**
     * Checks if this status indicates a warning state.
     *
     * @return true if this is WARNING, false otherwise
     */
    public boolean isWarning() {
        return this == WARNING;
    }

    /**
     * Checks if this status indicates a valid/okay state.
     *
     * @return true if this is OKAY, false otherwise
     */
    public boolean isOkay() {
        return this == OKAY;
    }

    /**
     * Checks if this status indicates a stateless state.
     *
     * @return true if this is STATELESS, false otherwise
     */
    public boolean isStateless() {
        return this == STATELESS;
    }

} // EditStatus
