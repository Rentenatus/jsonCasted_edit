package de.jare.jsoncasted.editor.core;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Roundtrip tests for wood identified arrays: the {@code <"id">[...]} notation survives import, export and
 * re-import; the array property keeps its identity in the linking set, and a stub in an array element is written
 * back in the bare id notation.
 *
 * @author Janusch Rentenatus
 */
public class ArrayWoodIdRoundTripNGTest {

    private static final String SHARED_JSON = "{\n"
            + "  \"left\": <\"7\">[\"a\", \"b\"],\n"
            + "  \"right\": <\"7\">,\n"
            + "  \"plain\": [\"x\"]\n"
            + "}\n";

    private static EditTree importTree() throws java.io.IOException, JsonParseException {
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse(SHARED_JSON, JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        return JsonTreeConverter.convertRessourceToEditTree(resource, "root");
    }

    @Test
    public void testImportKeepsArrayIdentityInLinkingSet() throws Exception {
        EditTree tree = importTree();
        EditLinkingSet links = tree.getLinkingSet();
        assertNotNull(links);
        final Object shared = links.findObjectById("7");
        assertNotNull(shared, "the array identity must register in the linking set");
        assertTrue(shared instanceof EditNodeProperty, "the shared node must be the array property");
        assertEquals(((EditNodeProperty) shared).getObjektId(), "7");
    }

    @Test
    public void testExportKeepsNotation() throws Exception {
        EditTree tree = importTree();
        String exported = EditTreeWriter.toJsonString(tree);

        assertTrue(exported.contains("<\"7\">"), "the array identity must keep its notation: " + exported);
        assertTrue(exported.matches("(?s).*<\"7\">\\s*\\[.*"), "the id must prefix the array literal: " + exported);
        assertFalse(exported.contains("_woodObjectId"),
                "arrays carry their identity in the notation, not as a member: " + exported);
    }

    @Test
    public void testReimportKeepsIdentityAndReference() throws Exception {
        EditTree tree = importTree();
        String exported = EditTreeWriter.toJsonString(tree);

        de.jare.jsoncasted.lang.JsonResource second
                = JsonParserService.parse(exported, JsonDebugLevel.SIMPLE);
        assertNotNull(second, "the exported string must be parseable: " + exported);
        EditTree reimported = JsonTreeConverter.convertRessourceToEditTree(second, "root");
        EditLinkingSet links = reimported.getLinkingSet();
        assertNotNull(links.findObjectById("7"), "the array identity must survive the roundtrip");
        assertFalse(links.getReferencesTo("this::7").isEmpty(),
                "the stub must resolve against the shared array");
    }

    @Test
    public void testPlainArrayStaysPlain() throws Exception {
        EditTree tree = importTree();
        String exported = EditTreeWriter.toJsonString(tree);

        assertTrue(exported.contains("plain: ["), "a plain array must stay plain: " + exported);
        long idNotationCount = exported.chars().filter(c -> c == '<').count();
        assertEquals(idNotationCount, 1, "only the shared array carries the id notation: " + exported);
    }

}
