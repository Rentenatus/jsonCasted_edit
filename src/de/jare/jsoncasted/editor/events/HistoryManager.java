/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.events;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.core.EditTree;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Manages the undo and redo history for the editor. Keeps track of executed
 * commands and allows undoing and redoing changes.
 *
 * @author Jansuch Rentenatus
 */
public class HistoryManager {

    private final EditTree tree;
    private final EventBus eventBus;

    private final Deque<EditCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditCommand> redoStack = new ArrayDeque<>();

    private int limit = 100;

    /**
     * Creates a new HistoryManager for the specified tree.
     *
     * @param tree the edit tree this manager operates on
     */
    public HistoryManager(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }
        this.tree = tree;
        this.eventBus = new EventBus();
    }

    /**
     * Adds a listener for a specific event type. The listener will be notified
     * whenever an event of the specified type is fired.
     *
     * @param listener the consumer to be called when an event is fired
     * @throws IllegalArgumentException if listener is null
     */
    public void addListener(HistoryListener listener) {
        if (eventBus == null) {
            throw new NullPointerException("EventBus not set.");
        }
        eventBus.addListener(listener);
    }

    /**
     * Adds a listener for a specific event type at a specified priority level.
     * The listener will be notified whenever an event of the specified type is fired.
     *
     * @param level the priority level of this listener (higher values are notified first)
     * @param listener the consumer to be called when an event is fired
     * @throws IllegalArgumentException if listener is null
     */
    public void addListener(int level, HistoryListener listener) {
        if (eventBus == null) {
            throw new NullPointerException("EventBus not set.");
        }
        eventBus.addListener(level, listener);
    }

    /**
     * Removes a listener for a specific event type.
     *
     * @param listener the consumer to remove
     * @return true if the listener was removed
     */
    public boolean removeListener(HistoryListener listener) {
        if (eventBus == null) {
            return false;
        }
        return eventBus.removeListener(listener);
    }

    /**
     * Executes the given command and adds it to the undo history. The redo
     * history is cleared when a new command is executed.
     *
     * @param command the command to execute
     * @return the command result, or null if command is null
     */
    public CommandResult execute(EditCommand command) {
        if (command == null) {
            return null;
        }

        CommandResult result = command.execute(tree, false);

        undoStack.push(command);
        redoStack.clear();
        trimToLimit();

        fireHistoryEvent(HistoryEvent.ChangeType.CMD_EXECUTED,
                command,
                result
        );

        return result;
    }

    /**
     * Undoes the last executed command. The undone command is moved to the redo
     * stack.
     *
     * @return the undo result, or null if nothing to undo
     */
    public CommandResult undo() {
        if (!canUndo()) {
            return null;
        }

        EditCommand command = undoStack.pop();
        CommandResult result = command.undo(tree);
        redoStack.push(command);

        fireHistoryEvent(HistoryEvent.ChangeType.CMD_UNDONE,
                command,
                result
        );

        return result;
    }

    /**
     * Redoes the last undone command. The redone command is moved back to the
     * undo stack.
     *
     * @return the redo result, or null if nothing to redo
     */
    public CommandResult redo() {
        if (!canRedo()) {
            return null;
        }

        EditCommand command = redoStack.pop();
        CommandResult redoResult = command.execute(tree, true);
        undoStack.push(command);

        fireHistoryEvent(HistoryEvent.ChangeType.CMD_REDONE,
                command,
                redoResult
        );

        return redoResult;
    }

    /**
     * Skips the last undone command without executing it. The skipped command
     * is moved from redo stack to undo stack without execution.
     *
     * @return the skipped command, or null if nothing to skip
     */
    public EditCommand skipRedo() {
        if (!canRedo()) {
            return null;
        }

        EditCommand command = redoStack.pop();
        undoStack.push(command);
        command.skipped();

        fireHistoryEvent(
                HistoryEvent.ChangeType.CMD_SKIPPED,
                command,
                null
        );

        return command;
    }

    /**
     * Returns whether an undo operation is available.
     *
     * @return true if there are commands to undo
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Returns whether a redo operation is available.
     *
     * @return true if there are commands to redo
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Clears all undo and redo history.
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();

        fireHistoryEvent(HistoryEvent.ChangeType.HIST_CLEARED,
                null,
                null
        );
    }

    /**
     * Sets the maximum number of commands to keep in history. Older commands
     * are discarded when the limit is exceeded.
     *
     * @param limit the maximum number of commands (must be > 0)
     */
    public void setLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be > 0");
        }
        this.limit = limit;
        trimToLimit();
    }

    /**
     * Returns the current history limit.
     *
     * @return the limit
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Returns the number of commands in the undo stack.
     *
     * @return the undo stack size
     */
    public int getUndoSize() {
        return undoStack.size();
    }

    /**
     * Returns the number of commands in the redo stack.
     *
     * @return the redo stack size
     */
    public int getRedoSize() {
        return redoStack.size();
    }

    /**
     * Returns the total number of commands in history.
     *
     * @return the total size
     */
    public int getTotalSize() {
        return undoStack.size() + redoStack.size();
    }

    /**
     * Returns an iterable over the undo stack commands.
     * <p>
     * The commands are returned in the order they would be undone (most recent first).
     * </p>
     *
     * @return an iterable over the undo stack commands
     */
    public Iterable<EditCommand> getUndoCommands() {
        return () -> undoStack.iterator();
    }

    /**
     * Returns an iterable over the redo stack commands.
     * <p>
     * The commands are returned in the order they would be redone (most recently undone first).
     * </p>
     *
     * @return an iterable over the redo stack commands
     */
    public Iterable<EditCommand> getRedoCommands() {
        return () -> redoStack.iterator();
    }

    /**
     * Trims the undo stack to the configured limit.
     */
    private void trimToLimit() {
        while (undoStack.size() > limit) {
            undoStack.removeLast();
        }
    }

    /**
     * Returns the event bus used by this manager.
     *
     * @return the event bus, may be null
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    private void fireHistoryEvent(
            HistoryEvent.ChangeType changeType,
            EditCommand command,
            CommandResult result) {
        if (eventBus != null) {
            eventBus.fireEvent(new HistoryEvent(
                    this,
                    changeType,
                    command,
                    result,
                    undoStack.size(),
                    redoStack.size()
            ));
        }
    }

    @Override
    public String toString() {
        return "HistoryManager[undo=" + undoStack.size()
                + ", redo=" + redoStack.size()
                + ", limit=" + limit + "]";
    }

    /**
     * Returns the number of commands in the undo stack.
     * <p>
     * This is a convenience method equivalent to {@link #getUndoSize()}.
     * </p>
     *
     * @return the number of commands that can be undone
     */
    public int undoSize() {
        return undoStack.size();
    }

    /**
     * Returns the number of commands in the redo stack.
     * <p>
     * This is a convenience method equivalent to {@link #getRedoSize()}.
     * </p>
     *
     * @return the number of commands that can be redone
     */
    public int redoSize() {
        return redoStack.size();
    }

    /**
     * Returns the command at the specified index in the redo stack.
     *
     * @param index the index of the command to retrieve (0 = most recently undone)
     * @return the command at the specified index, or null if index is out of bounds
     */
    public EditCommand getRedo(int index) {
        if (index < 0 || index >= redoStack.size()) {
            return null;
        }

        int i = 0;
        for (EditCommand cmd : redoStack) {
            if (i++ == index) {
                return cmd;
            }
        }
        return null;
    }

    /**
     * Returns the command at the specified index in the undo stack.
     *
     * @param index the index of the command to retrieve (0 = most recently executed)
     * @return the command at the specified index, or null if index is out of bounds
     */
    public EditCommand getUndo(int index) {
        if (index < 0 || index >= undoStack.size()) {
            return null;
        }

        int i = 0;
        for (EditCommand cmd : undoStack) {
            if (i++ == index) {
                return cmd;
            }
        }
        return null;
    }

    /**
     * Returns labels for the undo stack commands suitable for display in UI.
     * <p>
     * Each label array contains formatted strings describing the command's position,
     * type, and description.
     * </p>
     *
     * @param max the maximum number of labels to return
     * @return a list of string arrays, each representing a command label
     */
    public List<String[]> getUndoLabels(int max) {
        int count = Math.min(max, undoStack.size());
        List<String[]> result = new ArrayList<>(count);

        int i = 0;
        for (EditCommand cmd : undoStack) {
            if (i >= count) {
                break;
            }
            result.add(new String[]{
                String.valueOf(i + 1), ": ", cmd.getTypeText(), " - ", cmd.getDescription()
            });
            i++;
        }
        return result;
    }

    /**
     * Returns labels for the redo stack commands suitable for display in UI.
     * <p>
     * Each label array contains formatted strings describing the command's position,
     * type, and description.
     * </p>
     *
     * @param max the maximum number of labels to return
     * @return a list of string arrays, each representing a command label
     */
    public List<String[]> getRedoLabels(int max) {
        int count = Math.min(max, redoStack.size());
        List<String[]> result = new ArrayList<>(count);

        int i = 0;
        for (EditCommand cmd : redoStack) {
            if (i >= count) {
                break;
            }
            result.add(new String[]{
                String.valueOf(i + 1), ": ", cmd.getTypeText(), " - ", cmd.getDescription()
            });
            i++;
        }
        return result;
    }
}
