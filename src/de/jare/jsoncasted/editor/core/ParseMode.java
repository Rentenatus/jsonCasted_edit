/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.jsoncasted.editor.core;

/**
 * Parse mode of an EditTree. Controls how the on-the-fly type parser
 * reacts to node changes.
 *
 * <ul>
 * <li>{@link #WITHOUT_SEMANTICS} - the parser sleeps; edits do not trigger
 * any type parsing, exactly like a tree without a model.</li>
 * <li>{@link #SOFT_PARSE} - nodes are parsed asynchronously by the
 * background parser service (on-the-fly).</li>
 * <li>{@link #HARD_PARSE} - every change is parsed synchronously before
 * the mutating call returns; the tree is always in a verified state.</li>
 * </ul>
 */
public enum ParseMode {

    /**
     * No type parsing at all, like a tree without a model.
     */
    WITHOUT_SEMANTICS("without semantics"),

    /**
     * Background parsing of edited nodes (on-the-fly).
     */
    SOFT_PARSE("soft parse"),

    /**
     * Synchronous parsing after every change.
     */
    HARD_PARSE("hard parse");

    private final String literal;

    private ParseMode(String literal) {
        this.literal = literal;
    }

    /**
     * Returns the display literal of this mode.
     *
     * @return the display literal
     */
    public String getLiteral() {
        return literal;
    }

    /**
     * Returns the display literal of this mode, used by combo boxes.
     *
     * @return the display literal
     */
    @Override
    public String toString() {
        return literal;
    }

    /**
     * Returns the mode with the given display literal.
     *
     * @param literal the display literal
     * @return the matching mode, or {@code null} if none matches
     */
    public static ParseMode fromLiteral(String literal) {
        for (ParseMode mode : values()) {
            if (mode.literal.equals(literal)) {
                return mode;
            }
        }
        return null;
    }
}
