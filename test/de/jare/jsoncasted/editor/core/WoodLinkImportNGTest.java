package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.debug.JsonDebugLevel;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Tests for the wood link import: _woodObjectId and _woodLink entries register
 * identity and references in the EditLinkingSet, this::/self:: links are
 * normalized against the provider name, and the reference stubs stay childless.
 *
 * @author Janusch Rentenatus
 */
public class WoodLinkImportNGTest {

    private static final String LINKED_JSON = "{\n"
            + "  \"_woodObjectId\": \"1\",\n"
            + "  \"target\": {\n"
            + "    \"_woodObjectId\": \"2\",\n"
            + "    \"name\": \"Target\"\n"
            + "  },\n"
            + "  \"a\": {\n"
            + "    \"_woodLink\": \"this::2\"\n"
            + "  },\n"
            + "  \"b\": {\n"
            + "    \"_woodLink\": \"this::2\"\n"
            + "  },\n"
            + "  \"_woodDefinitions\": [\n"
            + "    { \"_woodObjectId\": \"3\", \"name\": \"Definition\" }\n"
            + "  ]\n"
            + "}\n";

    private EditTree importTree() throws java.io.IOException, JsonParseException {
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse(LINKED_JSON, JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        return JsonTreeConverter.convertRessourceToEditTree(resource, "root");
    }

    private static EditNodeObject findObjectChild(EditNode parent, String propertyName) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i) instanceof EditNodeProperty prop
                    && propertyName.equals(prop.getName())
                    && prop.getChildCount() > 0
                    && prop.getChildAt(0) instanceof EditNodeObject obj) {
                return obj;
            }
        }
        return null;
    }

    @Test
    public void testImportRegistersObjectIdentities() throws Exception {
        EditTree tree = importTree();

        EditLinkingSet linkingSet = tree.getLinkingSet();
        assertNotNull(linkingSet, "the tree must adopt the linking set built during import");
        assertEquals(linkingSet.getProviderName(), "this", "no provider in the file means this");

        EditNodeAbstract root = tree.getRoot();
        assertTrue(root instanceof EditNodeObject);
        assertEquals(((EditNodeObject) root).getObjektId(), "1");
        assertSame(linkingSet.findObjectById("1"), root, "the root registers its own identity");

        EditNodeObject target = findObjectChild(root, "target");
        assertNotNull(target);
        assertEquals(target.getObjektId(), "2");
        assertSame(linkingSet.findObjectById("2"), target);
    }

    @Test
    public void testImportCreatesReferenceStubsWithNormalizedTargets() throws Exception {
        EditTree tree = importTree();
        EditNodeAbstract root = tree.getRoot();

        EditNodeObject refA = findObjectChild(root, "a");
        EditNodeObject refB = findObjectChild(root, "b");
        assertNotNull(refA);
        assertNotNull(refB);

        assertTrue(refA.isLinkReference(), "a _woodLink entry turns the node into a reference stub");
        assertEquals(refA.getLinkTarget(), "this::2", "this:: links are normalized against the provider name");
        assertEquals(refB.getLinkTarget(), "this::2");
        assertEquals(refA.getChildCount(), 0, "a reference stub carries no field children");
    }

    @Test
    public void testReverseIndexAndTargetResolution() throws Exception {
        EditTree tree = importTree();
        EditLinkingSet linkingSet = tree.getLinkingSet();

        assertEquals(linkingSet.getReferencesTo("this::2").size(), 2,
                "both reference stubs must appear in the reverse index");

        EditNodeObject target = findObjectChild(tree.getRoot(), "target");
        assertSame(linkingSet.findLinkTarget("this::2"), target,
                "after the import the link map resolves the reference to its target");
    }

    @Test
    public void testDanglingLinkStaysRegisteredWithoutTarget() throws Exception {
        EditTree tree = importTree();
        EditLinkingSet linkingSet = tree.getLinkingSet();

        // The definitions array entry registers as a normal identity.
        assertNotNull(linkingSet.findObjectById("3"));
        // No reference points at it.
        assertFalse(linkingSet.hasReferencesTo("this::3"));

        // A link to an unknown id keeps its stub registered but resolves to no target.
        EditNodeObject dangling = new EditNodeObject("dangling");
        dangling.setLinkTarget("this::404");
        assertTrue(linkingSet.registerLinkReference(dangling));
        assertNull(linkingSet.findLinkTarget("this::404"));
    }

    @Test
    public void testImportWithoutLinkingSetStillWorks() throws Exception {
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse(LINKED_JSON, JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        EditNodeAbstract root = JsonTreeConverter.importFromJsonNode(
                resource.getRoot(), "solo", new EditTimes());
        assertNotNull(root);
        assertEquals(root.getChildCount(), 4,
                "without a linking set the import must behave exactly as before: all non-metadata children");
    }
}
