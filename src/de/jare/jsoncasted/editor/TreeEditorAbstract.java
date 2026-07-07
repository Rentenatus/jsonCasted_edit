/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.events.HistoryListener;
import de.jare.jsoncasted.editor.events.HistoryManager;
import de.jare.jsoncasted.tools.SimpleStringSplitter;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for tree editors that provides common command execution,
 * undo/redo functionality, and tree model validation.
 *
 * <p>
 * This class encapsulates the shared logic between different tree editor
 * implementations, particularly the interaction with a {@link HistoryManager}
 * and the ability to check whether the underlying tree model is available.
 * </p>
 *
 * <p>
 * Subclasses must implement {@link #missTreeModel()} to indicate whether the
 * tree model is currently unavailable (e.g., due to garbage collection or
 * initialization state). This allows uniform handling of null/absent model
 * scenarios across all editor variants.
 * </p>
 *
 * @author Jansuch Rentenatus
 *
 */
public abstract class TreeEditorAbstract implements SimpleStringSplitter {

    /**
     * The history manager used for undo/redo operations.
     */
    protected final HistoryManager historyManager;

    /**
     * Creates a new abstract tree editor with the given tree model and history
     * manager.
     *
     * @param historyManager the history manager to use; must not be
     * {@code null}
     * @throws IllegalArgumentException if historyManager is {@code null}
     */
    protected TreeEditorAbstract(HistoryManager historyManager) {
        if (historyManager == null) {
            throw new IllegalArgumentException("HistoryManager must not be null");
        }
        this.historyManager = historyManager;
    }

    /**
     * Adds a listener for a specific event type. The listener will be notified
     * whenever an event of the specified type is fired.
     *
     * @param listener the consumer to be called when an event is fired
     * @throws IllegalArgumentException if eventType or listener is null
     */
    public void addListener(HistoryListener listener) {
        historyManager.addListener(listener);
    }

    /**
     * Adds a listener for a specific event type.The listener will be notified
     * whenever an event of the specified type is fired.
     *
     * @param level
     * @param listener the consumer to be called when an event is fired
     * @throws IllegalArgumentException if eventType or listener is null
     */
    public void addListener(int level, HistoryListener listener) {
        historyManager.addListener(level, listener);
    }

    /**
     * Removes a listener for a specific event type.
     *
     * @param listener the consumer to remove
     * @return true if the listener was removed
     */
    public boolean removeListener(HistoryListener listener) {
        return historyManager.removeListener(listener);
    }

    /**
     * Sets the maximum number of commands kept in the undo history. Older
     * entries are discarded when the limit is exceeded.
     *
     * @param limit positive maximum size of the undo stack
     */
    public void setLimit(int limit) {
        historyManager.setLimit(limit);
    }

    public boolean hasTreeModel() {
        return true;
    }

    public boolean missTreeModel() {
        return false;
    }

    // -------------------------------------------------------------------------
    // Command execution / history facade
    // -------------------------------------------------------------------------
    /**
     * Executes the given command and records it in the undo history.
     *
     * @param command the command to execute
     * @return the command result, or {@code null} if the tree model is missing
     * or nothing was executed
     */
    public CommandResult executeCommand(EditCommand command) {
        if (command == null || missTreeModel()) {
            return null;
        }
        return historyManager.execute(command);
    }

    /**
     * Returns the configured maximum number of undoable commands.
     *
     * @return current limit
     */
    public int getLimit() {
        return historyManager.getLimit();
    }

    public int size() {
        return undoSize() + redoSize();
    }

    public int undoSize() {
        return historyManager.undoSize();
    }

    public int redoSize() {
        return historyManager.redoSize();
    }

    public int getTotalSize() {
        return historyManager.getTotalSize();
    }

    public EditCommand getRedo(int index) {
        return historyManager.getRedo(index);
    }

    public EditCommand getUndo(int index) {
        return historyManager.getUndo(index);
    }

    /**
     * Undoes the most recently executed command.
     *
     * @return the undo result, or {@code null} if the tree model is missing or
     * no undo is available
     */
    public CommandResult undo() {
        if (missTreeModel()) {
            return null;
        }
        return historyManager.undo();
    }

    /**
     * Redoes the most recently undone command.
     *
     * @return the redo result, or {@code null} if the tree model is missing or
     * no redo is available
     */
    public CommandResult redo() {
        if (missTreeModel()) {
            return null;
        }
        return historyManager.redo();
    }

    /**
     * Skips the current redo command without executing it and moves it back to
     * the undo stack.
     *
     * @return the skipped command, or {@code null} if the tree model is missing
     * or no redo is available
     */
    public EditCommand skipRedo() {
        if (missTreeModel()) {
            return null;
        }
        return historyManager.skipRedo();
    }

    /**
     * Returns whether an undo operation is currently available.
     *
     * @return {@code false} if the tree model is missing; otherwise delegates
     * to the history manager
     */
    public boolean canUndo() {
        if (missTreeModel()) {
            return false;
        }
        return historyManager.canUndo();
    }

    /**
     * Returns whether a redo operation is currently available.
     *
     * @return {@code false} if the tree model is missing; otherwise delegates
     * to the history manager
     */
    public boolean canRedo() {
        if (missTreeModel()) {
            return false;
        }
        return historyManager.canRedo();
    }

    /**
     * Clears the complete undo/redo history.
     */
    public void clearHistory() {
        historyManager.clear();
    }

    public List<String> getUndoLabels(int max) {
        return maskLabels(historyManager.getUndoLabels(max));
    }

    public List<String> getRedoLabels(int max) {
        return maskLabels(historyManager.getRedoLabels(max));
    }

    public List<String> maskLabels(List<String[]> labels) {
        List<String> ret = new ArrayList<>(labels.size());
        for (String[] label : labels) {
            // Hier muss noch die Maskierung von CommandTypeText rein.
            ret.add(simpleConcat(label, ""));
        }
        return ret;
    }

    /**
     * Returns the history manager used by this editor.
     *
     * @return the history manager instance
     */
    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    /**
     * Returns a readable representation of the undo and redo stacks.
     *
     * @return the formatted history output
     */
    public String toHistoryString() {
        if (historyManager == null) {
            return "<no history>";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("Undo[").append(historyManager.getUndoSize()).append("]:\n");
        for (EditCommand cmd : historyManager.getUndoCommands()) {
            sb.append("  - ").append(formatCommand(cmd)).append('\n');
        }

        sb.append("Redo[").append(historyManager.getRedoSize()).append("]:\n");
        for (EditCommand cmd : historyManager.getRedoCommands()) {
            sb.append("  - ").append(formatCommand(cmd)).append('\n');
        }

        return sb.toString();
    }

    /**
     * Formats a command for history output.
     *
     * @param cmd the command to format
     * @return the formatted command string
     */
    public String formatCommand(EditCommand cmd) {
        if (cmd == null) {
            return "null";
        }
        return cmd.getClass().getSimpleName() + "[" + cmd.toString() + "]";
    }

    /**
     * Formats a single node for debug output.
     *
     * @param node the node to format
     * @return the formatted node header
     */
    public String formatNodeHeader(EditNode node) {
        EditNode parent = node.getParent();
        long parentId = parent != null ? parent.getEditId() : -1;

        StringBuilder sb = new StringBuilder();
        sb.append(node.getClass().getSimpleName())
                .append(" {name =").append(node.getName())
                .append(" {id=").append(node.getEditId())
                .append(", parentId=").append(parentId);

        try {
            String text = node.getName();
            if (text != null) {
                sb.append(", text='").append(text).append('\'');
            }
        } catch (Exception ignore) {
            // Some node types might not support getEditText()
        }

        sb.append('}');
        return sb.toString();
    }

    /**
     * Returns the index of the given node within its parent.
     *
     * @param node the node to inspect
     * @return the child index, or {@code -1} if the node has no parent
     */
    public int getIndexInParent(EditNode node) {
        if (node == null || node.getParent() == null) {
            return -1;
        }
        return node.getParent().getChildIndex(node);
    }

}
