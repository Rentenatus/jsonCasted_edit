/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditLinkingSet;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditTree;
import java.util.ArrayList;
import java.util.List;

/**
 * The reroll transaction of one bundle (concept CONCEPT_ProjectView.md, section 7.1 - phase 8):
 * the identity rewrites, the link rewrites and the provider synonym flips of ALL trees of the
 * bundle as ONE history entry - one undo step rolls the whole reroll back. The transaction spans
 * several trees; it lives on the main tree's history while its children carry their captured
 * nodes. The linking sets of the affected trees rebuild after execute AND after undo - the wood
 * identities they index changed (or changed back), the resolution must never run on stale keys.
 *
 * @author Janusch Rentenatus
 */
public class RerollCompositeCommand extends CompositeEditCommand {

    private final List<EditTree> affectedTrees;

    /**
     * Creates the reroll transaction.
     *
     * @param description the transaction description (what the dialogs report)
     * @param commands the reroll commands of all trees, in execution order
     * @param affectedTrees every tree of the bundle (their linking sets rebuild after the
     * transaction and after its undo)
     */
    public RerollCompositeCommand(String description, List<EditCommand> commands, List<EditTree> affectedTrees) {
        super(description, commands);
        this.affectedTrees = new ArrayList<>(affectedTrees);
    }

    @Override
    protected CommandResult doExecute(EditTree tree, boolean redoAction) {
        final CommandResult result = super.doExecute(tree, redoAction);
        for (EditTree affected : affectedTrees) {
            affected.markEdited();
            rebuildLinkingSet(affected);
        }
        return result;
    }

    @Override
    protected CommandResult doUndo(EditTree tree) {
        final CommandResult result = super.doUndo(tree);
        for (EditTree affected : affectedTrees) {
            // the trees stay edited after the undo - a save is always a user decision; a markClean
            // would lie if the tree carried further edits besides the reroll
            affected.markEdited();
            rebuildLinkingSet(affected);
        }
        return result;
    }

    /**
     * Rebuilds the linking set of a tree from its current state: provider name first, then every
     * wood identity and every link reference re-registers. The same rails the import walks - the
     * rebase is deterministic in both directions, which is exactly what the undo needs.
     *
     * @param tree the tree whose resolution index rebuilds
     */
    private static void rebuildLinkingSet(EditTree tree) {
        final EditLinkingSet fresh = new EditLinkingSet(tree.getProviderName());
        walkWoodObjects(tree.getRoot(), obj -> {
            if (obj.getObjektId() != null && !obj.getObjektId().isEmpty()) {
                fresh.registerObject(obj);
            }
            if (obj.isLinkReference()) {
                fresh.registerLinkReference(obj);
            }
        });
        fresh.resolveLinkTargets();
        tree.setLinkingSet(fresh);
    }

    private static void walkWoodObjects(EditNodeAbstract node, java.util.function.Consumer<EditNodeObject> visitor) {
        if (node instanceof EditNodeObject obj) {
            visitor.accept(obj);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeAbstract child) {
                walkWoodObjects(child, visitor);
            }
        }
    }
}
