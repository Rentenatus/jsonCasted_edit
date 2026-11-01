package de.jare.jsoncasted.editor.core.validation;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonResource;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Tests for the EditStringAnnotationValidator: the editString annotation must be a single element string array -
 * one row object of an array annotation or one inline string. Empty and multi element arrays are errors.
 *
 * @author Janusch Rentenatus
 */
public class EditStringAnnotationValidatorNGTest {

    private static final JsonModelDescriptor DESCRIPTOR = new JsonModelDescriptor("editStringTest");

    private static final String SINGLE_ELEMENT_JSON = "{\n"
            + "  \"profile\": {\n"
            + "    \"@editString\": [\"${name} (${typ})\"],\n"
            + "    \"name\": \"Alice\"\n"
            + "  }\n"
            + "}\n";

    private static final String INLINE_STRING_JSON = "{\n"
            + "  \"profile\": {\n"
            + "    \"@editString\": \"${name}\",\n"
            + "    \"name\": \"Alice\"\n"
            + "  }\n"
            + "}\n";

    private static final String MULTI_ELEMENT_JSON = "{\n"
            + "  \"profile\": {\n"
            + "    \"@editString\": [\"${a}\", \"${b}\"],\n"
            + "    \"name\": \"Alice\"\n"
            + "  }\n"
            + "}\n";

    private static final String EMPTY_ARRAY_JSON = "{\n"
            + "  \"profile\": {\n"
            + "    \"@editString\": [],\n"
            + "    \"name\": \"Alice\"\n"
            + "  }\n"
            + "}\n";

    private static final String OTHER_ANNOTATIONS_JSON = "{\n"
            + "  \"profile\": {\n"
            + "    \"@doc:profile\": [\"first\", \"second\"],\n"
            + "    \"@hint\": [\"a\", \"b\"],\n"
            + "    \"name\": \"Alice\"\n"
            + "  }\n"
            + "}\n";

    private static EditTree importTree(String json) throws JsonParseException, java.io.IOException {
        JsonResource resource = JsonParserService.parse(json, JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        return JsonTreeConverter.convertRessourceToEditTree(resource, "root");
    }

    private static List<EditNodeDiagnostic> editStringFindings(EditValidationResult result) {
        return result.getErrors().stream()
                .filter(d -> EditStringAnnotationValidator.CODE.equals(d.getCode()))
                .toList();
    }

    @Test
    public void testSingleElementArrayIsValid() throws Exception {
        EditTree tree = importTree(SINGLE_ELEMENT_JSON);

        EditValidationResult result = new EditValidationRunner().validate(tree, DESCRIPTOR);

        assertTrue(editStringFindings(result).isEmpty(),
                "a single element editString array is the valid template form");
    }

    @Test
    public void testInlineStringCountsAsSingleElement() throws Exception {
        EditTree tree = importTree(INLINE_STRING_JSON);

        EditValidationResult result = new EditValidationRunner().validate(tree, DESCRIPTOR);

        assertTrue(editStringFindings(result).isEmpty(),
                "an inline string is the tolerated shorthand for exactly one template");
    }

    @Test
    public void testMultiElementArrayIsError() throws Exception {
        EditTree tree = importTree(MULTI_ELEMENT_JSON);

        EditValidationResult result = new EditValidationRunner().validate(tree, DESCRIPTOR);

        final List<EditNodeDiagnostic> findings = editStringFindings(result);
        assertEquals(findings.size(), 1, "multiple template elements must be an error");
        assertTrue(findings.get(0).getMessage().contains("found 2"),
                "the message names the element count: " + findings.get(0).getMessage());
    }

    @Test
    public void testEmptyArrayIsError() throws Exception {
        EditTree tree = importTree(EMPTY_ARRAY_JSON);

        EditValidationResult result = new EditValidationRunner().validate(tree, DESCRIPTOR);

        final List<EditNodeDiagnostic> findings = editStringFindings(result);
        assertEquals(findings.size(), 1, "an empty editString array has no template and must be an error");
        assertTrue(findings.get(0).getMessage().contains("found 0"),
                "the message names the element count: " + findings.get(0).getMessage());
    }

    @Test
    public void testOtherAnnotationsAreIgnored() throws Exception {
        EditTree tree = importTree(OTHER_ANNOTATIONS_JSON);

        EditValidationResult result = new EditValidationRunner().validate(tree, DESCRIPTOR);

        assertTrue(editStringFindings(result).isEmpty(),
                "the single element rule applies to editString only, not to other annotations");
    }
}
