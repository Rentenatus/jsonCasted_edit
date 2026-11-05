/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.command.EditCommand.CommandType;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JackAttribut;
import java.util.HashMap;
import java.util.Map;

/**
 * Sets attributes on a captured node - the node travels WITH the command instead of being looked
 * up in the executing tree. The reroll of a whole bundle spans several trees but runs as ONE
 * composite transaction on the main tree's history (concept CONCEPT_ProjectView.md, section 7.1:
 * one undo step rolls the whole reroll back); the node lookups of the standard commands resolve
 * within one tree only, so cross-tree children capture their nodes instead. The attributes patch
 * exactly the keys given: the identity ({@code objektId}) and the reference target
 * ({@code |linkTarget}) of a rerolled node.
 *
 * @author Janusch Rentenatus
 */
public class SetCapturedAttributesCommand extends AbstractEditCommand {

    private final EditNodeAbstract node;
    private final Map<String, JackAttribut> newAttributes;
    private final Map<String, JackAttribut> oldAttributes;

    /**
     * Creates the attribute patch for a captured node.
     *
     * @param node the node to edit (captured - it belongs to the tree it was taken from)
     * @param newAttributes the attribute values to set ({@code "objektId"}, {@code "|linkTarget"});
     * only the given keys change
     */
    public SetCapturedAttributesCommand(EditNodeAbstract node, Map<String, Object> newAttributes) {
        super(CommandType.SET_ATTRIBUTE);
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (newAttributes == null || newAttributes.isEmpty()) {
            throw new IllegalArgumentException("Attributes cannot be null or empty");
        }
        this.node = node;
        this.newAttributes = new HashMap<>();
        this.oldAttributes = new HashMap<>();
        final Map<String, JackAttribut> current = node.getAttributes();
        for (Map.Entry<String, Object> entry : newAttributes.entrySet()) {
            this.newAttributes.put(entry.getKey(), new JackAttribut(entry.getKey(), entry.getValue()));
            final JackAttribut old = current != null ? current.get(entry.getKey()) : null;
            this.oldAttributes.put(entry.getKey(), old != null
                    ? new JackAttribut(entry.getKey(), old.getValue()) : new JackAttribut(entry.getKey(), null));
        }
        setDescription("Set captured attributes " + newAttributes.keySet() + " of node " + node.getEditId());
    }

    @Override
    public CommandAvailability check(EditTree tree) {
        // the reroll validated its plan before assembling the transaction; the node is captured,
        // not looked up - the composite entry lives on the main tree's history
        return CommandAvailability.allowed();
    }

    @Override
    protected CommandResult doExecute(EditTree tree, boolean redoAction) {
        node.setAttributes(newAttributes);
        return result(redoAction ? CommandAction.REDO : CommandAction.EXECUTE);
    }

    @Override
    public CommandResult doUndo(EditTree tree) {
        node.setAttributes(oldAttributes);
        return result(CommandAction.UNDO);
    }

    private CommandResult result(CommandAction action) {
        final EditNodeAbstract[] updated = new EditNodeAbstract[]{node};
        return new CommandResult(this, action, updated, null, null, null, updated, null, NO_UPDATE_ACTIONS);
    }
}
