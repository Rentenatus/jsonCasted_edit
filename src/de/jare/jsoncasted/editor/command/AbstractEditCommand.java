/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.SimpleEntry;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Abstract base class for all edit commands in the JSON tree editor. Provides
 * common functionality for command type management and description handling.
 *
 * <p>
 * All edit commands must extend this class and implement the
 * {@link EditCommand} interface methods {@code execute()} and
 * {@code undo()}.</p>
 *
 * @author Jansuch Rentenatus
 */
public abstract class AbstractEditCommand implements EditCommand {

    public static final JackUpdateAction[] NO_UPDATE_ACTIONS = new JackUpdateAction[0];
    public static final JackUpdateAction[] ON_ADD_ACTIONS = new JackUpdateAction[]{JackUpdateAction.REBUILD_AFFECTED, JackUpdateAction.SELECT_ADDED};
    public static final JackUpdateAction[] ON_REMOVE_ACTIONS = new JackUpdateAction[]{JackUpdateAction.REBUILD_AFFECTED, JackUpdateAction.SELECT_UPDATED};
    public static final JackUpdateAction[] ON_CLIPBOARD_ACTIONS = new JackUpdateAction[]{JackUpdateAction.REBUILD_CLIPBOARD};
    public static final JackUpdateAction[] UPDATE_ACTIONS = new JackUpdateAction[]{JackUpdateAction.REBUILD_AFFECTED, JackUpdateAction.SELECT_UPDATED};

    private final CommandType type;
    private String description;
    boolean skipped;

    private CommandAction lastAction;
    private int lastUpdatedCount;
    private int lastFailedCount;
    private String lastMessage;

    /**
     * Creates a new abstract edit command with the specified type.
     *
     * @param type the command type
     */
    protected AbstractEditCommand(CommandType type) {
        this.type = type;
        this.description = "";
        this.skipped = false;
        this.lastMessage = null;
    }

    /**
     * Creates a new abstract edit command with the specified type and
     * description.
     *
     * @param type the command type
     * @param description a human-readable description of the command
     */
    protected AbstractEditCommand(CommandType type, String description) {
        this.type = type;
        this.description = description;
        this.lastMessage = null;
    }

    @Override
    public CommandType getType() {
        return type;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of this command.
     *
     * @param description the description to set
     */
    protected void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the last action performed by this command.
     *
     * @return the last command action
     */
    public CommandAction getLastAction() {
        return lastAction;
    }

    /**
     * Returns the number of nodes updated in the last operation.
     *
     * @return the count of updated nodes
     */
    public int getLastUpdatedCount() {
        return lastUpdatedCount;
    }

    /**
     * Returns the number of nodes that failed in the last operation.
     *
     * @return the count of failed nodes
     */
    public int getLastFailedCount() {
        return lastFailedCount;
    }

    @Override
    public String getLastMessage() {
        return lastMessage;
    }

    /**
     * Sets the last message associated with this command.
     *
     * @param lastMessage
     */
    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    @Override
    public void skipped() {
        skipped = true;
        lastAction = CommandAction.SKIPPED;
        lastUpdatedCount = 0;
        lastFailedCount = 0;
    }

    public boolean consumeSkipped() {
        boolean ret = skipped;
        skipped = false;
        return ret;
    }

    public CommandResult disallowed(String messageKey, SimpleEntry[] entries) {
        CommandResult ret = new CommandResult(this, CommandAction.DISALLOWED, null,
                Arrays.copyOf(entries, entries.length),
                null, null, null, null, null);
        ret.setMessageKey(messageKey);
        return ret;
    }

    @Override
    public final CommandResult execute(EditTree tree, boolean redoAction) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }
        CommandResult result = doExecute(tree, redoAction);
        if (result != null) {
            lastAction = result.getAction();
            lastUpdatedCount = result.getUpdatedNodes().length;
            lastFailedCount = result.getFailedNodes().length;
            lastMessage = null;
            if (CommandAction.DISALLOWED.equals(result.getAction())) {
                lastFailedCount = result.getTemplateEntries().length;
                lastMessage = result.getMessageKey();
            }
        }
        return result;
    }

    /**
     * Executes the command on the given tree.Subclasses must implement this
     * method.
     *
     * @param tree the tree to modify
     * @param redoAction true, if this execute is calling by redo action.
     * @return the result describing the changes caused by this execution
     */
    protected abstract CommandResult doExecute(EditTree tree, boolean redoAction);

    @Override
    public final CommandResult undo(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }
        if (consumeSkipped()) {
            lastAction = CommandAction.SKIPPED;
            lastUpdatedCount = 0;
            lastFailedCount = 0;
            return new CommandResult(this, CommandAction.SKIPPED, null, null, null, null, null, null, NO_UPDATE_ACTIONS);
        }
        CommandResult result = doUndo(tree);
        if (result != null) {
            lastAction = result.getAction();
            lastUpdatedCount = result.getUpdatedNodes().length;
            lastFailedCount = result.getFailedNodes().length;
        }
        return result;
    }

    /**
     * Undoes cover.
     *
     * @param tree the tree to modify
     * @return the result describing the changes caused by this undo operation
     */
    protected abstract CommandResult doUndo(EditTree tree);

    protected CommandResult doAdd(EditTree tree, final EditCommandEntry.MovementEntry[] entries, boolean regenerateEditId, CommandAction action) {
        CommandAvailability checkResult = checkAdd(tree, entries);
        if (checkResult.isDisallowed()) {
            return disallowed(checkResult.getMessageKey(), entries);
        }
        if (checkResult.isUseless()) {
            return null;
        }

        EditNodeAbstract[] added = new EditNodeAbstract[entries.length];
        Set<EditNodeAbstract> parentSet = new HashSet<>();
        Set<EditNodeAbstract> failedtSet = new HashSet<>();
        SimpleEntry[] templates = new SimpleEntry[entries.length];

        for (int i = 0; i < entries.length; i++) {
            EditCommandEntry.MovementEntry entry = entries[i];
            EditNodeAbstract parent = tree.findNodeByIdAndRange(entry.parentEditId, entry.leftRange, entry.timesRange);
            EditNodeAbstract newNode = tree.addNode(parent, entry.snapshot, entry.index, regenerateEditId);

            templates[i] = new SimpleEntry(entry.nodeId, entry.leftRange, entry.timesRange);
            if (newNode != null) {
                added[i] = newNode;
                if (entry.nodeId != newNode.getEditId()) {
                    entry.nodeId = newNode.getEditId();
                    entry.leftRange = newNode.getLeftRange();
                    entry.timesRange = newNode.getTimesRange();
                    entry.snapshot = newNode.deepCopy(false);
                }
                parentSet.add(parent);
            } else {
                failedtSet.add(newNode);
            }

        }
        final EditNodeAbstract[] parents = unionNodes(parentSet, null);

        return new CommandResult(
                this,
                action,
                parents, // affectedNodes
                templates, // templateEntries
                added, // addedNodes
                null, //removedNodes
                parents, // updatedNodes
                failedtSet.toArray(new EditNodeAbstract[failedtSet.size()]),
                ON_ADD_ACTIONS
        );
    }

    protected CommandAvailability checkAdd(EditTree tree, final EditCommandEntry.MovementEntry[] entries) {
        if (tree == null) {
            return CommandAvailability.disallowed(
                    "editor.command.tree.missing");
        }

        for (int i = 0; i < entries.length; i++) {
            EditCommandEntry.MovementEntry entry = entries[i];

            EditNode parent = tree.findNodeByIdAndRange(entry.parentEditId, entry.parentLeftRange, entry.parentTimesRange);
            if (parent == null) {
                return CommandAvailability.disallowed(
                        "editor.command.add.parentMissing",
                        Long.toString(entry.parentEditId),
                        Integer.toString(i));
            }

            EditNode child = entry.snapshot;
            if (child == null) {
                return CommandAvailability.disallowed(
                        "editor.command.add.snapshotMissing",
                        Integer.toString(i));
            }

            if (!child.canBeChildOf(parent)) {
                return CommandAvailability.disallowed(
                        "editor.command.add.childNotAllowedForParent",
                        child.getTypeKey(),
                        parent.getTypeKey(),
                        Integer.toString(i));
            }
        }

        return CommandAvailability.allowed("editor.command.add.allowed");
    }

    protected CommandResult doDelete(EditTree tree, final EditCommandEntry.MovementEntry[] entries, CommandAction action) {
        CommandAvailability checkResult = checkDelete(tree, entries);
        if (checkResult.isDisallowed()) {
            return disallowed(checkResult.getMessageKey(), entries);
        }
        if (checkResult.isUseless()) {
            return null;
        }
        EditNodeAbstract[] removed = new EditNodeAbstract[entries.length];
        Set<EditNodeAbstract> parentSet = new HashSet<>();
        Set<EditNodeAbstract> failedtSet = new HashSet<>();
        SimpleEntry[] templates = new SimpleEntry[entries.length];

        // rueckwaerts, um Indizes stabil zu halten
        for (int i = entries.length - 1; i >= 0; i--) {
            EditCommandEntry.MovementEntry entry = entries[i];

            templates[i] = new SimpleEntry(entry.nodeId, entry.leftRange, entry.timesRange);

            // bevorzugt nodeId nutzen; fallback auf snapshot-Id, falls nodeId == -1
            long id = entry.nodeId >= 0 ? entry.nodeId : entry.snapshot.getEditId();

            EditNodeAbstract existingNode = tree.findNodeByIdAndRange(id, entry.leftRange, entry.timesRange);
            if (existingNode == null) {
                failedtSet.add(entry.snapshot);
                continue;
            }
            final EditNodeAbstract parent = existingNode.getParent();
            if (parent != null) {
                parentSet.add(parent);
            }
            tree.removeNode(existingNode);
            removed[i] = existingNode;
        }
        final EditNodeAbstract[] parents = unionNodes(parentSet, null);

        return new CommandResult(
                this,
                action,
                parents, // affectedNodes
                templates, // templateEntries
                null, // addedNodes
                removed,//removedNodes
                parents, // updatedNodes
                failedtSet.toArray(new EditNodeAbstract[failedtSet.size()]),
                ON_REMOVE_ACTIONS
        );
    }

    protected CommandAvailability checkDelete(EditTree tree, final EditCommandEntry.MovementEntry[] entries) {
        if (tree == null) {
            return CommandAvailability.disallowed(
                    "editor.command.tree.missing");
        }

        for (int i = 0; i < entries.length; i++) {
            EditCommandEntry.MovementEntry entry = entries[i];
            long nodeId = entry.nodeId >= 0 ? entry.nodeId : entry.snapshot.getEditId();

            EditNode node = tree.findNodeByIdAndRange(nodeId, entry.leftRange, entry.timesRange);
            if (node == null) {
                return CommandAvailability.disallowed(
                        "editor.command.delete.nodeMissing",
                        Long.toString(nodeId),
                        Integer.toString(i));
            }

            if (node == tree.getRoot()) {
                return CommandAvailability.disallowed(
                        "editor.command.delete.rootNotAllowed",
                        Long.toString(nodeId));
            }

            EditNode parent = node.getParent();
            if (parent == null) {
                return CommandAvailability.disallowed(
                        "editor.command.delete.parentMissing",
                        Long.toString(nodeId),
                        Integer.toString(i));
            }

            int currentIndex = parent.getChildIndex(node);
            if (currentIndex < 0) {
                return CommandAvailability.disallowed(
                        "editor.command.delete.nodeNotChildOfParent",
                        Long.toString(nodeId),
                        Long.toString(parent.getEditId()),
                        Integer.toString(i));
            }
        }

        return CommandAvailability.allowed("editor.command.delete.allowed");
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[type=" + type + ", description='" + description + "']";
    }

    /**
     *
     * @param children1
     * @param children2orNull
     * @return
     */
    public final EditNodeAbstract[] unionNodes(Iterable<EditNodeAbstract> children1, Iterable<EditNodeAbstract> children2orNull) {
        Set<EditNodeAbstract> union = new java.util.HashSet<>();
        for (EditNodeAbstract node : children1) {
            if (node != null) {
                removeChildrenOf(union, node);
                if (!hasParentIn(union, node)) {
                    union.add(node);
                }
            }
        }
        if (children2orNull != null) {
            for (EditNodeAbstract node : children2orNull) {
                removeChildrenOf(union, node);
                if (!hasParentIn(union, node)) {
                    union.add(node);
                }
            }
        }
        return union.toArray(new EditNodeAbstract[union.size()]);
    }

    /**
     *
     * @param children1
     * @param children2orNull
     * @return
     */
    public final EditNodeAbstract[] unionParentNodes(Iterable<EditNodeAbstract> children1, Iterable<EditNodeAbstract> children2orNull) {
        Set<EditNodeAbstract> union = new java.util.HashSet<>();
        for (EditNodeAbstract node : children1) {
            EditNodeAbstract parent = node.getParent();
            if (parent != null) {
                removeChildrenOf(union, parent);
                if (!hasParentIn(union, parent)) {
                    union.add(parent);
                }
            }
        }
        if (children2orNull != null) {
            for (EditNodeAbstract node : children2orNull) {
                EditNodeAbstract parent = node.getParent();
                if (parent != null) {
                    removeChildrenOf(union, parent);
                    if (!hasParentIn(union, parent)) {
                        union.add(parent);
                    }
                }
            }
        }
        return union.toArray(new EditNodeAbstract[union.size()]);
    }

    /**
     * Returns true if candidateAncestor is an ancestor of node (strict).
     *
     * @param node
     * @param candidateAncestor
     * @return
     */
    public static boolean isAncestorOf(EditNode node, EditNode candidateAncestor) {
        EditNode current = node.getParent();
        while (current != null) {
            if (current == candidateAncestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Returns the parent ID of the given node.
     *
     * @param node the node to inspect
     * @return the parent
     */
    public static EditNode requireParent(EditNode node) {
        if (node == null || node.getParent() == null) {
            throw new IllegalArgumentException("Node must have a parent");
        }
        if (node.getParent().getEditId() < 0) {
            throw new IllegalArgumentException("ParentId cannot be negative");
        }
        return node.getParent();
    }

    /**
     * Validates that the given node is not null.If the node is null, an
     * IllegalArgumentException is thrown with a descriptive message. This
     * method is used to ensure that a valid node is provided when creating an
     * AddNodeCommand, as a null node would not be meaningful in the context of
     * adding a node to the tree.
     *
     * @param node the node to validate
     * @param index at index
     * @return the validated node if valid
     * @throws IllegalArgumentException if the node is null
     */
    public static EditNodeAbstract requireNode(EditNodeAbstract node, int index) {
        if (node == null) {
            throw new IllegalArgumentException("Node at index " + index + " cannot be null");
        }
        return node;
    }

    /**
     * Validates that the given node is not null. If the node is null, an
     * IllegalArgumentException is thrown with a descriptive message. This
     * method is used to ensure that a valid node is provided when creating an
     * AddNodeCommand, as a null node would not be meaningful in the context of
     * adding a node to the tree.
     *
     * @param node the node to validate
     * @return the validated node if valid
     * @throws IllegalArgumentException if the node is null
     */
    public static EditNodeAbstract requireNode(EditNodeAbstract node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        return node;
    }

    /**
     * Checks if the specified node has any parent in the given set of nodes.
     * This method iterates through the set of nodes and checks if the provided
     * node has any of them as a parent. If a parent is found, it returns true;
     * otherwise, it returns false after checking all nodes in the set. This is
     * useful for determining if a node is a descendant of any node in a
     * collection, which can help in managing relationships between nodes in a
     * tree structure.
     *
     * @param club the set of nodes to check against
     * @param node the node for which to check parent relationships
     * @return true if the node has a parent in the set, false otherwise
     */
    public static boolean hasParentIn(Set<EditNodeAbstract> club, EditNodeAbstract node) {
        for (EditNodeAbstract member : club) {
            if (node.hasParent(member)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes any nodes from the given set that are parents of the specified
     * node. This method iterates through the set of nodes and checks if any of
     * them are parents of the provided node. If a parent is found, it is
     * removed from the set. This is useful for maintaining a collection of
     * nodes that should not include any ancestors of a particular node,
     * ensuring that only relevant nodes are kept in the set.
     *
     * @param club the set of nodes from which to remove parents
     * @param node the node for which to remove parent nodes from the set
     */
    public static void removeChildrenOf(Set<EditNodeAbstract> club, EditNodeAbstract node) {
        for (EditNodeAbstract member : club) {
            if (member.hasParent(node)) {
                club.remove(member);
                return;
            }
        }
    }

    /**
     * Returns the union of two arrays of nodes, eliminating duplicates. If the
     * second array is null, only the first array is considered. The resulting
     * array contains all unique nodes from both input arrays. This method is
     * useful for combining sets of nodes while ensuring that each node appears
     * only once in the result.
     *
     * @param nodes1 the first array of nodes to union
     * @param nodes2orNull the second array of nodes to union, which may be null
     * @return an array containing the unique nodes from both input arrays
     */
    public static EditNodeAbstract[] unionNodes(EditNodeAbstract[] nodes1, EditNodeAbstract[] nodes2orNull) {
        Set<EditNodeAbstract> union = new java.util.HashSet<>();
        for (EditNodeAbstract node : nodes1) {
            union.add(node);
        }
        if (nodes2orNull != null) {
            for (EditNodeAbstract node : nodes2orNull) {
                union.add(node);
            }
        }
        return union.toArray(new EditNodeAbstract[union.size()]);
    }
}
