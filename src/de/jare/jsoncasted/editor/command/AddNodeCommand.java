/*
* Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v2.0 which
* accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.command.EditCommand.CommandType;
import de.jare.jsoncasted.editor.command.EditCommandEntry.MovementEntry;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import java.util.Arrays;

/**
 * Command that adds node(s) to the tree. When executed, the node(s) are
 * inserted at their specified parent and index. When undone, the node(s) are
 * removed from the tree.
 *
 * @author Jansuch Rentenatus
 */
public class AddNodeCommand extends AbstractEditCommand {

    private final MovementEntry[] entries;

    /**
     * Creates a command to add a single node.
     *
     * @param parent the parent node
     * @param node the node to add
     */
    public AddNodeCommand(EditNode parent, EditNodeAbstract node) {
        this(parent, node, -1);
    }

    /**
     * Creates a command to add a single node at a specific index.
     *
     * @param parent the parent node
     * @param node the node to add
     * @param index the index at which to insert the node, or -1 to append
     */
    public AddNodeCommand(EditNode parent, EditNodeAbstract node, int index) {
        super(CommandType.ADD_NODE);
        this.entries = (new MovementEntry[]{
            new MovementEntry(
            requireNode(node),
            parent,
            index // index
            )
        });
        setDescription("Add node: " + node.getName());
    }

    /**
     * Creates a command to add multiple nodes from entries.
     *
     * @param entries array of entries to add
     */
    public AddNodeCommand(MovementEntry[] entries) {
        super(CommandType.ADD_NODE);
        if (entries == null || entries.length == 0) {
            throw new IllegalArgumentException("Entries cannot be null or empty");
        }

        this.entries = copyAndValidate(entries);

        if (this.entries.length == 1) {
            setDescription("Add node: " + this.entries[0].snapshot.getName());
        } else {
            setDescription("Add " + this.entries.length + " nodes");
        }
    }

    /**
     * Creates a copy of the given entries array and validates each entry. This
     * method checks that each entry is not null, has a valid snapshot, a
     * non-negative parentEditId, and an index that is either -1 or
     * non-negative. If any entry fails validation, an IllegalArgumentException
     * is thrown with a descriptive message indicating the issue and the index
     * of the problematic entry. The method returns a new array containing
     * copies of the valid entries.
     *
     * @param entries the array of entries to copy and validate
     * @return a new array containing copies of the valid entries
     * @throws IllegalArgumentException if any entry is invalid
     */
    private static MovementEntry[] copyAndValidate(MovementEntry[] entries) {
        MovementEntry[] copy = new MovementEntry[entries.length];

        for (int i = 0; i < entries.length; i++) {
            MovementEntry entry = entries[i];
            if (entry == null) {
                throw new IllegalArgumentException("Entry at index " + i + " cannot be null");
            }
            if (entry.snapshot == null) {
                throw new IllegalArgumentException("Entry snapshot at index " + i + " cannot be null");
            }
            if (entry.parentEditId < 0) {
                throw new IllegalArgumentException("Entry parentEditId at index " + i + " is invalid");
            }
            if (entry.index < -1) {
                throw new IllegalArgumentException("Entry index at index " + i + " is invalid");
            }

            // nodeId aus Entry mit uebernehmen, Snapshot geklont
            copy[i] = new MovementEntry(
                    entry.nodeId,
                    entry.leftRange,
                    entry.timesRange,
                    entry.parentEditId,
                    entry.parentLeftRange,
                    entry.parentTimesRange,
                    entry.index,
                    entry.snapshot.deepCopy(false)
            );
        }
        return copy;
    }

    @Override
    public CommandAvailability check(EditTree tree) {
        return checkAdd(tree, entries);
    }

    @Override
    protected CommandResult doExecute(EditTree tree, boolean redoAction) {
        return doAdd(tree, entries, !redoAction, redoAction ? CommandAction.REDO : CommandAction.EXECUTE);
    }

    @Override
    public CommandResult doUndo(EditTree tree) {
        return doDelete(tree, entries, CommandAction.UNDO);
    }

    /**
     * Returns a defensive copy of the entries array.
     *
     * @return a copy of the entries array
     */
    public MovementEntry[] getEntries() {
        return Arrays.copyOf(entries, entries.length);
    }

}
