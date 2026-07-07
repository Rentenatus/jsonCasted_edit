/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.SimpleEntry;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable result object returned by command execution, undo, and redo
 * operations.
 *
 * <p>
 * A command result describes the triggering command, the executed action, and
 * the node sets affected by the operation. Depending on the command type, nodes
 * may appear in one or more semantic groups such as added, removed, or updated
 * nodes.</p>
 *
 * @author Jansuch Rentenatus
 */
public final class CommandResult {

    private final EditCommand trigger;
    private final CommandAction action;
    private final EditNodeAbstract[] affectedNodes;
    private final SimpleEntry[] templateEntries;
    private final EditNodeAbstract[] addedNodes;
    private final EditNodeAbstract[] removedNodes;
    private final EditNodeAbstract[] updatedNodes;
    private final EditNodeAbstract[] failedNodes;
    private JackUpdateAction[] updateActions;
    private String messageKey = null;

    /**
     * Creates a new command result.
     *
     * @param trigger the command that produced this result
     * @param action the action that was performed
     * @param affectedNodes all nodes affected by the operation
     * @param templateEntries entries that were used as templates by the
     * operation
     * @param addedNodes nodes that were added by the operation
     * @param removedNodes nodes that were removed by the operation
     * @param updatedNodes nodes that were updated by the operation
     * @param failedNodes nodes that failed to be moved
     * @param updateActions recommended action for update
     * @throws NullPointerException if {@code trigger} or {@code action} is
     * {@code null}
     */
    public CommandResult(
            EditCommand trigger, CommandAction action, EditNodeAbstract[] affectedNodes, SimpleEntry[] templateEntries, EditNodeAbstract[] addedNodes,
            EditNodeAbstract[] removedNodes, EditNodeAbstract[] updatedNodes, EditNodeAbstract[] failedNodes,
            JackUpdateAction[] updateActions) {
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.action = Objects.requireNonNull(action, "action");
        this.affectedNodes = affectedNodes != null ? affectedNodes.clone() : new EditNodeAbstract[0];
        this.templateEntries = templateEntries != null ? templateEntries.clone() : new SimpleEntry[0];
        this.addedNodes = addedNodes != null ? addedNodes.clone() : new EditNodeAbstract[0];
        this.removedNodes = removedNodes != null ? removedNodes.clone() : new EditNodeAbstract[0];
        this.updatedNodes = updatedNodes != null ? updatedNodes.clone() : new EditNodeAbstract[0];
        this.failedNodes = failedNodes != null ? failedNodes.clone() : new EditNodeAbstract[0];
        this.updateActions = updateActions != null ? updateActions : new JackUpdateAction[0];
    }

    /**
     * Returns the command that produced this result.
     *
     * @return the triggering command
     */
    public EditCommand getTrigger() {
        return trigger;
    }

    /**
     * Returns the action represented by this result.
     *
     * @return the command action
     */
    public CommandAction getAction() {
        return action;
    }

    /**
     * Returns all nodes affected by the operation.
     *
     * @return a defensive copy of the affected nodes
     */
    public EditNodeAbstract[] getAffectedNodes() {
        return affectedNodes.clone();
    }

    /**
     * Returns the entries used as templates by the operation.
     *
     * @return a defensive copy of the template entries
     */
    public SimpleEntry[] getTemplateEntries() {
        return templateEntries.clone();
    }

    /**
     * Returns the nodes added by the operation.
     *
     * @return a defensive copy of the added nodes
     */
    public EditNodeAbstract[] getAddedNodes() {
        return addedNodes.clone();
    }

    /**
     * Returns the nodes removed by the operation.
     *
     * @return a defensive copy of the removed nodes
     */
    public EditNodeAbstract[] getRemovedNodes() {
        return removedNodes.clone();
    }

    /**
     * Returns the nodes updated by the operation.
     *
     * @return a defensive copy of the updated nodes
     */
    public EditNodeAbstract[] getUpdatedNodes() {
        return updatedNodes.clone();
    }

    /**
     * Returns the nodes that failed to be moved by the operation.
     *
     * @return a defensive copy of the failed nodes
     */
    public EditNodeAbstract[] getFailedNodes() {
        return failedNodes.clone();
    }

    /**
     * Returns the recommended update actions for this result.
     *
     * @return a defensive copy of the update actions
     */
    public JackUpdateAction[] getUpdateActions() {
        return updateActions.clone();
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    /**
     * Returns a compact debug representation of this result.
     *
     * @return the formatted debug string
     */
    @Override
    public String toString() {
        return "CommandResult{"
                + "action=" + action
                + ", trigger=" + formatTrigger(trigger)
                + ", affectedNodes=" + formatNodes(affectedNodes)
                + ", templateEntries=" + java.util.Arrays.toString(templateEntries)
                + ", addedNodes=" + formatNodes(addedNodes)
                + ", removedNodes=" + formatNodes(removedNodes)
                + ", updatedNodes=" + formatNodes(updatedNodes)
                + ", updateActions=" + (updateActions.length > 0 ? java.util.Arrays.toString(updateActions) : "[]")
                + ", message='" + messageKey
                + "'}";
    }

    /**
     * Formats the triggering command for debug output.
     *
     * @param trigger the command to format
     * @return the formatted command string
     */
    private static String formatTrigger(EditCommand trigger) {
        if (trigger == null) {
            return "null";
        }
        return trigger.getClass().getSimpleName() + "[" + trigger.toString() + "]";
    }

    /**
     * Formats a node array for debug output.
     *
     * @param nodes the nodes to format
     * @return the formatted node list
     */
    private static String formatNodes(EditNode[] nodes) {
        if (nodes == null || nodes.length == 0) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < nodes.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(formatNode(nodes[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Formats a single node for debug output.
     *
     * @param node the node to format
     * @return the formatted node string
     */
    private static String formatNode(EditNode node) {
        if (node == null) {
            return "null";
        }

        EditNode parent = node.getParent();
        long parentId = parent != null ? parent.getEditId() : -1;

        return "EditNode{"
                + "id=" + node.getEditId()
                + ", name='" + node.getName() + '\''
                + ", parentId=" + parentId
                + '}';
    }

    CommandResult addActions(JackUpdateAction[] actions) {
        int oldLen = updateActions.length;
        updateActions = Arrays.copyOf(updateActions, oldLen + actions.length);
        System.arraycopy(actions, 0, updateActions, oldLen, actions.length);
        return this;
    }

}
