/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.merge;

import de.jare.jsoncasted.diff.DeltaOp;
import de.jare.jsoncasted.diff.DeltaProperty;
import de.jare.jsoncasted.editor.core.EditBundle;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Phase 10 of the project view concept (CONCEPT_ProjectView.md, question 10): the pick boundary
 * translation of a cross-world pair - ours is the live session (a rerolled bundle), theirs is an
 * isolated stand (the old version, same structure, other folder). The synonym anchor is the
 * relative provider filename; picked identities are born fresh and disjoint; references validate
 * through the synonym check and the conflict check - a dangling one refuses the whole pick.
 *
 * @author Janusch Rentenatus
 */
public class PickIdentityTranslationNGTest {

    private File tempRoot;
    private EditBundle liveSession;
    private EditTree ghostTree;

    @BeforeMethod
    public void setUp() throws Exception {
        tempRoot = Files.createTempDirectory("wjj-p10").toFile();
        // the live world: the bundle was rerolled (ids r1/r2), one id (9) stayed
        write("live/rBox.json", "{\"b\": {\"_woodObjectId\": \"r2\"}, \"old\": {\"_woodObjectId\": \"9\"}}");
        write("live/main.json", "{\"_woodProviders\": ["
                + "{ \"synonym\": \"sharedLive\", \"filename\": \"./rBox.json\" }],"
                + " \"cfg\": { \"_woodObjectId\": \"r1\"}}");
        // the ghost world (git -2): same structure, same relative provider name, OLD ids
        write("ghost/rBox.json", "{\"b\": {\"_woodObjectId\": \"5\"}, \"old\": {\"_woodObjectId\": \"9\"}}");
        write("ghost/main.json", "{\"_woodProviders\": ["
                + "{ \"synonym\": \"sharedGhost\", \"filename\": \"./rBox.json\" }]}");
        liveSession = EditBundle.open(new File(tempRoot, "live/main.json"));
        ghostTree = JsonTreeConverter.fromJsonFile(new File(tempRoot, "ghost/main.json"));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (liveSession != null) {
            liveSession.close();
        }
        deleteRecursively(tempRoot);
    }

    /**
     * The synonym anchor: ghost and live entries name the SAME relative member (./rBox.json) in
     * different folders - the ghost's old synonym resolves to the live one, picked identities are
     * born fresh and disjoint, and a resolvable external reference carries the live synonym.
     */
    @Test
    public void testPickGetsFreshIdsAndLiveSynonyms() throws Exception {
        final PickIdentityTranslation translation
                = PickIdentityTranslation.of(ghostTree, oursBySession());
        final EditNodeAbstract pick = subtree("{\"pick\": {\"_woodObjectId\": \"42\","
                + " \"use\": {\"_woodLink\": \"sharedGhost::9\"}}}");
        final List<DeltaOp> deltas = List.of(DeltaOp.addOp("$.pick", "use", pick));

        final PickIdentityTranslation.Result result = translation.translate(deltas);

        assertTrue(result.isOk(), "nothing dangles: " + result.getProblems());
        assertEquals(result.getDeltas().size(), 1);
        final EditNodeObject taken = objectWithIdentity(pick, "42", "r3");
        assertNotNull(taken, "the picked identity is born fresh (r3 is free in the live world)");
        assertNotEquals(taken.getObjektId(), "42", "never inherited from the ghost");
        assertEquals(findByLink(pick).getLinkTarget(), "sharedLive::9",
                "the reference carries the live synonym and the still-existing id");
    }

    /**
     * The internal rewiring: references inside the pick follow the fresh ids, the this/self point
     * of view stays; both fresh ids are distinct and disjoint from the live world.
     */
    @Test
    public void testInternalReferencesFollowTheFreshIds() throws Exception {
        final PickIdentityTranslation translation
                = PickIdentityTranslation.of(ghostTree, oursBySession());
        final EditNodeAbstract pick = subtree("{\"a\": {\"_woodObjectId\": \"41\","
                + " \"toB\": {\"_woodLink\": \"this::42\"}}, \"b\": {\"_woodObjectId\": \"42\"}}");
        final List<DeltaOp> deltas = List.of(DeltaOp.addOp("$", "a", pick));

        final PickIdentityTranslation.Result result = translation.translate(deltas);

        assertTrue(result.isOk(), result.getProblems().toString());
        final String rewired = findByLink(pick).getLinkTarget();
        assertTrue(rewired.startsWith("this::"), "the internal reference keeps its point of view: " + rewired);
        final String freshB = rewired.substring("this::".length());
        assertNotEquals(freshB, "42", "it follows the partner's fresh id");
        assertNotNull(objectWithIdentity(pick, freshB, null),
                "the link target is the partner's freshly generated id");
    }

    /**
     * The conflict check: a ghost reference whose live counterpart was rerolled away dangles in
     * the live world - the whole pick refuses, NOTHING applies (no half-picked bundle content).
     */
    @Test
    public void testDanglingReferenceRefusesTheWholePick() throws Exception {
        final PickIdentityTranslation translation
                = PickIdentityTranslation.of(ghostTree, oursBySession());
        final EditNodeAbstract pick = subtree("{\"pick\": {\"_woodObjectId\": \"42\","
                + " \"stale\": {\"_woodLink\": \"sharedGhost::5\"}}}");
        final List<DeltaOp> deltas = List.of(DeltaOp.addOp("$", "pick", pick));

        final PickIdentityTranslation.Result result = translation.translate(deltas);

        assertFalse(result.isOk(), "the ghost id 5 was rerolled away - the pick refuses");
        assertTrue(result.getDeltas().isEmpty(), "nothing half-applies");
        assertFalse(result.getProblems().isEmpty());
        assertEquals(objectWithIdentity(pick, "42", null).getObjektId(), "42",
                "the refused pick leaves the content untouched");
    }

    /**
     * An unknown synonym (the live world renamed or dropped the member) refuses with guidance -
     * the synonym check is part of every pick.
     */
    @Test
    public void testUnknownSynonymRefuses() throws Exception {
        final PickIdentityTranslation translation
                = PickIdentityTranslation.of(ghostTree, oursBySession());
        final EditNodeAbstract pick = subtree("{\"pick\": {\"_woodLink\": \"ghostUnknown::9\"}}");
        final List<DeltaOp> deltas = List.of(DeltaOp.addOp("$", "pick", pick));

        final PickIdentityTranslation.Result result = translation.translate(deltas);

        assertFalse(result.isOk());
        assertTrue(result.getProblems().get(0).contains("ghostUnknown"),
                "the refusal names the synonym: " + result.getProblems());
    }

    /**
     * A link target delta (MODIFY on an existing ours node) translates its new value through the
     * same rails - resolvable references pass with the live synonym, dangling ones refuse.
     */
    @Test
    public void testLinkTargetDeltaTranslates() throws Exception {
        final PickIdentityTranslation translation
                = PickIdentityTranslation.of(ghostTree, oursBySession());

        final PickIdentityTranslation.Result resolvable = translation.translate(List.of(
                DeltaOp.modifyOp("$.ref", DeltaProperty.LINK_TARGET,
                        "sharedLive::r2", "sharedGhost::9")));
        assertTrue(resolvable.isOk(), resolvable.getProblems().toString());
        assertEquals(resolvable.getDeltas().get(0).getNewValue(), "sharedLive::9",
                "the delta carries the translated live reference");

        final PickIdentityTranslation.Result dangling = translation.translate(List.of(
                DeltaOp.modifyOp("$.ref", DeltaProperty.LINK_TARGET,
                        "sharedLive::r2", "sharedGhost::5")));
        assertFalse(dangling.isOk(), "the rerolled-away id dangles - the pick refuses");
        assertTrue(dangling.getProblems().get(0).contains("sharedGhost::5"));
    }

    //
    // helpers
    //

    private Map<String, EditTree> oursBySession() throws Exception {
        final Map<String, EditTree> bySession = new LinkedHashMap<>();
        for (String synonym : liveSession.getSortedSynonyms()) {
            final EditTree tree = liveSession.findBySynonym(synonym);
            if (tree != null) {
                bySession.put(synonym, tree);
            }
        }
        return bySession;
    }

    private EditNodeAbstract subtree(String json) throws Exception {
        return JsonTreeConverter.fromJsonString(json, "ghostpick").getRoot();
    }

    private EditNodeObject objectWithIdentity(EditNodeAbstract root, String anyOrFresh,
            String expectedFresh) {
        final List<EditNodeObject> hits = new ArrayList<>();
        walk(root, obj -> {
            if (obj.getObjektId() != null && !obj.getObjektId().isEmpty()
                    && (obj.getObjektId().equals(anyOrFresh)
                    || (expectedFresh != null && obj.getObjektId().equals(expectedFresh)))) {
                hits.add(obj);
            }
        });
        return hits.isEmpty() ? null : hits.get(0);
    }

    private EditNodeObject findByLink(EditNodeAbstract root) {
        final List<EditNodeObject> hits = new ArrayList<>();
        walk(root, obj -> {
            if (obj.isLinkReference()) {
                hits.add(obj);
            }
        });
        return hits.isEmpty() ? null : hits.get(0);
    }

    private void walk(EditNodeAbstract node, java.util.function.Consumer<EditNodeObject> visitor) {
        if (node instanceof EditNodeObject obj) {
            visitor.accept(obj);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeAbstract child) {
                walk(child, visitor);
            }
        }
    }

    private void write(String relativePath, String content) throws Exception {
        final Path file = tempRoot.toPath().resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteRecursively(File folder) {
        final File[] entries = folder.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                deleteRecursively(entry);
            }
        }
        folder.delete();
    }

}
