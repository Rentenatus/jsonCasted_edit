package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.debug.JsonDebugLevel;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Roundtrip tests for the wood link support: import, export and re-import keep
 * identities and references; a detached but referenced object lands in the
 * _woodDefinitions section instead of being lost; an unreferenced detached
 * object is dropped.
 *
 * @author Janusch Rentenatus
 */
public class WoodLinkRoundTripNGTest {

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
            + "  }\n"
            + "}\n";

    private static EditTree importTree() throws java.io.IOException, JsonParseException {
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse(LINKED_JSON, JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        return JsonTreeConverter.convertRessourceToEditTree(resource, "root");
    }

    private static EditNodeProperty findProperty(EditNode parent, String propertyName) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i) instanceof EditNodeProperty prop
                    && propertyName.equals(prop.getName())) {
                return prop;
            }
        }
        return null;
    }

    @Test
    public void testRoundtripKeepsIdentitiesAndReferences() throws Exception {
        EditTree tree = importTree();
        String exported = EditTreeWriter.toJsonString(tree);

        // identity and reference terms survive the export
        assertTrue(exported.contains("_woodObjectId"), "identity terms must be exported: " + exported);
        assertTrue(exported.contains("_woodLink"), "reference terms must be exported: " + exported);

        // re-import: identities and references resolve again
        de.jare.jsoncasted.lang.JsonResource second
                = JsonParserService.parse(exported, JsonDebugLevel.SIMPLE);
        assertNotNull(second, "the exported string must be parseable: " + exported);
        EditTree reimported = JsonTreeConverter.convertRessourceToEditTree(second, "root");
        EditLinkingSet links = reimported.getLinkingSet();
        assertNotNull(links);
        assertNotNull(links.findObjectById("1"), "root identity must survive the roundtrip");
        assertNotNull(links.findObjectById("2"), "target identity must survive the roundtrip");

        EditNodeObject targetAgain = findObjectChild(reimported.getRoot(), "target");
        assertNotNull(targetAgain);
        assertSame(links.findLinkTarget("this::2"), targetAgain,
                "references must resolve to the re-imported target");
        assertTrue(links.getReferencesTo("this::2").size() >= 2);
    }

    @Test
    public void testDetachedReferencedObjectLandsInDefinitions() throws Exception {
        EditTree tree = importTree();
        EditNodeAbstract root = tree.getRoot();

        // Detach the target subtree: the object leaves the tree, the references stay.
        EditNodeProperty targetProp = findProperty(root, "target");
        assertNotNull(targetProp);
        assertTrue(root.removeChild(targetProp));

        String exported = EditTreeWriter.toJsonString(tree);
        assertTrue(exported.contains("_woodDefinitions"),
                "a detached but referenced object must be exported as a definition: " + exported);
        assertTrue(exported.contains("\"2\""), "the detached identity must be part of the definitions");

        // The re-import resolves the references through the definitions entry.
        de.jare.jsoncasted.lang.JsonResource second
                = JsonParserService.parse(exported, JsonDebugLevel.SIMPLE);
        assertNotNull(second);
        EditTree reimported = JsonTreeConverter.convertRessourceToEditTree(second, "root");
        EditLinkingSet links = reimported.getLinkingSet();
        assertNotNull(links.findObjectById("2"), "the definition entry must register its identity");
        assertNotNull(links.findLinkTarget("this::2"), "the references must resolve to the definition entry");
        assertNull(findProperty(reimported.getRoot(), "target"), "the detached property must not reappear as a tree member");
    }

    @Test
    public void testDetachedUnreferencedObjectIsDropped() throws Exception {
        EditTree tree = importTree();
        EditNodeAbstract root = tree.getRoot();

        // Detach the target and all its references: nothing points at it anymore.
        EditNodeProperty targetProp = findProperty(root, "target");
        EditNodeProperty refAProp = findProperty(root, "a");
        EditNodeProperty refBProp = findProperty(root, "b");
        assertNotNull(targetProp);
        assertNotNull(refAProp);
        assertNotNull(refBProp);
        assertTrue(root.removeChild(targetProp));
        assertTrue(root.removeChild(refAProp));
        assertTrue(root.removeChild(refBProp));

        String exported = EditTreeWriter.toJsonString(tree);
        assertFalse(exported.contains("_woodDefinitions"),
                "an unreferenced detached object is a zombie and must not be exported: " + exported);

        // The re-import knows neither the identity nor the references.
        de.jare.jsoncasted.lang.JsonResource second
                = JsonParserService.parse(exported, JsonDebugLevel.SIMPLE);
        assertNotNull(second);
        EditTree reimported = JsonTreeConverter.convertRessourceToEditTree(second, "root");
        assertNull(reimported.getLinkingSet().findObjectById("2"),
                "the zombie identity must not survive the roundtrip");
        assertTrue(reimported.getLinkingSet().getReferencesTo("this::2").isEmpty());
    }

    private static EditNodeObject findObjectChild(EditNode parent, String propertyName) {
        EditNodeProperty prop = findProperty(parent, propertyName);
        if (prop != null && prop.getChildCount() > 0 && prop.getChildAt(0) instanceof EditNodeObject obj) {
            return obj;
        }
        return null;
    }
}
