package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.editor.core.EditLinkingSet;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.debug.JsonDebugLevel;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Tests for the WoodLinkValidator: dangling references are errors, duplicate
 * wood object ids are warnings, unreferenced held objects are infos - and a
 * clean linked tree validates without findings.
 *
 * @author Janusch Rentenatus
 */
public class WoodLinkValidatorNGTest {

    private static final String LINKED_JSON = "{\n"
            + "  \"_woodObjectId\": \"1\",\n"
            + "  \"target\": {\n"
            + "    \"_woodObjectId\": \"2\",\n"
            + "    \"name\": \"Target\"\n"
            + "  },\n"
            + "  \"a\": {\n"
            + "    \"_woodLink\": \"this::2\"\n"
            + "  }\n"
            + "}\n";

    private static final JsonModelDescriptor DESCRIPTOR = new JsonModelDescriptor("woodLinkTest");

    private EditTree importTree() throws java.io.IOException, de.jare.jsoncasted.io.JsonParseException {
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse(LINKED_JSON, JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        return JsonTreeConverter.convertRessourceToEditTree(resource, "root");
    }

    /**
     * Objects are children of properties, not of the root: the test wraps new objects in a property first.
     */
    private static EditNodeProperty newHolderUnder(EditTree tree, String name) {
        EditNodeProperty holder = new EditNodeProperty(name);
        tree.addChild(tree.getRoot(), holder);
        return holder;
    }

    @Test
    public void testCleanLinkedTreeHasNoWoodFindings() throws Exception {
        EditTree tree = importTree();
        EditValidationResult result = new EditValidationRunner().validate(tree, DESCRIPTOR);

        assertTrue(result.getErrors().stream().noneMatch(d -> "woodlink.dangling".equals(d.getCode())),
                "a resolving link is no error");
        assertTrue(result.getWarnings().stream().noneMatch(d -> "woodlink.duplicateId".equals(d.getCode())),
                "a single identity is no warning");
        assertTrue(result.getInfos().stream().noneMatch(d -> "woodlink.unreferenced".equals(d.getCode())),
                "all held objects of the import are referenced");
    }

    @Test
    public void testDanglingLinkIsError() throws Exception {
        EditTree tree = importTree();
        EditLinkingSet linkingSet = tree.getLinkingSet();

        EditNodeProperty holder = newHolderUnder(tree, "c");
        EditNodeObject dangling = new EditNodeObject("dangling");
        dangling.setLinkTarget("this::404");
        linkingSet.registerLinkReference(dangling);
        tree.addChild(holder, dangling);

        EditValidationResult result = new EditValidationRunner().validate(tree, DESCRIPTOR);
        assertTrue(result.getErrors().stream().anyMatch(d -> "woodlink.dangling".equals(d.getCode())),
                "a stub whose target does not resolve must be an error");
    }

    @Test
    public void testDuplicateIdentityIsWarning() throws Exception {
        EditTree tree = importTree();

        EditNodeProperty holder = newHolderUnder(tree, "copyHolder");
        EditNodeObject copy = new EditNodeObject("targetCopy");
        copy.setObjektId("2");
        tree.addChild(holder, copy);

        EditValidationResult result = new EditValidationRunner().validate(tree, DESCRIPTOR);
        assertTrue(result.getWarnings().stream().anyMatch(d -> "woodlink.duplicateId".equals(d.getCode())),
                "two tree nodes with the same wood id must be a warning");
    }

    @Test
    public void testHeldUnreferencedObjectIsInfo() throws Exception {
        EditTree tree = importTree();
        EditLinkingSet linkingSet = tree.getLinkingSet();

        // Hold a fresh object with an identity but no references and no tree position.
        EditNodeObject held = new EditNodeObject("held");
        held.setObjektId("9");
        linkingSet.registerObject(held);

        EditValidationResult result = new EditValidationRunner().validate(tree, DESCRIPTOR);
        assertTrue(result.getInfos().stream().anyMatch(d -> "woodlink.unreferenced".equals(d.getCode())),
                "a held object without references is a cleanup hint");
    }
}
