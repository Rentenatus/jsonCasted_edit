/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.command.RerollCompositeCommand;
import de.jare.jsoncasted.editor.command.RerollProvidersCommand;
import de.jare.jsoncasted.editor.command.SetCapturedAttributesCommand;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.editor.events.HistoryManager;
import de.jare.jsoncasted.lang.JsonNode;
import de.jare.jsoncasted.project.BundleReroller;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Phase 8 of the project view concept (CONCEPT_ProjectView.md, section 7.1): the reroll command
 * stack of the editor - the captured-node attribute patch, the provider synonym relabel and the
 * composite that spans the whole bundle as ONE undo step, including the linking set rebase that
 * keeps the resolution index honest in both directions.
 *
 * @author Janusch Rentenatus
 */
public class RerollCommandsNGTest {

    /**
     * The reroll rewrites identities, references and provider synonyms in one history entry; the
     * linking set re-registers under the new keys. ONE undo step (one history entry) restores
     * everything: values, synonyms, provider name - and the linking set follows back.
     */
    @Test
    public void testRerollAsOneUndoStep() throws Exception {
        final EditTree tree = treeOf();
        final EditNodeObject cfg = findById(tree, "5");
        final EditNodeObject ref = findByLink(tree, "box::5");
        assertNotNull(cfg, "the fixture carries a node with identity 5");
        assertNotNull(ref, "the fixture carries a reference to box::5");

        final BundleReroller.RerollPlan plan = BundleReroller.plan(
                setOf("5"), setOf("box"), Set.of(), setOf("box::5"), new ArrayList<>(), true, true);
        assertFalse(plan.isRefused(), "the fixture plan computes: " + plan.getProblems());

        final List<EditCommand> commands = new ArrayList<>();
        commands.add(new SetCapturedAttributesCommand(cfg,
                Map.of("objektId", plan.getIdMap().get("5"))));
        commands.add(new SetCapturedAttributesCommand(ref,
                Map.of("|linkTarget", plan.getLinkMap().get("box::5"))));
        commands.add(new RerollProvidersCommand(
                Arrays.asList(new RerollProvidersCommand.EntryRelabel(providerEntry(tree),
                        "box", plan.getSynonymMap().get("box"))),
                Arrays.asList(new RerollProvidersCommand.ProviderFlip(null, tree,
                        "box", plan.getSynonymMap().get("box")))));

        final HistoryManager history = new HistoryManager(tree);
        history.execute(new RerollCompositeCommand("Reroll IDs of test bundle", commands,
                Arrays.asList(tree)));

        assertEquals(cfg.getObjektId(), "r1", "the identity carries its fresh value");
        assertEquals(ref.getLinkTarget(), "box_1::r1", "the reference rewrites consistently");
        assertEquals(providerSynonym(tree), "box_1", "the providers entry relabels");
        assertEquals(tree.getProviderName(), "box_1", "the tree answers under its new synonym");
        assertTrue(tree.getLinkingSet().getUnmodifiableObjectIdMap().containsKey("box_1::r1"),
                "the linking set registers the new qualified key");
        assertFalse(tree.getLinkingSet().getUnmodifiableObjectIdMap().containsKey("box::5"),
                "the old qualified key is gone");
        assertTrue(tree.isEdited(), "the tree is dirty until the user saves");

        // ONE undo step - the composite is one history entry
        history.undo();
        assertEquals(cfg.getObjektId(), "5", "one undo step restores the identity");
        assertEquals(ref.getLinkTarget(), "box::5", "one undo step restores the reference");
        assertEquals(providerSynonym(tree), "box", "one undo step restores the synonym");
        assertEquals(tree.getProviderName(), "box", "one undo step restores the provider name");
        assertTrue(tree.getLinkingSet().getUnmodifiableObjectIdMap().containsKey("box::5"),
                "the linking set follows back");
    }

    //
    // fixture helpers
    //

    /**
     * A tree with one identity, one reference and one providers entry - the minimal bundle world
     * the reroll touches all three of.
     */
    private static EditTree treeOf() throws Exception {
        final EditTree tree = JsonTreeConverter.fromJsonString("{"
                + "\"_woodProviders\": [ { \"synonym\": \"box\", \"filename\": \"./box.json\" } ],"
                + "\"cfg\": { \"_woodObjectId\": \"5\" },"
                + "\"ref\": { \"_woodLink\": \"box::5\" }"
                + "}", "main");
        tree.setProviderName("box"); // the session naming of the dialog flow
        return tree;
    }

    private static EditNodeObject findById(EditTree tree, String objektId) {
        final List<EditNodeObject> hits = new ArrayList<>();
        walk(tree.getRoot(), obj -> {
            if (objektId.equals(obj.getObjektId())) {
                hits.add(obj);
            }
        });
        return hits.isEmpty() ? null : hits.get(0);
    }

    private static EditNodeObject findByLink(EditTree tree, String linkTarget) {
        final List<EditNodeObject> hits = new ArrayList<>();
        walk(tree.getRoot(), obj -> {
            if (linkTarget.equals(obj.getLinkTarget())) {
                hits.add(obj);
            }
        });
        return hits.isEmpty() ? null : hits.get(0);
    }

    private static JsonNode providerEntry(EditTree tree) {
        return tree.getProvidersNode().asArray().get(0);
    }

    private static String providerSynonym(EditTree tree) {
        return providerEntry(tree).asObjectValues().get("synonym").asText();
    }

    private static void walk(EditNodeAbstract node, Consumer<EditNodeObject> visitor) {
        if (node instanceof EditNodeObject obj) {
            visitor.accept(obj);
            for (int i = 0; i < obj.getChildCount(); i++) {
                walk(obj.getChildAt(i), visitor);
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChildAt(i), visitor);
        }
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
