/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditTree;

/**
 * Interface for all commands that can be executed and undone in the editor.
 *
 * Implementations are expected to modify the provided {@link EditTree} and
 * return a {@link CommandResult} describing the affected nodes.
 *
 * @author Jansuch Rentenatus
 */
public interface EditCommand {

    /**
     * Checks
     *
     * @param tree the tree to modify
     * @return availability
     */
    public CommandAvailability check(EditTree tree);

    /**
     * Executes this command on the given tree.
     *
     * @param tree the tree to modify
     * @param redoAction
     * @return the result describing the changes caused by this execution
     */
    CommandResult execute(EditTree tree, boolean redoAction);

    /**
     * Mark this command as skipped.
     */
    public void skipped();

    /**
     * Undoes this command on the given tree.
     *
     * @param tree the tree to modify
     * @return the result describing the changes caused by this undo operation
     */
    CommandResult undo(EditTree tree);

    /**
     * Returns a human-readable description of this command.
     *
     * @return the command description
     */
    String getDescription();

    /**
     * Returns the last message associated with this command, if the command has
     * been failed or has warnings.
     *
     * @return message after last execution (first action or redo) or undo, or
     * null if no message is set
     */
    String getLastMessage();

    /**
     * Returns the command type.
     *
     * @return the command type
     */
    CommandType getType();

    default String getTypeText() {
        CommandType type = getType();
        return type == null ? "command.null" : type.getLabelKey();
    }

    /**
     * Fixed set of supported command categories.
     */
    enum CommandType {
        ADD_NODE("command.add_node"),
        DELETE_NODE("command.delete_node"),
        MOVE_NODE("command.move_node"),
        SET_VALUE("command.set_value"),
        RENAME_NODE("command.rename_node"),
        SET_ATTRIBUTE("command.set_attribute"),
        CUT_NODE("command.cut_node"),
        COPY_NODE("command.copy_node"),
        PASTE_NODE("command.paste_node"),
        OTHER("command.other");

        private final String labelKey;

        private CommandType(String labelKey) {
            this.labelKey = labelKey;
        }

        public String getLabelKey() {
            return labelKey;
        }
    }
}
