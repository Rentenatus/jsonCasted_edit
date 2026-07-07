/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.SimpleEntry;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.JackAttribut;
import java.util.Map;

/**
 * Utility container for immutable command entry types used by edit commands.
 *
 * <p>
 * Each nested entry class stores the data required to execute and undo a
 * specific kind of command operation.</p>
 *
 * @author Jansuch Rentenatus
 */
public final class EditCommandEntry {

    private EditCommandEntry() {
        throw new AssertionError("Utility class");
    }

    /**
     * Immutable entry describing a node position within the tree.
     *
     * <p>
     * This entry is used for add, delete, and move operations. Depending on the
     * command type, {@code snapshot} may contain a subtree copy used for
     * undo/redo reconstruction.</p>
     */
    public static final class MovementEntry extends SimpleEntry {

        /**
         * The ID of the parent node.
         */
        public final long parentEditId;

        /**
         * The leftRange of the parent node, or {@code -1} if not yet known.
         */
        public final long parentLeftRange;

        /**
         * The timesRange of the parent node, or {@code -1} if not yet known.
         */
        public final long parentTimesRange;

        /**
         * The child index inside the parent, or {@code -1} for append
         * semantics.
         */
        public final int index;

        /**
         * Optional snapshot of the affected subtree.
         */
        public EditNodeAbstract snapshot;

        /**
         * Creates a movement entry without a node ID.
         *
         * @param parentEditId the parent node ID
         * @param index the child index
         * @param snapshot an optional subtree snapshot
         */
        public MovementEntry(long parentEditId, int index, EditNodeAbstract snapshot) {
            this(-1, -1, Long.MIN_VALUE, parentEditId, -1, Long.MIN_VALUE, index, snapshot);
        }

        /**
         * Creates a movement entry.
         *
         * @param nodeId the affected node ID
         * @param parentEditId the parent node ID
         * @param index the child index
         * @param snapshot an optional subtree snapshot
         */
        public MovementEntry(long nodeId, long parentEditId, int index, EditNodeAbstract snapshot) {
            this(nodeId, -1, Long.MIN_VALUE, parentEditId, -1, Long.MIN_VALUE, index, snapshot);
        }

        /**
         * Creates a movement entry.
         *
         * @param nodeId the affected node ID
         * @param leftRange value of fast indexing in tree.
         * @param timesRange times to fast indexing in tree if possible.
         * @param parentEditId the parent node ID
         * @param parentLeftRange value of fast indexing in tree.
         * @param parentTimesRange times to fast indexing in tree if possible.
         * @param index the child index
         * @param snapshot an optional subtree snapshot
         */
        public MovementEntry(long nodeId, long leftRange, long timesRange,
                long parentEditId, long parentLeftRange, long parentTimesRange,
                int index, EditNodeAbstract snapshot) {
            super(nodeId, leftRange, timesRange);
            this.parentEditId = parentEditId;
            this.parentLeftRange = parentLeftRange;
            this.parentTimesRange = parentTimesRange;
            this.index = index;
            this.snapshot = snapshot;
        }

        /**
         * Creates a movement entry.
         *
         * @param node what about this node
         * @param parent node.getParent() or null
         * @param index the child index
         */
        public MovementEntry(EditNodeAbstract node, EditNode parent, int index) {
            this(node.getEditId(), // nodeId
                    node.getLeftRange(),
                    node.getTimesRange(),
                    parent == null ? -1 : parent.getEditId(),
                    parent == null ? -1 : parent.getLeftRange(),
                    parent == null ? Long.MIN_VALUE : parent.getTimesRange(),
                    index,
                    node.deepCopy(false) // snapshot
            );
        }
    }

    /**
     * Immutable entry describing a text-based content change of a node.
     *
     * <p>
     * This entry is used for commands such as rename and set-value.</p>
     */
    public static final class ContentEntry extends SimpleEntry {

        /**
         * The previous value.
         */
        public final String oldValue;

        /**
         * The new value.
         */
        public final String newValue;

        /**
         * Creates a content entry.
         *
         * @param nodeId the affected node ID
         * @param oldValue the previous value
         * @param newValue the new value
         */
        public ContentEntry(long nodeId, String oldValue, String newValue) {
            this(nodeId, -1, Long.MIN_VALUE, oldValue, newValue);
        }

        /**
         * Creates a content entry.
         *
         * @param nodeId the affected node ID
         * @param leftRange value of fast indexing in tree.
         * @param timesRange times to fast indexing in tree if possible.
         * @param oldValue the previous value
         * @param newValue the new value
         */
        public ContentEntry(long nodeId, long leftRange, long timesRange, String oldValue, String newValue) {
            super(nodeId, leftRange, timesRange);
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        /**
         * Creates a content entry.
         *
         * @param node what about this node
         * @param oldValue the previous value
         * @param newValue the new value
         */
        public ContentEntry(EditNode node, String oldValue, String newValue) {
            this(node.getEditId(), // nodeId
                    node.getLeftRange(),
                    node.getTimesRange(),
                    oldValue,
                    newValue
            );
        }
    }

    /**
     * Immutable entry describing an attribute change of a node.
     *
     * <p>
     * This entry is used for set-attribute commands. It stores the old and new
     * values for the modified attributes only.</p>
     */
    public static final class AttributeEntry extends SimpleEntry {

        /**
         * The previous attribute values (only for keys that were modified).
         */
        public final Map<String, JackAttribut> oldAttributes;

        /**
         * The new attribute values to set.
         */
        public final Map<String, JackAttribut> newAttributes;

        /**
         * Creates an attribute entry.
         *
         * @param nodeId the affected node ID
         * @param oldAttributes the previous attribute values (only modified
         * keys)
         * @param newAttributes the new attribute values
         */
        public AttributeEntry(long nodeId, Map<String, JackAttribut> oldAttributes, Map<String, JackAttribut> newAttributes) {
            this(nodeId, -1, Long.MIN_VALUE, oldAttributes, newAttributes);
        }

        /**
         * Creates an attribute entry.
         *
         * @param nodeId the affected node ID
         * @param leftRange value of fast indexing in tree
         * @param timesRange times to fast indexing in tree if possible
         * @param oldAttributes the previous attribute values (only modified
         * keys)
         * @param newAttributes the new attribute values
         */
        public AttributeEntry(long nodeId, long leftRange, long timesRange,
                Map<String, JackAttribut> oldAttributes, Map<String, JackAttribut> newAttributes) {
            super(nodeId, leftRange, timesRange);
            this.oldAttributes = oldAttributes;
            this.newAttributes = newAttributes;
        }

        /**
         * Creates an attribute entry.
         *
         * @param node the affected node
         * @param oldAttributes the previous attribute values (only modified
         * keys)
         * @param newAttributes the new attribute values
         */
        public AttributeEntry(EditNode node, Map<String, JackAttribut> oldAttributes, Map<String, JackAttribut> newAttributes) {
            this(node.getEditId(),
                    node.getLeftRange(),
                    node.getTimesRange(),
                    oldAttributes,
                    newAttributes
            );
        }
    }

}
