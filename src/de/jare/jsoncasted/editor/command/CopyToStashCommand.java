/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.clipboard.ClipboardManager;
import de.jare.jsoncasted.editor.clipboard.ClipboardStash;
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
 * Command that copies node(s) to a clipboard stash without removing them from
 * the tree.
 *
 * <p>
 * When executed, deep copies of the node(s) are stored in the clipboard stash.
 * When undone, the stash content is restored to its original state.</p>
 *
 * <p>
 * For multi-selection, descendant nodes are ignored if one of their ancestors
 * is already part of the copy set. This prevents duplicate content in the
 * stash.</p>
 *
 * <p>
 * The command uses MovementEntry[] for storing node snapshots, similar to
 * AddNodeCommand but without actually adding nodes to the tree.</p>
 *
 * @author Jansuch Rentenatus
 */
public class CopyToStashCommand extends AbstractEditCommand {

    private final ClipboardManager clipboardManager;
    private final String stashName;
    private final MovementEntry[] entries;
    private EditNodeAbstract[] originalStashContent;

    /**
     * Creates a new copy command for a single node.
     *
     * @param clipboardManager the clipboard manager
     * @param stashName the name of the target stash
     * @param node the node to copy
     */
    public CopyToStashCommand(ClipboardManager clipboardManager, String stashName, EditNodeAbstract node) {
        this(clipboardManager, stashName, new EditNodeAbstract[]{requireNode(node, 0)});
    }

    /**
     * Creates a new copy command for a single node using the active stash.
     *
     * @param clipboardManager the clipboard manager
     * @param node the node to copy
     */
    public CopyToStashCommand(ClipboardManager clipboardManager, EditNodeAbstract node) {
        this(clipboardManager, clipboardManager.getActiveStashName(), node);
    }

    /**
     * Creates a new copy command for multiple nodes.
     *
     * @param clipboardManager the clipboard manager
     * @param stashName the name of the target stash
     * @param nodes the nodes to copy
     */
    public CopyToStashCommand(ClipboardManager clipboardManager, String stashName, EditNodeAbstract[] nodes) {
        super(CommandType.COPY_NODE);

        if (clipboardManager == null) {
            throw new IllegalArgumentException("ClipboardManager cannot be null");
        }
        if (stashName == null || stashName.trim().isEmpty()) {
            throw new IllegalArgumentException("Stash name cannot be null or empty");
        }
        if (nodes == null || nodes.length == 0) {
            throw new IllegalArgumentException("Nodes cannot be null or empty");
        }

        this.clipboardManager = clipboardManager;
        this.stashName = stashName;
        this.entries = toEntries(nodes);

        // Capture original stash content for undo
        ClipboardStash stash = clipboardManager.getStash(stashName);
        this.originalStashContent = stash != null ? stash.getNodes() : new EditNodeAbstract[0];

        if (this.entries.length == 1) {
            setDescription("Copy node: " + this.entries[0].snapshot.getName() + " to stash '" + stashName + "'");
        } else {
            setDescription("Copy " + this.entries.length + " nodes to stash '" + stashName + "'");
        }
    }

    /**
     * Creates a copy command from entries array.
     *
     * @param clipboardManager the clipboard manager
     * @param stashName the name of the target stash
     * @param entries array of entries to copy
     */
    public CopyToStashCommand(ClipboardManager clipboardManager, String stashName, MovementEntry[] entries) {
        super(CommandType.COPY_NODE);

        if (clipboardManager == null) {
            throw new IllegalArgumentException("ClipboardManager cannot be null");
        }
        if (stashName == null || stashName.trim().isEmpty()) {
            throw new IllegalArgumentException("Stash name cannot be null or empty");
        }
        if (entries == null || entries.length == 0) {
            throw new IllegalArgumentException("Entries cannot be null or empty");
        }

        this.clipboardManager = clipboardManager;
        this.stashName = stashName;
        this.entries = copyAndValidate(entries);

        // Capture original stash content for undo
        ClipboardStash stash = clipboardManager.getStash(stashName);
        this.originalStashContent = stash != null ? stash.getNodes() : new EditNodeAbstract[0];

        if (this.entries.length == 1) {
            setDescription("Copy node to stash '" + stashName + "'");
        } else {
            setDescription("Copy " + this.entries.length + " nodes to stash '" + stashName + "'");
        }
    }

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
        if (tree == null) {
            return CommandAvailability.disallowed("editor.command.tree.missing");
        }

        // Check if stash exists
        ClipboardStash stash = clipboardManager.getStash(stashName);
        if (stash == null) {
            return CommandAvailability.disallowed(
                    "editor.command.copy.stashMissing",
                    stashName);
        }

        String seenTypeKey = null;

        for (int i = 0; i < entries.length; i++) {
            MovementEntry entry = entries[i];
            EditNode node = tree.findNodeByIdAndRange(entry);

            if (node == null) {
                return CommandAvailability.disallowed(
                        "editor.command.copy.nodeMissing",
                        Long.toString(entry.nodeId),
                        Integer.toString(i));
            }

            String typeKey = node.getTypeKey();
            if (typeKey == null || typeKey.trim().isEmpty()) {
                return CommandAvailability.disallowed(
                        "editor.command.copy.unsupportedNodeType",
                        String.valueOf(typeKey),
                        Long.toString(entry.nodeId),
                        Integer.toString(i));
            }

            if (seenTypeKey == null) {
                seenTypeKey = typeKey;
            } else if (!seenTypeKey.equals(typeKey)) {
                return CommandAvailability.disallowed(
                        "editor.command.copy.mixedNodeTypes",
                        stashName,
                        Integer.toString(i));
            }
        }

        return CommandAvailability.allowed("editor.command.copy.allowed", stashName);
    }

    @Override
    protected CommandResult doExecute(EditTree tree, boolean redoAction) {
        CommandAvailability availability = check(tree);
        if (!availability.isAllowed()) {
            return disallowed(availability.getMessageKey(), entries);
        }

        // Store node copies in stash via ClipboardManager
        clipboardManager.copyToStash(stashName, tree, entries);

        // Collect the copied nodes for the result
        EditNodeAbstract[] copiedNodes = collectNodes(tree);

        return new CommandResult(
                this,
                CommandAction.EXECUTE,
                copiedNodes,
                null, // templateEntries
                copiedNodes,
                null,
                null,
                null,
                ON_CLIPBOARD_ACTIONS
        );
    }

    @Override
    public CommandResult doUndo(EditTree tree) {
        // Restore original stash content
        ClipboardStash stash = clipboardManager.getStash(stashName);
        if (stash != null) {
            stash.setNodes(originalStashContent);
        }

        return new CommandResult(
                this,
                CommandAction.UNDO,
                originalStashContent,
                null, // templateEntries
                null,
                originalStashContent,
                null,
                null,
                ON_CLIPBOARD_ACTIONS
        );
    }

    /**
     * Returns a defensive copy of the entries array.
     *
     * @return a copy of the entries array
     */
    public MovementEntry[] getEntries() {
        return Arrays.copyOf(entries, entries.length);
    }

    /**
     * Returns the stash name.
     *
     * @return the stash name
     */
    public String getStashName() {
        return stashName;
    }

    /**
     * Returns the clipboard manager.
     *
     * @return the clipboard manager
     */
    public ClipboardManager getClipboardManager() {
        return clipboardManager;
    }

    private EditNodeAbstract[] collectNodes(EditTree tree) {
        EditNodeAbstract[] nodes = new EditNodeAbstract[entries.length];
        for (int i = 0; i < entries.length; i++) {
            MovementEntry entry = entries[i];
            EditNodeAbstract node = tree.findNodeByIdAndRange(entry);
            nodes[i] = node != null ? node : entry.snapshot;
        }
        return nodes;
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
            int index = parent != null ? parent.getChildIndex(node) : -1;

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

    @Override
    public String toString() {
        return "CopyToStashCommand[stash='" + stashName + "', nodeCount=" + entries.length + "]";
    }
}
