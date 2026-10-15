package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.command.DeleteNodeCommand;
import de.jare.jsoncasted.editor.core.EditLinkingSet;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.EditTreeWriter;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.debug.JsonDebugLevel;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Command tests for the wood link semantics: deleting an object is a tree
 * operation, the linking set holds the identity while references exist, and
 * the undo rebinds the identity to the restored copy.
 *
 * @author Janusch Rentenatus
 */
public class WoodLinkCommandNGTest {

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

    private TreeEditorModel importEditor() throws java.io.IOException, de.jare.jsoncasted.io.JsonParseException {
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse(LINKED_JSON, JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");
        return new TreeEditorModel(tree);
    }

    private static EditNodeProperty findProperty(de.jare.jsoncasted.editor.core.EditNode parent,
            String propertyName) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i) instanceof EditNodeProperty prop
                    && propertyName.equals(prop.getName())) {
                return prop;
            }
        }
        return null;
    }

    @Test
    public void testDeleteKeepsIdentityInHoldingSpace() throws Exception {
        TreeEditorModel editor = importEditor();
        EditTree tree = editor.getTree();
        EditLinkingSet linkingSet = tree.getLinkingSet();

        EditNodeObject before = (EditNodeObject) findProperty(tree.getRoot(), "target").getChildAt(0);
        assertEquals(linkingSet.getReferencesTo("this::2").size(), 2);

        editor.executeCommand(new DeleteNodeCommand(findProperty(tree.getRoot(), "target")));

        // Delete is a tree operation: the identity stays, the references stay intact.
        assertNotNull(findProperty(tree.getRoot(), "a"), "the reference must stay in the tree");
        assertSame(linkingSet.findObjectById("2"), before, "the identity stays in the holding space");
        assertEquals(linkingSet.getReferencesTo("this::2").size(), 2,
                "the tree references keep pointing at the held object");
    }

    @Test
    public void testExportAfterDeleteWritesDefinition() throws Exception {
        TreeEditorModel editor = importEditor();
        EditTree tree = editor.getTree();

        editor.executeCommand(new DeleteNodeCommand(findProperty(tree.getRoot(), "target")));

        String exported = EditTreeWriter.toJsonString(tree);
        assertTrue(exported.contains("_woodDefinitions"),
                "the deleted but referenced object must be exported as a definition");
    }

    @Test
    public void testUndoRebindsIdentityToRestoredCopy() throws Exception {
        TreeEditorModel editor = importEditor();
        EditTree tree = editor.getTree();
        EditLinkingSet linkingSet = tree.getLinkingSet();

        editor.executeCommand(new DeleteNodeCommand(findProperty(tree.getRoot(), "target")));
        editor.undo();

        EditNodeProperty restoredProp = findProperty(tree.getRoot(), "target");
        assertNotNull(restoredProp, "the undo must restore the property");
        assertTrue(restoredProp.getChildAt(0) instanceof EditNodeObject);
        EditNodeObject restored = (EditNodeObject) restoredProp.getChildAt(0);

        assertSame(linkingSet.findObjectById("2"), restored,
                "the identity must rebind to the restored tree instance");
        assertSame(linkingSet.findLinkTarget("this::2"), restored,
                "the link map must resolve to the restored tree instance");
    }

    @Test
    public void testDeleteOfReferenceLeavesReverseIndex() throws Exception {
        TreeEditorModel editor = importEditor();
        EditTree tree = editor.getTree();
        EditLinkingSet linkingSet = tree.getLinkingSet();

        assertEquals(linkingSet.getReferencesTo("this::2").size(), 2);

        editor.executeCommand(new DeleteNodeCommand(findProperty(tree.getRoot(), "a")));
        assertEquals(linkingSet.getReferencesTo("this::2").size(), 1,
                "a deleted reference stub leaves the reverse index");

        editor.executeCommand(new DeleteNodeCommand(findProperty(tree.getRoot(), "b")));
        assertEquals(linkingSet.getReferencesTo("this::2").size(), 0,
                "after deleting all references nothing points at the target anymore");

        // Without references the export drops the (meanwhile deleted) target: no zombie definitions.
        editor.executeCommand(new DeleteNodeCommand(findProperty(tree.getRoot(), "target")));
        String exported = EditTreeWriter.toJsonString(tree);
        assertFalse(exported.contains("_woodDefinitions"));
    }
}
