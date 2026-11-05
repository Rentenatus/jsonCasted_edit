/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.command.EditCommand.CommandType;
import de.jare.jsoncasted.editor.core.EditBundle;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.lang.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * The provider synonym reroll of one bundle (concept CONCEPT_ProjectView.md, section 7.1 - phase
 * 8): every providers entry whose synonym the plan rerolls is relabeled in place, and the tree the
 * old synonym identified renames its provider - including the session registration of the open
 * bundle, so resolution and tabs address the tree under its new name from now on. External
 * synonyms (references to files outside the bundle) are not part of this command - the reroll
 * scope is the bundle.
 *
 * @author Janusch Rentenatus
 */
public class RerollProvidersCommand extends AbstractEditCommand {

    private static final String SYNONYM_KEY = "synonym";

    /**
     * One providers entry relabel: the entry node (a member of the tree's captured providers
     * member), its old and its new synonym.
     */
    public static final class EntryRelabel {

        private final JsonNode providerEntry;
        public final String oldSynonym;
        public final String newSynonym;

        /**
         * Creates the relabel data.
         *
         * @param providerEntry the providers entry node to rewrite
         * @param oldSynonym its current synonym
         * @param newSynonym the rerolled synonym
         */
        public EntryRelabel(JsonNode providerEntry, String oldSynonym, String newSynonym) {
            this.providerEntry = providerEntry;
            this.oldSynonym = oldSynonym;
            this.newSynonym = newSynonym;
        }
    }

    /**
     * One tree rename: the tree the old synonym identified gets the new provider name - the
     * linking set rebase happens after the whole transaction (see the reroll composite).
     */
    public static final class ProviderFlip {

        public final EditBundle bundle;
        public final EditTree tree;
        public final String oldSynonym;
        public final String newSynonym;

        /**
         * Creates the flip.
         *
         * @param bundle the open bundle session (its synonym registration renames too)
         * @param tree the tree that was identified by the old synonym
         * @param oldSynonym the current synonym of the tree
         * @param newSynonym the rerolled synonym
         */
        public ProviderFlip(EditBundle bundle, EditTree tree, String oldSynonym, String newSynonym) {
            this.bundle = bundle;
            this.tree = tree;
            this.oldSynonym = oldSynonym;
            this.newSynonym = newSynonym;
        }
    }

    private final List<EntryRelabel> entries;
    private final List<ProviderFlip> flips;

    /**
     * Creates the synonym reroll of the bundle.
     *
     * @param entries the providers entries to relabel, in plan order
     * @param flips the trees whose provider name flips with their session registration
     */
    public RerollProvidersCommand(List<EntryRelabel> entries, List<ProviderFlip> flips) {
        super(CommandType.SET_ATTRIBUTE);
        this.entries = new ArrayList<>(entries);
        this.flips = new ArrayList<>(flips);
        setDescription("Reroll " + entries.size() + " provider synonym(s)");
    }

    @Override
    public CommandAvailability check(EditTree tree) {
        if (entries.isEmpty()) {
            return CommandAvailability.disallowed("editor.command.composite.empty");
        }
        return CommandAvailability.allowed();
    }

    @Override
    protected CommandResult doExecute(EditTree tree, boolean redoAction) {
        for (EntryRelabel relabel : entries) {
            relabel.providerEntry.put(SYNONYM_KEY, JsonNode.stringNode(relabel.newSynonym));
        }
        for (ProviderFlip flip : flips) {
            if (flip.bundle != null) {
                flip.bundle.relabelSynonym(flip.oldSynonym, flip.newSynonym);
            }
            if (flip.tree != null) {
                flip.tree.setProviderName(flip.newSynonym);
            }
        }
        return commandResult(redoAction, entries.size());
    }

    @Override
    public CommandResult doUndo(EditTree tree) {
        for (ProviderFlip flip : flips) {
            if (flip.tree != null) {
                flip.tree.setProviderName(flip.oldSynonym);
            }
            if (flip.bundle != null) {
                flip.bundle.relabelSynonym(flip.newSynonym, flip.oldSynonym);
            }
        }
        for (EntryRelabel relabel : entries) {
            relabel.providerEntry.put(SYNONYM_KEY, JsonNode.stringNode(relabel.oldSynonym));
        }
        final de.jare.jsoncasted.editor.core.EditNodeAbstract[] empty = new de.jare.jsoncasted.editor.core.EditNodeAbstract[0];
        return new CommandResult(this, CommandAction.UNDO, empty, null, null, null, empty, null, NO_UPDATE_ACTIONS);
    }

    private CommandResult commandResult(boolean redoAction, int count) {
        final de.jare.jsoncasted.editor.core.EditNodeAbstract[] empty = new de.jare.jsoncasted.editor.core.EditNodeAbstract[0];
        return new CommandResult(this,
                redoAction ? CommandAction.REDO : CommandAction.EXECUTE,
                empty, null, null, null, empty, null, NO_UPDATE_ACTIONS);
    }
}
