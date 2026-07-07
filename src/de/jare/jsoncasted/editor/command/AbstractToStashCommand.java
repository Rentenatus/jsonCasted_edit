/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.clipboard.ClipboardManager;
import de.jare.jsoncasted.editor.clipboard.ClipboardStash;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;

/*
 *
 * @author Jansuch Rentenatus
 */
public abstract class AbstractToStashCommand extends AbstractEditCommand {

    protected final ClipboardManager clipboardManager;
    protected final String stashName;
    protected final long[] nodeIds;
    protected final EditNodeAbstract[] originalStashContent;

    protected AbstractToStashCommand(
            CommandType commandType,
            String description,
            ClipboardManager clipboardManager,
            String stashName,
            long[] nodeIds) {

        super(commandType, description);

        if (clipboardManager == null) {
            throw new IllegalArgumentException("ClipboardManager cannot be null");
        }
        if (stashName == null || stashName.trim().isEmpty()) {
            throw new IllegalArgumentException("Stash name cannot be null or empty");
        }
        if (nodeIds == null || nodeIds.length == 0) {
            throw new IllegalArgumentException("Node IDs cannot be null or empty");
        }

        this.clipboardManager = clipboardManager;
        this.stashName = stashName;
        this.nodeIds = nodeIds.clone();

        ClipboardStash stash = clipboardManager.getStash(stashName);
        this.originalStashContent = stash != null ? stash.getNodes() : new EditNodeAbstract[0];
    }

    @Override
    public CommandAvailability check(EditTree tree) {
        if (tree == null) {
            return CommandAvailability.disallowed(
                    "editor.command.tree.missing");
        }

        return validate(tree);
    }

    protected CommandAvailability validate(EditTree tree) {
        String seenTypeKey = null;

        for (int i = 0; i < nodeIds.length; i++) {
            long nodeId = nodeIds[i];
            EditNode node = tree.findNodeById(nodeId);

            if (node == null) {
                return CommandAvailability.disallowed(
                        getNodeMissingMessageKey(),
                        Long.toString(nodeId),
                        Integer.toString(i));
            }

            String typeKey = node.getTypeKey();
            if (typeKey == null || typeKey.trim().isEmpty()) {
                return CommandAvailability.disallowed(
                        getUnsupportedNodeTypeMessageKey(),
                        String.valueOf(typeKey),
                        Long.toString(nodeId),
                        Integer.toString(i));
            }

            if (seenTypeKey == null) {
                seenTypeKey = typeKey;
            } else if (!seenTypeKey.equals(typeKey)) {
                return CommandAvailability.disallowed(
                        getMixedNodeTypesMessageKey(),
                        stashName,
                        Integer.toString(i));
            }
        }

        return validateFurther(tree);
    }

    protected CommandAvailability validateFurther(EditTree tree) {
        return CommandAvailability.allowed(getAllowedMessageKey(), stashName);
    }

    protected void requireExecutable(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        CommandAvailability availability = validate(tree);
        if (!availability.isAllowed()) {
            throw new IllegalStateException(availability.toString());
        }
    }

    protected EditNodeAbstract[] collectNodes(EditTree tree) {
        EditNodeAbstract[] nodes = new EditNodeAbstract[nodeIds.length];
        for (int i = 0; i < nodeIds.length; i++) {
            nodes[i] = tree.findNodeById(nodeIds[i]);
        }
        return nodes;
    }

    protected abstract String getAllowedMessageKey();

    protected abstract String getNodeMissingMessageKey();

    protected abstract String getUnsupportedNodeTypeMessageKey();

    protected abstract String getMixedNodeTypesMessageKey();
}
