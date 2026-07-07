/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

/**
 * Immutable entry class that stores node identification information for tree
 * operations. Contains the node ID, left range, and times range used for node
 * lookup and reference.
 *
 * @author Janusch Rentenatus
 */
public class SimpleEntry {

    /**
     * The ID of the affected node, or {@code -1} if not yet known.
     */
    public long nodeId;

    /**
     * The leftRange of the affected node, or {@code -1} if not yet known.
     */
    public long leftRange;

    /**
     * The timesRange of the affected node, or {@code Long.MIN_VALUE} if not yet
     * known.
     */
    public long timesRange;

    /**
     * Creates a new abstract entry with the specified node ID, leftRange, and
     * timesRange. The node ID represents the unique identifier of the affected
     * node in the tree, while the leftRange and timesRange provide information
     * about the position and structure of the node within the tree.
     *
     *
     * @param nodeId the ID of the affected node, or {@code -1} if not yet known
     * @param leftRange the leftRange of the affected node, or {@code -1} if not
     * yet known
     * @param timesRange the timesRange of the affected node, or
     * {@code Long.MIN_VALUE} if not yet known
     */
    public SimpleEntry(long nodeId, long leftRange, long timesRange) {
        this.nodeId = nodeId;
        this.leftRange = leftRange;
        this.timesRange = timesRange;
    }

    public SimpleEntry(EditNode node) {
        this.nodeId = node.getEditId();
        this.leftRange = node.getLeftRange();
        this.timesRange = node.getTimesRange();
    }
}
