package de.jare.jsoncasted.editor.core;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.diff.DeltaOp;
import de.jare.jsoncasted.diff.DeltaProperty;
import de.jare.jsoncasted.diff.DeltaType;
import de.jare.jsoncasted.diff.DiffReport;
import de.jare.jsoncasted.diff.TreeDiffer;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonResource;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Layer-2 diff tests: the {@link EditNodeAbstract} DifferableNode projection feeds the same TreeDiffer engine that
 * runs on the parse layer (see doc/TREE_DIFF.md in jsonCasted). Two imports of the same text are a semantic no-op;
 * every edit kind produces its concept op; and the memoized Merkle hash follows content edits through the ancestor
 * chain.
 *
 * @author Janusch Rentenatus
 */
public class EditNodeDiffNGTest {

    private static final String DEMO_JSON = "{\n"
            + "  \"size\": 3,\n"
            + "  \"name\": \"demo\",\n"
            + "  \"rows\": [{\"_woodObjectId\": \"r1\", \"value\": \"a\"},"
            + " {\"_woodObjectId\": \"r2\", \"value\": \"b\"}],\n"
            + "  \"ref\": {\"_woodLink\": \"this::7\"}\n"
            + "}\n";

    private static EditNodeAbstract importRoot(String json) throws java.io.IOException, JsonParseException {
        JsonResource resource = JsonParserService.parse(json, JsonDebugLevel.SIMPLE);
        EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");
        return tree.getRoot();
    }

    /**
     * Two imports of the same text are identical on the edit layer too — validated root short-circuit, no deltas.
     */
    @Test
    public void testTwoImportsAreSemanticNoOp() throws Exception {
        EditNodeAbstract base = importRoot(DEMO_JSON);
        EditNodeAbstract theirs = importRoot(DEMO_JSON);
        assertTrue(base.getMerkleHash() == theirs.getMerkleHash(), "independent imports must hash equally");
        assertTrue(TreeDiffer.diff(base, theirs).isEmpty(), "no deltas expected");
    }

    /**
     * A scalar property change is one MODIFY(VALUE) with the tagged value texts, addressed by member key.
     */
    @Test
    public void testScalarValueChange() throws Exception {
        EditNodeAbstract base = importRoot(DEMO_JSON);
        EditNodeAbstract theirs = importRoot(DEMO_JSON.replace("\"size\": 3", "\"size\": 4"));
        DiffReport report = TreeDiffer.diff(base, theirs);
        assertEquals(report.getOps().size(), 1, "one op expected: " + report);
        DeltaOp op = report.getOps().get(0);
        assertEquals(op.getType(), DeltaType.MODIFY);
        assertEquals(op.getProperty(), DeltaProperty.VALUE);
        assertEquals(op.getKey(), "$.size");
        assertEquals(op.getOldValue(), "long:3");
        assertEquals(op.getNewValue(), "long:4");
    }

    /**
     * A type-only change (string "5" to long 5) is a VALUE change, never a no-op.
     */
    @Test
    public void testScalarTypeChange() throws Exception {
        EditNodeAbstract base = importRoot("{\"v\": \"5\"}");
        EditNodeAbstract theirs = importRoot("{\"v\": 5}");
        DiffReport report = TreeDiffer.diff(base, theirs);
        assertEquals(report.getOps().size(), 1, "one op expected: " + report);
        assertEquals(report.getOps().get(0).getProperty(), DeltaProperty.VALUE);
        assertEquals(report.getOps().get(0).getOldValue(), "string:5");
        assertEquals(report.getOps().get(0).getNewValue(), "long:5");
    }

    /**
     * A member added or removed is an ADD / REMOVE addressed by member key; a rename stays the conservative
     * REMOVE + ADD (similarity matching is v1-deferred) — but its content move is recognized.
     */
    @Test
    public void testMemberAddRemoveRename() throws Exception {
        EditNodeAbstract base = importRoot(DEMO_JSON);
        EditNodeAbstract removed = importRoot(DEMO_JSON.replace("\"name\": \"demo\",\n", ""));
        DiffReport removeReport = TreeDiffer.diff(base, removed);
        assertEquals(removeReport.getOps().size(), 1, "one op expected: " + removeReport);
        assertEquals(removeReport.getOps().get(0).getType(), DeltaType.REMOVE);
        assertEquals(removeReport.getOps().get(0).getKey(), "$.name");

        EditNodeAbstract added = importRoot(DEMO_JSON.replace("\"name\": \"demo\",", "\"name\": \"demo\",\n  \"extra\": 1,"));
        DiffReport addReport = TreeDiffer.diff(base, added);
        assertEquals(addReport.getOps().size(), 1, "one op expected: " + addReport);
        assertEquals(addReport.getOps().get(0).getType(), DeltaType.ADD);
        assertEquals(addReport.getOps().get(0).getFieldOrIndex(), "extra");
    }

    /**
     * Identified array rows pair by wood id: a content change inside a row is a MODIFY, addressed by row position.
     */
    @Test
    public void testIdentifiedRowContentChange() throws Exception {
        EditNodeAbstract base = importRoot(DEMO_JSON);
        EditNodeAbstract theirs = importRoot(DEMO_JSON.replace("\"value\": \"b\"", "\"value\": \"x\""));
        DiffReport report = TreeDiffer.diff(base, theirs);
        assertEquals(report.getOps().size(), 1, "one op expected: " + report);
        DeltaOp op = report.getOps().get(0);
        assertEquals(op.getType(), DeltaType.MODIFY);
        assertEquals(op.getKey(), "$.rows[1].value");
    }

    /**
     * Identified rows in a different order collapse into one REORDER.
     */
    @Test
    public void testIdentifiedRowReorder() throws Exception {
        EditNodeAbstract base = importRoot(DEMO_JSON);
        EditNodeAbstract theirs = importRoot(DEMO_JSON.replace(
                "\"rows\": [{\"_woodObjectId\": \"r1\", \"value\": \"a\"},"
                + " {\"_woodObjectId\": \"r2\", \"value\": \"b\"}],",
                "\"rows\": [{\"_woodObjectId\": \"r2\", \"value\": \"b\"},"
                + " {\"_woodObjectId\": \"r1\", \"value\": \"a\"}],"));
        DiffReport report = TreeDiffer.diff(base, theirs);
        assertEquals(report.getOps().size(), 1, "one op expected: " + report);
        DeltaOp op = report.getOps().get(0);
        assertEquals(op.getType(), DeltaType.REORDER);
        assertEquals(op.getKey(), "$.rows");
        assertEquals(op.getNewOrder(), List.of("r2", "r1"));
    }

    /**
     * A link target change is one radiating MODIFY(LINK_TARGET) — dependents are affected.
     */
    @Test
    public void testLinkTargetChangeIsRadiating() throws Exception {
        EditNodeAbstract base = importRoot(DEMO_JSON);
        EditNodeAbstract theirs = importRoot(DEMO_JSON.replace("\"this::7\"", "\"this::9\""));
        DiffReport report = TreeDiffer.diff(base, theirs);
        assertEquals(report.getOps().size(), 1, "one op expected: " + report);
        DeltaOp op = report.getOps().get(0);
        assertEquals(op.getType(), DeltaType.MODIFY);
        assertEquals(op.getProperty(), DeltaProperty.LINK_TARGET);
        // the link lives on the object data child of the ref property, hence the #0 position
        assertEquals(op.getKey(), "$.ref.#0");
        assertTrue(op.isRadiating());
        assertEquals(report.getRadiatingOps().size(), 1);
    }

    /**
     * An identity change is never a MODIFY: REMOVE (old id) + ADD (new id), radiating.
     */
    @Test
    public void testWoodIdChangeIsRemoveAndAdd() throws Exception {
        EditNodeAbstract base = importRoot(DEMO_JSON);
        EditNodeAbstract theirs = importRoot(DEMO_JSON.replace("\"r1\"", "\"r9\""));
        DiffReport report = TreeDiffer.diff(base, theirs);
        assertEquals(report.getOps().size(), 2, "one op expected: " + report);
        assertEquals(report.getOps(DeltaType.REMOVE).size(), 1);
        assertTrue(report.getOps(DeltaType.REMOVE).get(0).isRadiating());
        assertEquals(report.getRemovedObjektIds(), List.of("r1"));
    }

    /**
     * A cast change is one MODIFY(CLASS), not radiating.
     */
    @Test
    public void testCastChange() throws Exception {
        String castJson = "{\"cfg\": {\"_class\": \"hallucinated.test.Clazz\"}}";
        EditNodeAbstract base = importRoot(castJson);
        EditNodeAbstract theirs = importRoot(castJson.replace("hallucinated.test.Clazz", "other.test.Clazz"));
        DiffReport report = TreeDiffer.diff(base, theirs);
        assertEquals(report.getOps().size(), 1, "one op expected: " + report);
        assertEquals(report.getOps().get(0).getProperty(), DeltaProperty.CLASS);
        assertTrue(!report.getOps().get(0).isRadiating());
    }

    /**
     * Annotations are parse feedback, not content: an annotation attached to one side produces no delta.
     */
    @Test
    public void testAnnotationsAreNotContent() throws Exception {
        EditNodeAbstract base = importRoot(DEMO_JSON);
        EditNodeAbstract theirs = importRoot(DEMO_JSON);
        EditNodeAnnotation annotation = new EditNodeAnnotation("warning");
        ((EditNodeObject) theirs).addChild(annotation, new EditTimes());
        DiffReport report = TreeDiffer.diff(base, theirs);
        assertTrue(report.isEmpty(), "annotations must not appear in the diff: " + report);
    }

    /**
     * The memoized Merkle hash follows content edits: a leaf edit changes the root hash, an equal edit on the
     * same tree changes nothing structurally, and a re-import stays comparable across parses.
     */
    @Test
    public void testMerkleHashInvalidationChain() throws Exception {
        EditNodeAbstract base = importRoot(DEMO_JSON);
        final long rootHash = base.getMerkleHash();

        EditNodeObject root = (EditNodeObject) base;
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof EditNodeProperty prop && "size".equals(prop.getName())) {
                prop.setValue("9");
            }
        }
        assertNotEquals(base.getMerkleHash(), rootHash, "a leaf edit must change the ancestor hashes");

        EditNodeAbstract fresh = importRoot(DEMO_JSON);
        assertNotEquals(base.getMerkleHash(), fresh.getMerkleHash(),
                "the edited tree must differ from a fresh import");
        assertEquals(fresh.getMerkleHash(), importRoot(DEMO_JSON).getMerkleHash(),
                "two fresh imports must hash equally");
    }
}
