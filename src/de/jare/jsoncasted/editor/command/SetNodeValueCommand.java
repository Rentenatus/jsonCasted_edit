/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.command.EditCommand.CommandType;
import de.jare.jsoncasted.editor.command.EditCommandEntry.ContentEntry;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.SimpleEntry;

/**
 * Command that sets the value of node(s) in the tree. When executed, the node value is updated
 * and the parse machinery is notified - the soft parse mode queues the node for the background
 * parser, the hard parse mode validates synchronously. When undone, the previous value is
 * restored. Primitive values are shape-conform by nature, so a value change is legitimate in
 * both parse modes; the parser judges the content (a text in a number field gets flagged).
 *
 * @author Janusch Rentenatus
 */
public class SetNodeValueCommand extends AbstractEditCommand {

    private final ContentEntry[] entries;

    /**
     * Creates a command to set the value of a single node.
     *
     * @param node the node to edit
     * @param newValue the new value, may be {@code null} (clears the value)
     */
    public SetNodeValueCommand(EditNode node, String newValue) {
        super(CommandType.SET_VALUE);
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }

        String oldValue = node.getValue();
        this.entries = new ContentEntry[]{
            new ContentEntry(node, oldValue, newValue)
        };

        setDescription("Set value: " + text(oldValue) + " -> " + text(newValue));
    }

    /**
     * Creates a command to set the values of multiple nodes.
     *
     * @param nodes the nodes to edit
     * @param newValues the new values
     */
    public SetNodeValueCommand(EditNode[] nodes, String[] newValues) {
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

            this.entries[i] = new ContentEntry(
                    node,
                    node.getValue(),
                    newValues[i]
            );
        }

        setDescription("Set " + nodes.length + " values");
    }

    @Override
    public CommandAvailability check(EditTree tree) {
        if (tree == null) {
            return CommandAvailability.disallowed(
                    "editor.command.tree.missing");
        }

        for (int i = 0; i < entries.length; i++) {
            ContentEntry entry = entries[i];

            if (tree.findNodeByIdAndRange(entry) == null) {
                return CommandAvailability.disallowed(
                        "editor.command.setvalue.nodeMissing",
                        Long.toString(entry.nodeId),
                        Integer.toString(i));
            }
        }

        return CommandAvailability.allowed(
                "editor.command.setvalue.allowed");
    }

    @Override
    protected CommandResult doExecute(EditTree tree, boolean redoAction) {
        EditNodeAbstract[] updated = new EditNodeAbstract[entries.length];

        for (int i = 0; i < entries.length; i++) {
            ContentEntry entry = entries[i];
            EditNodeAbstract node = tree.findNodeByIdAndRange(entry);
            if (node == null) {
                return disallowed("editor.command.setvalue.nodeMissing",
                        new SimpleEntry[]{new SimpleEntry(entry.nodeId, entry.leftRange, entry.timesRange)});
            }

            node.setValue(entry.newValue);
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
                // REBUILD_AFFECTED repaints the edited node in the tree, SELECT_UPDATED keeps it
                // selected - on execute, redo and undo alike (the tree applies update actions only)
                UPDATE_ACTIONS
        );
    }

    @Override
    public CommandResult doUndo(EditTree tree) {
        EditNodeAbstract[] updated = new EditNodeAbstract[entries.length];

        for (int i = 0; i < entries.length; i++) {
            ContentEntry entry = entries[i];
            EditNodeAbstract node = tree.findNodeByIdAndRange(entry);
            if (node == null) {
                return disallowed("editor.command.setvalue.nodeMissing",
                        new SimpleEntry[]{new SimpleEntry(entry.nodeId, entry.leftRange, entry.timesRange)});
            }

            node.setValue(entry.oldValue);
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
                UPDATE_ACTIONS
        );
    }

    private static String text(String value) {
        return value == null ? "null" : value;
    }
}
