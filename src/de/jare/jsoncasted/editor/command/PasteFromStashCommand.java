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
import de.jare.jsoncasted.editor.core.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command that pastes nodes from a clipboard stash into the tree.
 *
 * <p>
 * When executed, copies of the stash content are inserted at the specified
 * parent and index. When undone, the pasted nodes are removed from the
 * tree.</p>
 *
 * <p>
 * The command uses MovementEntry[] for storing the target positions, similar to
 * AddNodeCommand but with stash as the source of the nodes.</p>
 *
 * @author Jansuch Rentenatus
 */
public class PasteFromStashCommand extends AbstractEditCommand {

    private final ClipboardManager clipboardManager;
    private final String stashName;
    private final MovementEntry[] entries;
    private SimpleEntry[] pastedEntries;

    /**
     * Creates a command to paste from a stash to a parent node at a specific
     * index.
     *
     * @param clipboardManager the clipboard manager
     * @param stashName the name of the source stash
     * @param parent the parent node where nodes will be inserted
     * @param index the index at which to insert the nodes, or -1 to append
     */
    public PasteFromStashCommand(ClipboardManager clipboardManager,
            String stashName,
            EditNode parent,
            int index) {
        super(CommandType.PASTE_NODE);

        if (clipboardManager == null) {
            throw new IllegalArgumentException("ClipboardManager cannot be null");
        }
        if (stashName == null || stashName.trim().isEmpty()) {
            throw new IllegalArgumentException("Stash name cannot be null or empty");
        }
        if (parent == null) {
            throw new IllegalArgumentException("Parent cannot be null");
        }
        if (index < -1) {
            throw new IllegalArgumentException("Index cannot be < -1");
        }

        this.clipboardManager = clipboardManager;
        this.stashName = stashName;
        this.pastedEntries = new SimpleEntry[0];

        // Create entries for all nodes in the stash
        ClipboardStash stash = clipboardManager.getStash(stashName);
        if (stash == null) {
            throw new IllegalArgumentException("Stash '" + stashName + "' does not exist");
        }

        EditNodeAbstract[] stashNodes = stash.getNodes();
        if (stashNodes == null || stashNodes.length == 0) {
            throw new IllegalArgumentException("Stash '" + stashName + "' is empty");
        }

        this.entries = new MovementEntry[stashNodes.length];
        for (int i = 0; i < stashNodes.length; i++) {
            final int targetIndex = (index == -1) ? parent.getChildCount() + i : index + i;

            final EditNodeAbstract entry = stashNodes[i];
            this.entries[i] = new MovementEntry(
                    entry.getEditId(),
                    entry.getLeftRange(),
                    entry.getTimesRange(),
                    parent.getEditId(),
                    parent.getLeftRange(),
                    parent.getTimesRange(),
                    targetIndex,
                    entry.deepCopy(false)
            );
        }

        if (stashNodes.length == 1) {
            setDescription("Paste from stash '" + stashName + "' to parent " + parent.getEditId()
                    + (index >= 0 ? " at index " + index : " (append)"));
        } else {
            setDescription("Paste " + stashNodes.length + " nodes from stash '" + stashName + "' to parent " + parent.getEditId()
                    + (index >= 0 ? " at index " + index : " (append)"));
        }
    }

    /**
     * Creates a command to paste from a stash to a parent node (append).
     *
     * @param clipboardManager the clipboard manager
     * @param stashName the name of the source stash
     * @param parent the parent node where nodes will be inserted
     */
    public PasteFromStashCommand(ClipboardManager clipboardManager,
            String stashName,
            EditNode parent) {
        this(clipboardManager, stashName, parent, -1);
    }

    /**
     * Creates a command to paste from the active stash to a parent node at a
     * specific index.
     *
     * @param clipboardManager the clipboard manager
     * @param parent the parent node where nodes will be inserted
     * @param index the index at which to insert the nodes, or -1 to append
     */
    public PasteFromStashCommand(ClipboardManager clipboardManager,
            EditNode parent,
            int index) {
        this(clipboardManager, clipboardManager.getActiveStashName(), parent, index);
    }

    /**
     * Creates a command to paste from the active stash to a parent node
     * (append).
     *
     * @param clipboardManager the clipboard manager
     * @param parent the parent node where nodes will be inserted
     */
    public PasteFromStashCommand(ClipboardManager clipboardManager,
            EditNode parent) {
        this(clipboardManager, clipboardManager.getActiveStashName(), parent, -1);
    }

    /**
     * Creates a paste command from explicit target entries.
     *
     * @param clipboardManager the clipboard manager
     * @param stashName the name of the source stash
     * @param entries target positions
     */
    public PasteFromStashCommand(ClipboardManager clipboardManager,
            String stashName,
            MovementEntry[] entries) {
        super(CommandType.PASTE_NODE);

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
        this.pastedEntries = new SimpleEntry[0];

        if (this.entries.length == 1) {
            setDescription("Paste from stash '" + stashName + "'");
        } else {
            setDescription("Paste " + this.entries.length + " nodes from stash '" + stashName + "'");
        }
    }

    private static MovementEntry[] copyAndValidate(MovementEntry[] entries) {
        MovementEntry[] copy = new MovementEntry[entries.length];

        for (int i = 0; i < entries.length; i++) {
            MovementEntry entry = entries[i];
            if (entry == null) {
                throw new IllegalArgumentException("Entry at index " + i + " cannot be null");
            }
            if (entry.parentEditId < 0) {
                throw new IllegalArgumentException("Entry parentEditId at index " + i + " is invalid");
            }
            if (entry.index < -1) {
                throw new IllegalArgumentException("Entry index at index " + i + " is invalid");
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
                    "editor.command.paste.stashMissing",
                    stashName);
        }

        EditNodeAbstract[] stashNodes = stash.getNodes();
        if (stashNodes == null || stashNodes.length == 0) {
            return CommandAvailability.disallowed(
                    "editor.command.paste.stashEmpty",
                    stashName);
        }

        // Use the standard add check from AbstractEditCommand
        return checkAdd(tree, entries);
    }

    @Override
    protected CommandResult doExecute(EditTree tree, boolean redoAction) {
        CommandAvailability availability = check(tree);
        if (!availability.isAllowed()) {
            return disallowed(availability.getMessageKey(), entries);
        }

        ClipboardStash stash = clipboardManager.getStash(stashName);
        if (stash == null) {
            return disallowed(availability.getMessageKey(), entries);
        }

        // Perform the add operation
        CommandResult result = doAdd(tree, entries, !stash.isFreshlyCut() && !redoAction,
                redoAction ? CommandAction.REDO : CommandAction.EXECUTE);

        // Capture the IDs of the pasted nodes
        if (result != null) {
            stash.setFreshlyCut(false);
            EditNodeAbstract[] addedNodes = result.getAddedNodes();
            this.pastedEntries = new SimpleEntry[addedNodes.length];
            for (int i = 0; i < addedNodes.length; i++) {
                this.pastedEntries[i] = new SimpleEntry(addedNodes[i]);
            }
        }

        return result;
    }

    @Override
    public CommandResult doUndo(EditTree tree) {
        if (pastedEntries.length == 0) {
            return disallowed("editor.command.paste.nodeMissing", entries);
        }

        // Create delete entries for all pasted nodes
        List<MovementEntry> deleteEntries = new ArrayList<>(pastedEntries.length);
        for (int i = 0; i < pastedEntries.length; i++) {
            SimpleEntry pastedEntry = pastedEntries[i];
            EditNodeAbstract node = tree.findNodeByIdAndRange(pastedEntry);
            if (node == null) {
                // Node might have been removed by other means
                continue;
            }

            EditNodeAbstract parent = node.getParent();
            int index = parent != null ? parent.getChildIndex(node) : -1;

            deleteEntries.add(new MovementEntry(
                    node,
                    parent,
                    index
            ));
        }

        // Filter out null entries
        int validCount = 0;
        for (MovementEntry entry : deleteEntries) {
            if (entry != null) {
                validCount++;
            }
        }

        if (validCount == 0) {
            return disallowed("editor.command.paste.noValid", entries);
        }

        MovementEntry[] validEntries = new MovementEntry[validCount];
        int j = 0;
        for (MovementEntry entry : deleteEntries) {
            if (entry != null) {
                validEntries[j++] = entry;
            }
        }

        // Perform the delete operation
        CommandResult result = doDelete(tree, validEntries, CommandAction.UNDO);

        return result;
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

    @Override
    public String toString() {
        return "PasteFromStashCommand["
                + "stash='" + stashName + '\''
                + ", entryCount=" + entries.length
                + "]";
    }
}
