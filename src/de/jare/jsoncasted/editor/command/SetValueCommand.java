/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.command.EditCommand.CommandType;
import de.jare.jsoncasted.editor.command.EditCommandEntry.ContentEntry;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.SimpleEntry;
import java.util.Arrays;

/**
 * Command that sets the value of node(s) in the tree. When executed, the
 * node(s) values are updated. When undone, the previous values are restored.
 *
 * @author Jansuch Rentenatus
 */
public class SetValueCommand extends AbstractEditCommand {

    private final ContentEntry[] entries;

    /**
     * Creates a command to set the value of a single node.
     *
     * @param node the node whose value will be set
     * @param newValue the new value to set
     */
    public SetValueCommand(EditNode node, String newValue) {
        super(CommandType.SET_VALUE);
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        String oldValue = node.getValue();
        this.entries = new ContentEntry[]{
            new ContentEntry(node, oldValue, newValue)
        };
        setDescription("Set value: " + valueText(oldValue) + " -> " + valueText(newValue));
    }

    /**
     * Creates a command to set values for multiple nodes.
     *
     * @param nodes the nodes to update
     * @param newValues the new values for each node
     */
    public SetValueCommand(EditNode[] nodes, String[] newValues) {
        super(CommandType.SET_VALUE);
        if (nodes == null || newValues == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        if (nodes.length != newValues.length) {
            throw new IllegalArgumentException("Arrays must have the same length");
        }
        if (nodes.length == 0) {
            throw new IllegalArgumentException("Arrays cannot be empty");
        }

        this.entries = new ContentEntry[nodes.length];

        for (int i = 0; i < nodes.length; i++) {
            EditNode node = nodes[i];
            if (node == null) {
                throw new IllegalArgumentException("Node at index " + i + " cannot be null");
            }
            String oldValue = node.getValue();
            this.entries[i] = new ContentEntry(
                    node,
                    oldValue,
                    newValues[i]
            );
        }

        if (nodes.length == 1) {
            setDescription("Set value: " + valueText(entries[0].oldValue) + " -> " + valueText(entries[0].newValue));
        } else {
            setDescription("Set values for " + nodes.length + " nodes");
        }
    }

    /**
     * Creates a command from value entries array.
     *
     * @param entries array of value entries
     */
    public SetValueCommand(ContentEntry[] entries) {
        super(CommandType.SET_VALUE);
        if (entries == null || entries.length == 0) {
            throw new IllegalArgumentException("Entries cannot be null or empty");
        }

        this.entries = copyAndValidate(entries);

        if (this.entries.length == 1) {
            setDescription("Set value: " + valueText(this.entries[0].oldValue) + " -> " + valueText(this.entries[0].newValue));
        } else {
            setDescription("Set values for " + this.entries.length + " nodes");
        }
    }

    @Override
    public CommandAvailability check(EditTree tree) {
        if (tree == null) {
            return CommandAvailability.disallowed(
                    "editor.command.tree.missing");
        }

        for (int i = 0; i < entries.length; i++) {
            ContentEntry entry = entries[i];

            EditNode node = tree.findNodeByIdAndRange(entry);
            if (node == null) {
                return CommandAvailability.disallowed(
                        "editor.command.setValue.nodeMissing",
                        Long.toString(entry.nodeId),
                        Integer.toString(i));
            }
        }

        return CommandAvailability.allowed(
                "editor.command.setValue.allowed");
    }

    @Override
    protected CommandResult doExecute(EditTree tree, boolean redoAction) {
        EditNodeAbstract[] updated = new EditNodeAbstract[entries.length];

        for (int i = 0; i < entries.length; i++) {
            ContentEntry entry = entries[i];
            EditNodeAbstract node = tree.findNodeByIdAndRange(entry);
            if (node == null) {
                return disallowed("editor.command.setValue.nodeMissing",
                        new SimpleEntry[]{new SimpleEntry(entry.nodeId, entry.leftRange, entry.timesRange)});
            }

            String newValue = entry.newValue;
            node.setValue(newValue != null ? newValue : "");
            updated[i] = node;
        }

        return new CommandResult(
                this,
                redoAction ? CommandAction.REDO : CommandAction.EXECUTE,
                updated,
                null, // templateEntries
                null,
                null,
                updated, null,
                NO_UPDATE_ACTIONS
        );
    }

    @Override
    public CommandResult doUndo(EditTree tree) {

        EditNodeAbstract[] updated = new EditNodeAbstract[entries.length];

        for (int i = 0; i < entries.length; i++) {
            ContentEntry entry = entries[i];
            EditNodeAbstract node = tree.findNodeByIdAndRange(entry);
            if (node == null) {
                return disallowed("editor.command.setValue.nodeMissing",
                        new SimpleEntry[]{new SimpleEntry(entry.nodeId, entry.leftRange, entry.timesRange)});
            }

            String oldValue = entry.oldValue;
            node.setValue(oldValue != null ? oldValue : "");
            updated[i] = node;
        }

        return new CommandResult(
                this,
                CommandAction.UNDO,
                updated,
                null, // templateEntries
                null,
                null,
                updated, null,
                NO_UPDATE_ACTIONS
        );
    }

    /**
     * Returns a defensive copy of the entries array.
     *
     * @return a copy of the entries array
     */
    public ContentEntry[] getEntries() {
        return Arrays.copyOf(entries, entries.length);
    }

    /**
     * Returns the node IDs of all entries.
     *
     * @return array of node IDs
     */
    public long[] getNodeIds() {
        long[] ids = new long[entries.length];
        for (int i = 0; i < entries.length; i++) {
            ids[i] = entries[i].nodeId;
        }
        return ids;
    }

    /**
     * Returns the old values of all entries.
     *
     * @return array of old values
     */
    public String[] getOldValues() {
        String[] values = new String[entries.length];
        for (int i = 0; i < entries.length; i++) {
            values[i] = entries[i].oldValue;
        }
        return values;
    }

    /**
     * Returns the new values of all entries.
     *
     * @return array of new values
     */
    public String[] getNewValues() {
        String[] values = new String[entries.length];
        for (int i = 0; i < entries.length; i++) {
            values[i] = entries[i].newValue;
        }
        return values;
    }

    private static ContentEntry[] copyAndValidate(ContentEntry[] entries) {
        ContentEntry[] copy = new ContentEntry[entries.length];

        for (int i = 0; i < entries.length; i++) {
            ContentEntry entry = entries[i];
            if (entry == null) {
                throw new IllegalArgumentException("Entry at index " + i + " cannot be null");
            }
            if (entry.nodeId < 0) {
                throw new IllegalArgumentException("Entry nodeId at index " + i + " is invalid");
            }

            copy[i] = new ContentEntry(
                    entry.nodeId,
                    entry.leftRange,
                    entry.timesRange,
                    entry.oldValue,
                    entry.newValue
            );
        }
        return copy;
    }

    private static String valueText(String value) {
        return value != null ? value : "null";
    }
}
