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
import java.util.List;
import java.util.Set;

/**
 * Command that moves one or more nodes to new parent/index positions.
 *
 * <p>
 * The command stores both source and target positions explicitly. For
 * multi-node moves, nodes are normalized by source parent and source index to
 * ensure a stable processing order.</p>
 *
 * <p>
 * Single-node moves support index adjustment when reordering inside the same
 * parent. Multi-node moves use the stored target indices as absolute positions
 * and therefore do not apply same-parent correction logic.</p>
 *
 * @author Jansuch Rentenatus
 */
public class MoveNodeCommand extends AbstractEditCommand {

    private final MovementEntry[] oldEntries;
    private final MovementEntry[] newEntries;

    class MoveBubble {

        EditNodeAbstract removed;
        EditNodeAbstract oldParent;
        EditNodeAbstract newParent;

        private MoveBubble(EditNodeAbstract removed, EditNodeAbstract oldParent, EditNodeAbstract newParent) {
            this.removed = removed;
            this.oldParent = oldParent;
            this.newParent = newParent;
        }
    }

    /**
     * Creates a command to move a single node to a new parent and index.
     *
     * @param node the node to move
     * @param newParent the target parent
     * @param newIndex the target index, or {@code -1} to append
     */
    public MoveNodeCommand(EditNodeAbstract node, EditNode newParent, int newIndex) {
        this(new EditNodeAbstract[]{node}, newParent, newIndex);
    }

    /**
     * Creates a command to move a single node to a new index within its current
     * parent.
     *
     * @param node the node to move
     * @param newIndex the target index, or {@code -1} to append
     */
    public MoveNodeCommand(EditNodeAbstract node, int newIndex) {
        this(node, requireParent(node), newIndex);
    }

    /**
     * Creates a command to move multiple nodes to the same target parent,
     * starting at the given index.
     *
     * <p>
     * If {@code newIndex >= 0}, nodes are assigned consecutive target indices
     * starting at that position. If {@code newIndex == -1}, all nodes are
     * appended in normalized order.</p>
     *
     * @param nodes the nodes to move
     * @param newParent the target parent
     * @param newIndex the starting target index, or {@code -1} to append
     */
    public MoveNodeCommand(EditNodeAbstract[] nodes, EditNode newParent, int newIndex) {
        super(CommandType.MOVE_NODE);

        if (newParent == null) {
            throw new NullPointerException("New parent cannot be null");
        }
        long newParentId = newParent.getEditId();

        if (nodes == null) {
            throw new NullPointerException("Nodes cannot be null");
        }
        final int length = nodes.length;
        if (length == 0) {
            throw new IllegalArgumentException("Nodes cannot be empty");
        }
        if (newIndex < -1) {
            throw new IllegalArgumentException("New index cannot be < -1");
        }

        if (newIndex == -1 || newIndex > newParent.getChildCount()) {
            newIndex = newParent.getChildCount();
        }

        EditNodeAbstract[] sortedNodes = Arrays.copyOf(nodes, length);
        Arrays.sort(sortedNodes, Comparator
                .comparingLong((EditNode n) -> {
                    EditNode parent = n.getParent();
                    return parent != null ? parent.getEditId() : -1L;
                })
                .thenComparingInt(n -> {
                    EditNode parent = n.getParent();
                    return parent != null ? parent.getChildIndex(n) : -1;
                }));

        this.oldEntries = new MovementEntry[length];
        this.newEntries = new MovementEntry[length];

        int shift = 0;
        for (int i = 0; i < length; i++) {
            EditNode node = nodes[i];
            if (node == null) {
                throw new IllegalArgumentException("Node at index " + i + " cannot be null");
            }
            if (node.getParent() == null) {
                throw new IllegalArgumentException("Node at index " + i + " must have a parent");
            }
            if (node.getParent().getEditId() != newParentId) {
                continue; // No same-parent correction needed for nodes moving to a different parent
            }
            int childIndex = node.getParent().getChildIndex(node);
            if (newIndex > childIndex) {
                shift++;
            }
        }

        for (int i = 0; i < length; i++) {
            EditNodeAbstract node = sortedNodes[i];
            EditNodeAbstract parent = node.getParent();
            long oldParentId = parent != null ? parent.getEditId() : -1;
            int oldIndex = parent != null ? parent.getChildIndex(node) : -1;

            if (oldParentId < 0 || oldIndex < 0) {
                throw new IllegalArgumentException(
                        "Node at index " + i + " must have a valid parent and index");
            }

            this.oldEntries[i] = new MovementEntry(
                    node,
                    parent,
                    oldIndex
            );

            int targetIndex = newIndex - shift;
            this.newEntries[i] = new MovementEntry(
                    node,
                    newParent,
                    targetIndex + i
            );
        }

        if (nodes.length == 1) {
            setDescription("Move node: " + nodes[0].getName());
        } else {
            setDescription("Move " + nodes.length + " nodes");
        }
    }

    /**
     * Creates a move command from explicit source and target entries.
     *
     * @param oldEntries source positions
     * @param newEntries target positions
     */
    public MoveNodeCommand(MovementEntry[] oldEntries,
            MovementEntry[] newEntries) {
        super(CommandType.MOVE_NODE);

        if (oldEntries == null || newEntries == null) {
            throw new IllegalArgumentException("Entries cannot be null");
        }
        if (oldEntries.length != newEntries.length) {
            throw new IllegalArgumentException("Arrays must have the same length");
        }
        if (oldEntries.length == 0) {
            throw new IllegalArgumentException("Arrays cannot be empty");
        }

        this.oldEntries = copyAndValidate(oldEntries, true);
        this.newEntries = copyAndValidate(newEntries, false);

        if (this.oldEntries.length == 1) {
            setDescription("Move node");
        } else {
            setDescription("Move " + this.oldEntries.length + " nodes");
        }
    }

    /**
     * Copies and validates movement entries.
     *
     * @param entries the entries to copy
     * @param requireSourceIndex whether the index must be non-negative
     * @return the validated copy
     */
    private static MovementEntry[] copyAndValidate(
            MovementEntry[] entries,
            boolean requireSourceIndex) {

        MovementEntry[] copy = new MovementEntry[entries.length];

        for (int i = 0; i < entries.length; i++) {
            MovementEntry entry = entries[i];
            if (entry == null) {
                throw new IllegalArgumentException("Entry at index " + i + " cannot be null");
            }
            if (entry.nodeId < 0) {
                throw new IllegalArgumentException("Entry nodeId at index " + i + " is invalid");
            }
            if (entry.parentEditId < 0) {
                throw new IllegalArgumentException("Entry parentEditId at index " + i + " is invalid");
            }
            if (requireSourceIndex) {
                if (entry.index < 0) {
                    throw new IllegalArgumentException(
                            "Source entry index at index " + i + " is invalid");
                }
            } else {
                if (entry.index < -1) {
                    throw new IllegalArgumentException(
                            "Target entry index at index " + i + " is invalid");
                }
            }

            copy[i] = new MovementEntry(
                    entry.nodeId,
                    entry.leftRange,
                    entry.timesRange,
                    entry.parentEditId,
                    entry.parentLeftRange,
                    entry.parentTimesRange,
                    entry.index,
                    entry.snapshot
            );
        }

        return copy;
    }

    @Override
    public CommandAvailability check(EditTree tree) {
        if (tree == null) {
            return CommandAvailability.disallowed("editor.command.tree.missing");
        }

        if (isNoOpMove(tree)) {
            return CommandAvailability.useless("editor.command.move.useless");
        }

        for (int i = 0; i < newEntries.length; i++) {
            MovementEntry oldEntry = oldEntries[i];
            MovementEntry newEntry = newEntries[i];

            EditNode node = tree.findNodeByIdAndRange(oldEntry);
            if (node == null) {
                return CommandAvailability.disallowed(
                        "editor.command.move.nodeMissing",
                        Long.toString(oldEntry.nodeId),
                        Integer.toString(i));
            }

            EditNode sourceParent = tree.findNodeByIdAndRange(
                    oldEntry.parentEditId, oldEntry.parentLeftRange, oldEntry.parentTimesRange
            );
            if (sourceParent == null) {
                return CommandAvailability.disallowed(
                        "editor.command.move.sourceParentMissing",
                        Long.toString(oldEntry.parentEditId),
                        Integer.toString(i));
            }

            EditNode targetParent = tree.findNodeByIdAndRange(
                    newEntry.parentEditId, newEntry.parentLeftRange, newEntry.parentTimesRange
            );
            if (targetParent == null) {
                return CommandAvailability.disallowed(
                        "editor.command.move.targetParentMissing",
                        Long.toString(newEntry.parentEditId),
                        Integer.toString(i));
            }

            int sourceIndex = sourceParent.getChildIndex(node);
            if (sourceIndex < 0) {
                return CommandAvailability.disallowed(
                        "editor.command.move.nodeNotChildOfSourceParent",
                        Long.toString(oldEntry.nodeId),
                        Long.toString(oldEntry.parentEditId),
                        Integer.toString(i));
            }

            int targetChildCount = targetParent.getChildCount();
            int requestedIndex = newEntry.index;
            if (requestedIndex < -1 || requestedIndex > targetChildCount) {
                return CommandAvailability.disallowed(
                        "editor.command.move.indexInvalid",
                        Integer.toString(requestedIndex),
                        Integer.toString(targetChildCount),
                        Integer.toString(i));
            }

            if (isAncestorOf(targetParent, node)) {
                return CommandAvailability.disallowed(
                        "editor.command.move.wouldCreateCycle",
                        Long.toString(node.getEditId()),
                        Long.toString(targetParent.getEditId()),
                        Integer.toString(i));
            }

            if (!node.canBeChildOf(targetParent)) {
                return CommandAvailability.disallowed(
                        "editor.command.move.childNotAllowedForParent",
                        node.getTypeKey(),
                        targetParent.getTypeKey(),
                        Integer.toString(i));
            }
        }

        return CommandAvailability.allowed("editor.command.move.allowed");
    }

    private boolean isNoOpMove(EditTree tree) {
        if (oldEntries.length != 1 || newEntries.length != 1) {
            return false;
        }

        int minIndex = Integer.MAX_VALUE;
        int maxInddex = -1;
        for (int i = 0; i < newEntries.length; i++) {
            MovementEntry oldEntry = oldEntries[i];
            MovementEntry newEntry = newEntries[i];

            EditNode node = tree.findNodeByIdAndRange(oldEntry);
            if (node == null || node.getParent() == null) {
                return false;
            }

            EditNode currentParent = node.getParent();
            if (currentParent.getEditId() != newEntry.parentEditId) {
                return false;
            }

            int currentIndex = currentParent.getChildIndex(node);
            minIndex = Math.min(minIndex, currentIndex);
            maxInddex = Math.max(maxInddex, currentIndex);
            if (currentIndex < 0) {
                return false;
            }
        }

        if (maxInddex - minIndex > newEntries.length - 1) {
            return false;
        }

        MovementEntry newEntry = newEntries[0];
        int newIndex = newEntry.index;

        return newIndex == minIndex;
    }

    /**
     * Executes the move operation.
     *
     * @param tree the target tree
     * @param redoAction true, if this execute is calling by redo action.
     * @return the command result
     */
    @Override
    protected CommandResult doExecute(EditTree tree, boolean redoAction) {
        MoveBubble[] successfullyRemoved = removeAll(tree, oldEntries, newEntries);
        EditNodeAbstract[] moved = addAll(tree, newEntries, successfullyRemoved);
        EditNodeAbstract[] failed = failed(tree, oldEntries, successfullyRemoved);

        return new CommandResult(
                this,
                redoAction ? CommandAction.REDO : CommandAction.EXECUTE,
                unionParentNodes(successfullyRemoved),
                newEntries, // templateEntries
                null,
                null,
                moved,
                failed,
                UPDATE_ACTIONS
        );
    }

    /**
     * Undoes the move operation.
     *
     * <p>
     * Undo uses the stored original indices as absolute target positions.
     * Therefore, multi-node undo is processed in forward order.</p>
     *
     * @param tree the target tree
     * @return the command result
     */
    @Override
    public CommandResult doUndo(EditTree tree) {

        MoveBubble[] successfullyRemoved = removeAll(tree, newEntries, oldEntries);
        EditNodeAbstract[] moved = addAll(tree, oldEntries, successfullyRemoved);
        EditNodeAbstract[] failed = failed(tree, newEntries, successfullyRemoved);

        return new CommandResult(
                this,
                CommandAction.UNDO,
                unionParentNodes(successfullyRemoved),
                oldEntries, // templateEntries
                null,
                null,
                moved,
                failed,
                UPDATE_ACTIONS
        );
    }

    /**
     * Returns the stored source entries.
     *
     * @return a defensive copy of the source entries
     */
    public MovementEntry[] getOldEntries() {
        return Arrays.copyOf(oldEntries, oldEntries.length);
    }

    /**
     * Returns the stored target entries.
     *
     * @return a defensive copy of the target entries
     */
    public MovementEntry[] getNewEntries() {
        return Arrays.copyOf(newEntries, newEntries.length);
    }

    /**
     * Removes nodes from the tree based on the provided movement entries and
     * their corresponding target check entries. This method iterates through
     * the movement entries and checks if the corresponding target check entry
     * indicates a valid target position. If a valid target is found, it
     * retrieves the node to remove and its old parent from the tree, removes
     * the node from its current position, and creates a MoveBubble to store the
     * removed node along with its old and new parent information. The method
     * collects all successfully removed nodes into an array of MoveBubbles and
     * returns it.
     *
     * @param tree the tree from which nodes should be removed
     * @param entries the movement entries containing the source positions of
     * the nodes to remove
     * @param targetCheck the movement entries containing the target positions
     * to check for validity before removal
     * @return an array of MoveBubbles representing the successfully removed
     * nodes and their parent information
     */
    private MoveBubble[] removeAll(EditTree tree, MovementEntry[] entries, MovementEntry[] targetCheck) {
        MoveBubble[] successfullyRemoved = new MoveBubble[entries.length];
        for (int i = 0; i < entries.length; i++) {
            MovementEntry entry = entries[i];
            EditNodeAbstract node = tree.findNodeByIdAndRange(entry);
            EditNodeAbstract parentTarget = tree.findNodeByIdAndRange(
                    targetCheck[i].parentEditId, targetCheck[i].parentLeftRange, targetCheck[i].parentTimesRange
            );
            if (node != null && node.getParent() != null && parentTarget != null) {
                EditNodeAbstract oldParent = node.getParent();
                tree.removeNode(node);
                successfullyRemoved[i] = new MoveBubble(node, oldParent, parentTarget);
            }
        }
        return successfullyRemoved;
    }

    /**
     * Adds nodes to the tree based on the provided movement entries and their
     * corresponding move bubbles. This method iterates through the movement
     * entries and checks if the corresponding move bubble indicates a
     * successful removal. If a move bubble is present, it retrieves the node to
     * add and its new parent from the move bubble, and then adds the node to
     * the tree at the specified index. The method collects all successfully
     * moved nodes into a list and returns them as an array.
     *
     * @param tree the tree to which nodes should be added
     * @param entries the movement entries containing the target positions for
     * the nodes
     * @param successfullyRemoved the array of move bubbles indicating which
     * nodes were successfully removed and their new parents
     * @return an array of nodes that were successfully added to the tree
     */
    private EditNodeAbstract[] addAll(EditTree tree, MovementEntry[] entries, MoveBubble[] successfullyRemoved) {
        List<EditNode> moved = new ArrayList<>();
        for (int i = 0; i < entries.length; i++) {
            if (successfullyRemoved[i] == null) {
                continue; // Skip adding if removal was not successful
            }
            MovementEntry entry = entries[i];
            EditNodeAbstract parent = successfullyRemoved[i].newParent;
            EditNodeAbstract nodeToAdd = successfullyRemoved[i].removed;

            int insertIndex = entry.index;
            if (insertIndex < 0 || insertIndex > parent.getChildCount()) {
                insertIndex = parent.getChildCount();
            }
            tree.addChild(parent, nodeToAdd, insertIndex);
            moved.add(nodeToAdd);
        }
        return moved.toArray(new EditNodeAbstract[moved.size()]);
    }

    /**
     * Returns the nodes for which the move operation failed. This method
     * iterates through the entries and checks if the corresponding move bubble
     * indicates a successful removal. If a move bubble is null, it means that
     * the node was not successfully removed from its original position, and
     * therefore the node is considered to have failed to move. The method
     * collects all such nodes into a list and returns them as an array.
     *
     * @param tree the tree in which the move operation was attempted
     * @param entries the movement entries corresponding to the attempted move
     * operations
     * @param successfullyRemoved the array of move bubbles indicating which
     * nodes were successfully removed
     * @return an array of nodes for which the move operation failed
     */
    private EditNodeAbstract[] failed(EditTree tree, MovementEntry[] entries, MoveBubble[] successfullyRemoved) {
        List<EditNode> failed = new ArrayList<>();
        for (int i = 0; i < entries.length; i++) {
            if (successfullyRemoved[i] != null) {
                continue; // Skip if removal was successful
            }
            MovementEntry entry = entries[i];
            failed.add(entry.snapshot);
        }
        return failed.toArray(new EditNodeAbstract[failed.size()]);
    }

    /**
     * Returns the union of parent nodes involved in the given move bubbles.
     * This method iterates through the array of move bubbles and collects the
     * old and new parent nodes into a set, ensuring that each parent node is
     * included only once. If a parent node is found to be a child of another
     * parent already in the set, it is removed to maintain a collection of
     * unique parent nodes that are relevant to the move operation. The
     * resulting array contains all unique parent nodes from the move bubbles.
     *
     * @param moveBubbles the array of move bubbles to process
     * @return an array of unique parent nodes involved in the move bubbles
     */
    private EditNodeAbstract[] unionParentNodes(MoveBubble[] moveBubbles) {
        Set<EditNodeAbstract> union = new java.util.HashSet<>();
        for (MoveBubble moveBubble : moveBubbles) {
            if (moveBubble.oldParent != null) {
                removeChildrenOf(union, moveBubble.oldParent);
                if (!hasParentIn(union, moveBubble.oldParent)) {
                    union.add(moveBubble.oldParent);
                }
            }
            if (moveBubble.newParent != null) {
                removeChildrenOf(union, moveBubble.newParent);
                if (!hasParentIn(union, moveBubble.newParent)) {
                    union.add(moveBubble.newParent);
                }
            }
        }
        return union.toArray(new EditNodeAbstract[union.size()]);
    }

}
