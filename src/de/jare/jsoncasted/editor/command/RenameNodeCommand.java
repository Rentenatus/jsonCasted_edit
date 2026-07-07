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
 * Command that renames node(s) in the tree. When executed, the node(s) names
 * are updated. When undone, the previous names are restored.
 *
 * @author Jansuch Rentenatus
 */
public class RenameNodeCommand extends AbstractEditCommand {

    private final ContentEntry[] entries;

    /**
     * Creates a command to rename a single node.
     *
     * @param node the node to rename
     * @param newName the new name
     */
    public RenameNodeCommand(EditNode node, String newName) {
        super(CommandType.RENAME_NODE);
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }

        String oldName = node.getName();
        this.entries = new ContentEntry[]{
            new ContentEntry(node, oldName, newName)
        };

        setDescription("Rename node: " + text(oldName) + " -> " + text(newName));
    }

    /**
     * Creates a command to rename multiple nodes.
     *
     * @param nodes the nodes to rename
     * @param newNames the new names
     */
    public RenameNodeCommand(EditNode[] nodes, String[] newNames) {
        super(CommandType.RENAME_NODE);
        if (nodes == null || newNames == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        if (nodes.length != newNames.length) {
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
                    node.getName(),
                    newNames[i]
            );
        }

        if (nodes.length == 1) {
            setDescription("Rename node: " + text(entries[0].oldValue) + " -> " + text(entries[0].newValue));
        } else {
            setDescription("Rename " + nodes.length + " nodes");
        }
    }

    /**
     * Creates a command from rename entries.
     *
     * @param entries the rename entries
     */
    public RenameNodeCommand(ContentEntry[] entries) {
        super(CommandType.RENAME_NODE);
        if (entries == null || entries.length == 0) {
            throw new IllegalArgumentException("Entries cannot be null or empty");
        }

        this.entries = copyAndValidate(entries);

        if (this.entries.length == 1) {
            setDescription("Rename node: " + text(this.entries[0].oldValue) + " -> " + text(this.entries[0].newValue));
        } else {
            setDescription("Rename " + this.entries.length + " nodes");
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
                        "editor.command.rename.nodeMissing",
                        Long.toString(entry.nodeId),
                        Integer.toString(i));
            }

            if (entry.newValue == null) {
                return CommandAvailability.disallowed(
                        "editor.command.rename.newNameMissing",
                        Long.toString(entry.nodeId),
                        Integer.toString(i));
            }

            if (entry.newValue.trim().isEmpty()) {
                return CommandAvailability.disallowed(
                        "editor.command.rename.newNameBlank",
                        Long.toString(entry.nodeId),
                        Integer.toString(i));
            }
        }

        return CommandAvailability.allowed(
                "editor.command.rename.allowed");
    }

    @Override
    protected CommandResult doExecute(EditTree tree, boolean redoAction) {
        EditNodeAbstract[] updated = new EditNodeAbstract[entries.length];

        for (int i = 0; i < entries.length; i++) {
            ContentEntry entry = entries[i];
            EditNodeAbstract node = tree.findNodeByIdAndRange(entry);
            if (node == null) {
                return disallowed("editor.command.rename.nodeMissing",
                        new SimpleEntry[]{new SimpleEntry(entry.nodeId, entry.leftRange, entry.timesRange)});
            }

            node.setName(entry.newValue);
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
                return disallowed("editor.command.rename.nodeMissing",
                        new SimpleEntry[]{new SimpleEntry(entry.nodeId, entry.leftRange, entry.timesRange)});
            }

            node.setName(entry.oldValue);
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
     * Returns the old names of all entries.
     *
     * @return array of old names
     */
    public String[] getOldNames() {
        String[] values = new String[entries.length];
        for (int i = 0; i < entries.length; i++) {
            values[i] = entries[i].oldValue;
        }
        return values;
    }

    /**
     * Returns the new names of all entries.
     *
     * @return array of new names
     */
    public String[] getNewNames() {
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

    private static String text(String value) {
        return value != null ? value : "null";
    }

}
