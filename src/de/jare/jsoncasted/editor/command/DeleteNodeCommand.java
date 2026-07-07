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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command that deletes node(s) from the tree. When executed, the node(s) are
 * removed from their parent(s). When undone, the node(s) are restored from deep
 * copy snapshots.
 *
 * Snapshots preserve the original node IDs to ensure undo/redo works correctly.
 *
 * For multi-selection, descendant nodes are ignored if one of their ancestors
 * is already part of the delete set. This prevents duplicate restoration on
 * undo.
 *
 * @author Jansuch Rentenatus
 */
public class DeleteNodeCommand extends AbstractEditCommand {

    private final MovementEntry[] entries;

    /**
     * Creates a new delete node command for a single node.
     *
     * @param node the node to delete
     */
    public DeleteNodeCommand(EditNodeAbstract node) {
        this(toEntries(new EditNodeAbstract[]{requireNode(node, 0)}));
    }

    /**
     * Creates a new delete node command for multiple nodes.
     *
     * @param nodes the nodes to delete
     */
    public DeleteNodeCommand(EditNodeAbstract[] nodes) {
        this(toEntries(nodes));
    }

    /**
     * Creates a delete command from entries array.
     *
     * @param entries array of entries to delete
     */
    public DeleteNodeCommand(MovementEntry[] entries) {
        super(CommandType.DELETE_NODE);
        if (entries == null || entries.length == 0) {
            throw new IllegalArgumentException("Entries cannot be null or empty");
        }
        this.entries = copyAndValidate(entries);

        if (this.entries.length == 1) {
            setDescription("Delete node: " + this.entries[0].snapshot.getName());
        } else {
            setDescription("Delete " + this.entries.length + " nodes");
        }
    }

    private static MovementEntry[] copyAndValidate(MovementEntry[] entries) {
        MovementEntry[] copy = new MovementEntry[entries.length];

        for (int i = 0; i < entries.length; i++) {
            MovementEntry entry = entries[i];
            if (entry == null) {
                throw new IllegalArgumentException("Entry at index " + i + " cannot be null");
            }
            if (entry.nodeId < 0) {
                throw new IllegalArgumentException("Entry nodeId at index " + i + " is invalid");
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
        return checkDelete(tree, entries);
    }

    @Override
    protected CommandResult doExecute(EditTree tree, boolean redoAction) {
        return doDelete(tree, entries, redoAction ? CommandAction.REDO : CommandAction.EXECUTE);
    }

    @Override
    public CommandResult doUndo(EditTree tree) {
        return doAdd(tree, entries, false, CommandAction.UNDO);
    }

    /**
     * Returns a defensive copy of the entries array.
     *
     * @return a copy of the entries array
     */
    public MovementEntry[] getEntries() {
        return Arrays.copyOf(entries, entries.length);
    }

    private static MovementEntry[] toEntries(EditNodeAbstract[] nodes) {
        if (nodes == null || nodes.length == 0) {
            throw new IllegalArgumentException("Nodes cannot be null or empty");
        }

        EditNodeAbstract[] validated = new EditNodeAbstract[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            validated[i] = requireNode(nodes[i], i);
        }

        List<EditNodeAbstract> normalized = normalizeNodes(validated);
        MovementEntry[] result = new MovementEntry[normalized.size()];

        for (int i = 0; i < normalized.size(); i++) {
            EditNodeAbstract node = normalized.get(i);
            EditNodeAbstract parent = node.getParent();
            if (parent == null) {
                throw new IllegalArgumentException("Node '" + node.getName() + "' has no parent and cannot be deleted");
            }

            int index = parent.getChildIndex(node);
            if (index < 0) {
                throw new IllegalArgumentException(
                        "Node '" + node.getName() + "' is not a child of its parent");
            }

            result[i] = new MovementEntry(
                    node,
                    parent,
                    index
            );
        }

        return result;
    }

    private static List<EditNodeAbstract> normalizeNodes(EditNodeAbstract[] nodes) {
        Map<Long, EditNodeAbstract> unique = new LinkedHashMap<>();
        for (EditNodeAbstract node : nodes) {
            unique.put(node.getEditId(), node);
        }

        List<EditNodeAbstract> result = new ArrayList<>();
        for (EditNodeAbstract node : unique.values()) {
            if (!hasSelectedAncestor(node, unique)) {
                result.add(node);
            }
        }

        result.sort(Comparator
                .comparingLong((EditNode n) -> n.getParent().getEditId())
                .thenComparingInt(n -> n.getParent().getChildIndex(n)));

        return result;
    }

    private static boolean hasSelectedAncestor(EditNodeAbstract node, Map<Long, EditNodeAbstract> selectedNodes) {
        EditNode parent = node.getParent();
        while (parent != null) {
            if (selectedNodes.containsKey(parent.getEditId())) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

}
