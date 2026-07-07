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
 * Command that cuts node(s) from the tree and stores them in a stash. When
 * executed, the node(s) are removed from their parent(s) and copied to the
 * clipboard stash. When undone, the node(s) are restored from the stash.
 *
 * <p>
 * For multi-selection, descendant nodes are ignored if one of their ancestors
 * is already part of the cut set. This prevents duplicate restoration on
 * undo.</p>
 *
 * <p>
 * The command uses MovementEntry[] for storing node positions and snapshots,
 * similar to DeleteNodeCommand but with clipboard stash integration.</p>
 *
 * @author Jansuch Rentenatus
 */
public class CutToStashCommand extends AbstractEditCommand {

    private final ClipboardManager clipboardManager;
    private final String stashName;
    private final MovementEntry[] entries;
    private EditNodeAbstract[] originalStashContent;

    /**
     * Creates a new cut command for a single node.
     *
     * @param clipboardManager the clipboard manager
     * @param stashName the name of the target stash
     * @param node the node to cut
     */
    public CutToStashCommand(ClipboardManager clipboardManager, String stashName, EditNodeAbstract node) {
        this(clipboardManager, stashName, new EditNodeAbstract[]{requireNode(node, 0)});
    }

    /**
     * Creates a new cut command for a single node using the active stash.
     *
     * @param clipboardManager the clipboard manager
     * @param node the node to cut
     */
    public CutToStashCommand(ClipboardManager clipboardManager, EditNodeAbstract node) {
        this(clipboardManager, clipboardManager.getActiveStashName(), node);
    }

    /**
     * Creates a new cut command for multiple nodes.
     *
     * @param clipboardManager the clipboard manager
     * @param stashName the name of the target stash
     * @param nodes the nodes to cut
     */
    public CutToStashCommand(ClipboardManager clipboardManager, String stashName, EditNodeAbstract[] nodes) {
        super(CommandType.CUT_NODE);

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
            setDescription("Cut node: " + this.entries[0].snapshot.getName() + " to stash '" + stashName + "'");
        } else {
            setDescription("Cut " + this.entries.length + " nodes to stash '" + stashName + "'");
        }
    }

    /**
     * Creates a cut command from entries array.
     *
     * @param clipboardManager the clipboard manager
     * @param stashName the name of the target stash
     * @param entries array of entries to cut
     */
    public CutToStashCommand(ClipboardManager clipboardManager, String stashName, MovementEntry[] entries) {
        super(CommandType.CUT_NODE);

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
            setDescription("Cut node to stash '" + stashName + "'");
        } else {
            setDescription("Cut " + this.entries.length + " nodes to stash '" + stashName + "'");
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
        if (tree == null) {
            return CommandAvailability.disallowed("editor.command.tree.missing");
        }

        // Check if stash exists
        ClipboardStash stash = clipboardManager.getStash(stashName);
        if (stash == null) {
            return CommandAvailability.disallowed(
                    "editor.command.cut.stashMissing",
                    stashName);
        }

        // Use the standard delete check from AbstractEditCommand
        CommandAvailability availability = checkDelete(tree, entries);
        if (!availability.isAllowed()) {
            return availability;
        }

        // Additional check: ensure no ancestor-descendant conflicts in the cut set
        for (int i = 0; i < entries.length; i++) {
            EditNodeAbstract nodeI = entries[i].snapshot;
            for (int j = 0; j < entries.length; j++) {
                if (i != j) {
                    EditNodeAbstract nodeJ = entries[j].snapshot;
                    if (isAncestorOf(nodeI, nodeJ)) {
                        return CommandAvailability.disallowed(
                                "editor.command.cut.containsAncestorAndDescendant",
                                Long.toString(entries[i].nodeId),
                                Long.toString(entries[j].nodeId),
                                Integer.toString(i),
                                Integer.toString(j));
                    }
                }
            }
        }

        return CommandAvailability.allowed("editor.command.cut.allowed", stashName);
    }

    @Override
    protected CommandResult doExecute(EditTree tree, boolean redoAction) {
        CommandAvailability availability = check(tree);
        if (!availability.isAllowed()) {
            return disallowed(availability.getMessageKey(), entries);
        }

        // Store nodes in stash via ClipboardManager
        clipboardManager.cutToStash(stashName, tree, entries);

        // Perform the delete operation
        return doDelete(tree, entries, redoAction ? CommandAction.REDO : CommandAction.EXECUTE)
                .addActions(ON_CLIPBOARD_ACTIONS);

    }

    @Override
    public CommandResult doUndo(EditTree tree) {
        // Restore original stash content
        ClipboardStash stash = clipboardManager.getStash(stashName);
        if (stash != null) {
            stash.setNodes(originalStashContent);
        }

        // Perform the add operation (undo of delete is add)
        return doAdd(tree, entries, false, CommandAction.UNDO)
                .addActions(ON_CLIPBOARD_ACTIONS);
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
                throw new IllegalArgumentException("Node '" + node.getName() + "' has no parent and cannot be cut");
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

    @Override
    public String toString() {
        return "CutToStashCommand[stash='" + stashName + "', nodeCount=" + entries.length + "]";
    }
}
