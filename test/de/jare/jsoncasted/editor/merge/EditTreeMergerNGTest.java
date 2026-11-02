package de.jare.jsoncasted.editor.merge;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.diff.DeltaProperty;
import de.jare.jsoncasted.diff.MergePolicy;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.editor.core.EditTreeWriter;
import de.jare.jsoncasted.editor.events.HistoryManager;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonResource;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Integration tests for the 3-way merge (U1 of doc/TREE_DIFF.md): base is the retained import state (G1), the
 * application runs as one composite command (G2), SAFE_AUTO applies the non-overlapping external changes while
 * conflicts stay untouched.
 *
 * @author Janusch Rentenatus
 */
public class EditTreeMergerNGTest {

    private static final String BASE_JSON = "{\"a\": 1, \"b\": 2, \"keep\": \"x\"}";

    private static EditTree importTree(String json) throws Exception {
        JsonResource resource = JsonParserService.parse(json, JsonDebugLevel.SIMPLE);
        EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");
        return tree;
    }

    private static void setValue(EditTree tree, String key, String newValue) {
        EditNode root = tree.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            EditNode child = root.getChildAt(i);
            if (child instanceof EditNodeProperty prop && key.equals(prop.getName())) {
                prop.setValue(newValue);
                return;
            }
        }
        throw new AssertionError("member not found: " + key);
    }

    private static String getValue(EditTree tree, String key) {
        EditNode root = tree.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            EditNode child = root.getChildAt(i);
            if (child instanceof EditNodeProperty prop && key.equals(prop.getName())) {
                return prop.getValue();
            }
        }
        throw new AssertionError("member not found: " + key);
    }

    /**
     * G1: the import converter retains the parsed resource as the merge base.
     */
    @Test
    public void testImportRetainsBaseSnapshot() throws Exception {
        EditTree tree = importTree(BASE_JSON);
        assertNotNull(tree.getBaseSnapshot(), "the import state must be retained");
        assertEquals(tree.getBaseSnapshot().getRoot().asObjectValues().get("b").asLong(), Long.valueOf(2L));
    }

    /**
     * SAFE_AUTO: the user edited a, the file changed b — both survive the merge, and the merge is one undo step
     * that restores the pre-merge state.
     */
    @Test
    public void testSafeAutoMergesNonOverlappingChanges() throws Exception {
        EditTree ours = importTree(BASE_JSON);
        setValue(ours, "a", "10"); // user edit

        JsonResource theirs = JsonParserService.parse("{\"a\": 1, \"b\": 99, \"keep\": \"x\"}", JsonDebugLevel.SIMPLE);
        HistoryManager history = new HistoryManager(ours);
        MergeOutcome outcome = EditTreeMerger.merge(ours, theirs, MergePolicy.SAFE_AUTO, history);

        assertTrue(outcome.isApplied(), outcome.toString());
        assertFalse(outcome.getPlan().hasConflicts(), outcome.getPlan().toString());
        assertEquals(getValue(ours, "a"), "10", "the user edit must survive");
        assertEquals(getValue(ours, "b"), "99", "the external change must be in");
        assertEquals(getValue(ours, "keep"), "x");

        // G2: one undo step per merge
        assertEquals(history.getUndoSize(), 1, "the merge must book exactly one undo step");
        history.undo();
        assertEquals(getValue(ours, "b"), "2", "undo must restore the pre-merge state");
        assertEquals(getValue(ours, "a"), "10", "undo must not touch the user edit");
    }

    /**
     * ASK (the default): report only, apply nothing.
     */
    @Test
    public void testAskAppliesNothing() throws Exception {
        EditTree ours = importTree(BASE_JSON);
        setValue(ours, "a", "10");

        JsonResource theirs = JsonParserService.parse("{\"a\": 1, \"b\": 99, \"keep\": \"x\"}", JsonDebugLevel.SIMPLE);
        HistoryManager history = new HistoryManager(ours);
        MergeOutcome outcome = EditTreeMerger.merge(ours, theirs, MergePolicy.ASK, history);

        assertFalse(outcome.isApplied());
        assertEquals(getValue(ours, "b"), "2", "nothing must be applied");
        assertEquals(history.getUndoSize(), 0);
        assertEquals(outcome.getPlan().getApplyOps().size(), 1, "the plan still knows what would apply");
        assertFalse(outcome.getPlan().hasConflicts());
    }

    /**
     * Divergent edits of the same property conflict: nothing is applied, the user edit stays, the conflict names
     * the spot and the reason.
     */
    @Test
    public void testDivergentValueConflictLeavesOursUntouched() throws Exception {
        EditTree ours = importTree(BASE_JSON);
        setValue(ours, "a", "10");

        JsonResource theirs = JsonParserService.parse("{\"a\": 20, \"b\": 2, \"keep\": \"x\"}", JsonDebugLevel.SIMPLE);
        MergeOutcome outcome = EditTreeMerger.merge(ours, theirs, MergePolicy.SAFE_AUTO, null);

        assertTrue(outcome.getPlan().hasConflicts(), outcome.getPlan().toString());
        assertEquals(outcome.getPlan().getConflicts().size(), 1);
        assertEquals(outcome.getPlan().getConflicts().get(0).getProperty(), DeltaProperty.VALUE);
        assertEquals(getValue(ours, "a"), "10", "the conflict must not be resolved silently");
    }

    /**
     * The user removed a member, the file added a different one: both apply cleanly.
     */
    @Test
    public void testRemoveAndAddMerge() throws Exception {
        EditTree ours = importTree(BASE_JSON);
        // user removes "b"
        EditNode root = ours.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof EditNodeProperty prop && "b".equals(prop.getName())) {
                assertTrue(root instanceof EditNodeObject);
                ((EditNodeObject) root).removeChild((de.jare.jsoncasted.editor.core.EditNodeAbstract) prop);
                break;
            }
        }

        JsonResource theirs = JsonParserService.parse(
                "{\"a\": 1, \"b\": 2, \"keep\": \"x\", \"added\": \"new\"}", JsonDebugLevel.SIMPLE);
        HistoryManager history = new HistoryManager(ours);
        MergeOutcome outcome = EditTreeMerger.merge(ours, theirs, MergePolicy.SAFE_AUTO, history);

        assertTrue(outcome.isApplied(), outcome.toString());
        assertFalse(outcome.getPlan().hasConflicts(), outcome.getPlan().toString());
        assertEquals(history.getUndoSize(), 1);
        String exported = EditTreeWriter.toJsonString(ours);
        assertTrue(exported.contains("added:"), "the external member must be in: " + exported);
        history.undo();
        exported = EditTreeWriter.toJsonString(ours);
        assertFalse(exported.contains("added:"), "undo must remove the external member again");
    }

    /**
     * Both sides made the same change: a no-op — nothing to apply, no conflict.
     */
    @Test
    public void testSameChangeBothSidesIsNoOp() throws Exception {
        EditTree ours = importTree(BASE_JSON);
        setValue(ours, "a", "10");

        JsonResource theirs = JsonParserService.parse("{\"a\": 10, \"b\": 2, \"keep\": \"x\"}", JsonDebugLevel.SIMPLE);
        MergeOutcome outcome = EditTreeMerger.merge(ours, theirs, MergePolicy.SAFE_AUTO, null);

        assertFalse(outcome.isApplied(), "a pure no-op must not book a transaction: " + outcome);
        assertTrue(outcome.getPlan().getApplyOps().isEmpty());
        assertFalse(outcome.getPlan().hasConflicts());
        assertEquals(outcome.getPlan().getNoOps().size(), 1);
        assertEquals(getValue(ours, "a"), "10");
    }

    /**
     * A tree without import state cannot merge — the base side is the precondition (G1).
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMergeWithoutBaseFails() throws Exception {
        EditTree bare = new EditTree("root");
        JsonResource theirs = JsonParserService.parse(BASE_JSON, JsonDebugLevel.SIMPLE);
        EditTreeMerger.merge(bare, theirs, MergePolicy.SAFE_AUTO, null);
    }

    /**
     * Regression (live W3 finding): type resolution inside a subtree the external side removed must not surface as
     * a phantom REMOVED_VS_MODIFIED conflict — inferred casts are parse feedback, not user content.
     */
    @Test
    public void testInferredCastsDoNotPhantomConflict() throws Exception {
        EditTree ours = importTree("{\"a\": 1, \"b\": {\"c\": 2}}");
        // simulate the parser typing the object data child of the member theirs will remove
        EditNode root = ours.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof EditNodeProperty prop && "b".equals(prop.getName())) {
                ((EditNodeObject) prop.getChildAt(0)).setCastNameInferred("de.jare.jsonconfig.item.ConfigThing");
            }
        }

        JsonResource theirs = JsonParserService.parse("{\"a\": 1}", JsonDebugLevel.SIMPLE);
        de.jare.jsoncasted.diff.MergePlan plan = EditTreeMerger.analyze(ours, theirs);

        assertTrue(!plan.hasConflicts(),
                "inferred casts must not conflict a removal: " + plan.getConflicts());
        assertEquals(plan.getApplyOps().size(), 1);
        assertEquals(plan.getApplyOps().get(0).getType(), de.jare.jsoncasted.diff.DeltaType.REMOVE);
        assertEquals(plan.getApplyOps().get(0).getKey(), "$.b");
    }

    /**
     * R4: after a save the saved file is the new base. Cast provenance stays untouched (the writer persists
     * {@code _class} only for detached {@code _woodDefinitions} rows, not for tree objects), so the tree matches
     * its own refreshed base without phantom deltas.
     */
    @Test
    public void testSavedBaseRefreshKeepsDiffClean() throws Exception {
        EditTree ours = importTree(BASE_JSON);
        ((EditNodeObject) ours.getRoot()).setCastNameInferred("de.jare.X"); // the parser typed the root

        String saved = EditTreeWriter.toJsonString(ours);
        JsonResource savedResource = JsonParserService.parse(saved, JsonDebugLevel.SIMPLE);
        ours.adoptSavedBase(savedResource);

        assertEquals(ours.getBaseSnapshot(), savedResource, "the saved file is the new base");
        assertTrue(((EditNodeObject) ours.getRoot()).isCastInferred(),
                "tree casts keep their provenance across a save");
        EditTree baseEdit = JsonTreeConverter.convertRessourceToEditTree(savedResource,
                ours.getRoot().getName());
        assertTrue(de.jare.jsoncasted.diff.TreeDiffer.diff(baseEdit.getRoot(), ours.getRoot()).isEmpty(),
                "the tree must match its saved state without phantom deltas");
    }

    /**
     * The merge against a live theirs root (another open editor playing the external version): one transaction,
     * both sides' changes survive.
     */
    @Test
    public void testMergeAgainstLiveTheirsRoot() throws Exception {
        EditTree ours = importTree(BASE_JSON);
        setValue(ours, "a", "10");
        EditTree theirsTree = importTree(BASE_JSON.replace("\"b\": 2", "\"b\": 99"));

        HistoryManager history = new HistoryManager(ours);
        MergeOutcome outcome = EditTreeMerger.merge(ours, theirsTree.getRoot(), MergePolicy.SAFE_AUTO, history);

        assertTrue(outcome.isApplied(), outcome.toString());
        assertTrue(!outcome.getPlan().hasConflicts());
        assertEquals(getValue(ours, "a"), "10");
        assertEquals(getValue(ours, "b"), "99");
        assertEquals(history.getUndoSize(), 1);
    }

    /**
     * The conflict decision path: chosen theirs resolutions plus the apply list land in ONE transaction (one undo
     * step), and undo restores the pre-merge state.
     */
    @Test
    public void testApplyDeltasResolvesConflictTakeTheirs() throws Exception {
        EditTree ours = importTree(BASE_JSON);
        setValue(ours, "a", "10");
        EditTree theirsTree = importTree(
                BASE_JSON.replace("\"a\": 1", "\"a\": 20").replace("\"b\": 2", "\"b\": 99"));

        de.jare.jsoncasted.diff.MergePlan plan = EditTreeMerger.analyze(ours, theirsTree.getRoot());
        assertTrue(plan.hasConflicts(), "the a-edits diverge: " + plan);
        assertEquals(plan.getApplyOps().size(), 1, "the b-change still applies");

        java.util.List<de.jare.jsoncasted.diff.DeltaOp> chosen = new java.util.ArrayList<>(plan.getApplyOps());
        chosen.add(plan.getConflicts().get(0).getTheirsDelta()); // decision: take theirs
        HistoryManager history = new HistoryManager(ours);
        MergeOutcome outcome = EditTreeMerger.applyDeltas(ours, theirsTree.getRoot(), plan, chosen, history);

        assertTrue(outcome.isApplied(), outcome.toString());
        assertEquals(getValue(ours, "a"), "20", "the theirs decision must win");
        assertEquals(getValue(ours, "b"), "99");
        assertEquals(history.getUndoSize(), 1, "one undo step for the whole merge");

        history.undo();
        assertEquals(getValue(ours, "a"), "10", "undo restores the pre-merge state");
        assertEquals(getValue(ours, "b"), "2");
    }
}
