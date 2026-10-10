package de.jare.jsoncasted.editor.core;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonResource;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Tests for the EditStringEvaluator: placeholder substitution against the fields of an object, the filter chain
 * (upper, lower, trim, substr, number, default), the literal escape and the display fallback chain (template of the
 * type, else the object name). The wiring test checks the adoption of @editString templates while loading a
 * description file.
 *
 * @author Janusch Rentenatus
 */
public class EditStringEvaluatorNGTest {

    private static final String PROFILE_JSON = "{\n"
            + "  \"_woodObjectId\": \"9\",\n"
            + "  \"name\": \"Alice\",\n"
            + "  \"typ\": \"admin\",\n"
            + "  \"zahl\": \"7\"\n"
            + "}\n";

    private static final String OWNER_JSON = "{\n"
            + "  \"owner\": {\n"
            + "    \"name\": \"Bob\"\n"
            + "  }\n"
            + "}\n";

    private static final String DESCRIPTION_JSON = "{\n"
            + "  \"modelName\": \"Mini\",\n"
            + "  \"describedTypes\": {\n"
            + "    \"profile\": {\n"
            + "      \"typeName\": \"profile\",\n"
            + "      \"nodeType\": \"OBJECT\",\n"
            + "      \"@editString\": [\"${name} (${typ})\"]\n"
            + "    },\n"
            + "    \"broken\": {\n"
            + "      \"typeName\": \"broken\",\n"
            + "      \"nodeType\": \"OBJECT\",\n"
            + "      \"@editString\": [\"${a}\", \"${b}\"]\n"
            + "    }\n"
            + "  }\n"
            + "}\n";

    private static EditTree importTree(String json) throws JsonParseException, IOException {
        JsonResource resource = JsonParserService.parse(json, JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        return JsonTreeConverter.convertRessourceToEditTree(resource, "root");
    }

    private static EditNodeObject rootNode(String json) throws JsonParseException, IOException {
        return (EditNodeObject) importTree(json).getRoot();
    }

    private static JsonTypeDescriptor typeWithEditString(String template) {
        final JsonTypeDescriptor type = new JsonTypeDescriptor("profile");
        type.setEditString(template);
        return type;
    }

    @Test
    public void testDisplayTextFormatsTheTemplate() throws Exception {
        final EditNodeObject node = rootNode(PROFILE_JSON);
        node.setJsonType(typeWithEditString("${name} (${typ})"));

        assertEquals(EditStringEvaluator.displayText(node), "Alice (admin)");
    }

    @Test
    public void testDisplayTextFallsBackToNameFieldAndNodeName() throws Exception {
        final EditNodeObject profileNode = rootNode(PROFILE_JSON);
        assertEquals(EditStringEvaluator.displayText(profileNode), "Alice",
                "without a template the name field is the common fallback");

        final EditNodeObject ownerNode = rootNode(OWNER_JSON);
        assertEquals(EditStringEvaluator.displayText(ownerNode), "root",
                "without a template and without a name field the object name remains");
    }

    @Test
    public void testFilterChain() throws Exception {
        final EditNodeObject node = rootNode(PROFILE_JSON);

        assertEquals(EditStringEvaluator.format(node, "${name|upper}"), "ALICE");
        assertEquals(EditStringEvaluator.format(node, "${typ|lower}"), "admin");
        assertEquals(EditStringEvaluator.format(node, "${zahl|number:000}"), "007");
        assertEquals(EditStringEvaluator.format(node, "${name|substr:0:3}"), "Ali");
        assertEquals(EditStringEvaluator.format(node, "${name|substr:-3}"), "ice");
        assertEquals(EditStringEvaluator.format(node, "${missing|default:'?'}"), "?");
        assertEquals(EditStringEvaluator.format(node, "${missing}"), "");
    }

    @Test
    public void testLiteralEscapeAndSpecialParams() throws Exception {
        final EditNodeObject node = rootNode(PROFILE_JSON);

        assertEquals(EditStringEvaluator.format(node, "$${name}"), "${name}");
        assertEquals(EditStringEvaluator.format(node, "${woodId}"), "9");
    }

    @Test
    public void testObjectFieldUsesRecursiveDisplay() throws Exception {
        final EditNodeObject node = rootNode(OWNER_JSON);
        final JsonTypeDescriptor ownerType = new JsonTypeDescriptor("ownerType");
        ownerType.setEditString("${name}");
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeProperty owner) {
                for (int j = 0; j < owner.getChildCount(); j++) {
                    if (owner.getChildAt(j) instanceof EditNodeObject ownerObject) {
                        ownerObject.setJsonType(ownerType);
                    }
                }
            }
        }
        node.setJsonType(typeWithEditString("owner=${owner}"));

        assertEquals(EditStringEvaluator.displayText(node), "owner=Bob");
    }

    @Test
    public void testDescriptionLoadAdoptsEditStrings() throws Exception {
        final File descriptionFile = File.createTempFile("editstring_desc", ".json");
        descriptionFile.deleteOnExit();
        Files.write(descriptionFile.toPath(), DESCRIPTION_JSON.getBytes(StandardCharsets.UTF_8));

        final EditTree tree = importTree("{}");
        JsonTreeConverter.loadDescrAndConvertRessourceToEditTree(tree, descriptionFile.getPath(), descriptionFile);

        final var descriptor = tree.getJsonModelDescriptor();
        assertNotNull(descriptor, "the description must load into a descriptor");
        assertEquals(descriptor.getType("profile").getEditString(), "${name} (${typ})",
                "a single element @editString is adopted while loading");
        assertNull(descriptor.getType("broken").getEditString(),
                "a multi element @editString is invalid and stays unadopted - the validator reports it");
    }
}
