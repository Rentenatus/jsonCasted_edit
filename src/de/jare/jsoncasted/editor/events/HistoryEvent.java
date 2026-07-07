/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.events;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import java.util.Objects;

/**
 * Event fired when the history state changes (command executed, undone, redone,
 * skipped, cleared).
 *
 * @author Jansuch Rentenatus
 */
public class HistoryEvent implements EditEvent {

    /**
     * Type of history change.
     */
    public enum ChangeType {
        /**
         * A command was executed
         */
        CMD_EXECUTED,
        /**
         * A command was undone
         */
        CMD_UNDONE,
        /**
         * A command was redone
         */
        CMD_REDONE,
        /**
         * A command was skipped (moved from redo to undo without execution)
         */
        CMD_SKIPPED,
        /**
         * History was cleared
         */
        HIST_CLEARED
    }

    private final HistoryManager source;
    private final long timestamp;
    private final ChangeType changeType;
    private final EditCommand command;
    private final CommandResult result;
    private final int undoSize;
    private final int redoSize;

    /**
     * Creates a new history event.
     *
     * @param source the source of this event
     * @param changeType the type of history change
     * @param command the command involved (may be null for CLEARED)
     * @param result the command result (may be null for CLEARED or SKIPPED)
     * @param undoSize the current size of the undo stack
     * @param redoSize the current size of the redo stack
     */
    public HistoryEvent(
            HistoryManager source,
            ChangeType changeType,
            EditCommand command,
            CommandResult result,
            int undoSize,
            int redoSize) {
        this.source = Objects.requireNonNull(source, "source");
        this.timestamp = System.currentTimeMillis();
        this.changeType = Objects.requireNonNull(changeType, "changeType");
        this.command = command;
        this.result = result;
        this.undoSize = undoSize;
        this.redoSize = redoSize;
    }

    @Override
    public HistoryManager getSource() {
        return source;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("History ").append(changeType).append(": ");

        if (command != null) {
            sb.append("command=").append(command.getDescription());
        } else {
            sb.append("command=null");
        }

        if (result != null) {
            sb.append(", action=").append(result.getAction());
            sb.append(", affectedNodes=").append(result.getAffectedNodes().length);
            sb.append(", templateEntries=").append(result.getTemplateEntries().length);
            sb.append(", addedNodes=").append(result.getAddedNodes().length);
            sb.append(", removedNodes=").append(result.getRemovedNodes().length);
            sb.append(", updatedNodes=").append(result.getUpdatedNodes().length);
        }

        sb.append(", undoSize=").append(undoSize);
        sb.append(", redoSize=").append(redoSize);
        return sb.toString();
    }

    /**
     * Returns the type of history change.
     *
     * @return the change type
     */
    public ChangeType getChangeType() {
        return changeType;
    }

    /**
     * Returns the command involved in this event.
     *
     * @return the command, may be null
     */
    public EditCommand getCommand() {
        return command;
    }

    /**
     * Returns the command result associated with this event.
     *
     * @return the result, may be null
     */
    public CommandResult getResult() {
        return result;
    }

    /**
     * Returns the current size of the undo stack.
     *
     * @return the undo stack size
     */
    public int getUndoSize() {
        return undoSize;
    }

    /**
     * Returns the current size of the redo stack.
     *
     * @return the redo stack size
     */
    public int getRedoSize() {
        return redoSize;
    }

    @Override
    public String toString() {
        return "HistoryEvent[" + changeType
                + ", command=" + (command != null ? command.getClass().getSimpleName() : "null")
                + ", resultAction=" + (result != null ? result.getAction() : "null")
                + ", affectedNodes=" + (result != null ? result.getAffectedNodes().length : 0)
                + ", templateEntries=" + (result != null ? result.getTemplateEntries().length : 0)
                + ", undoSize=" + undoSize
                + ", redoSize=" + redoSize + "]";
    }
}
