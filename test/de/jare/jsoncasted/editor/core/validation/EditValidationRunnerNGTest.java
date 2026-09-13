package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Tests for EditValidationRunner: null descriptor handling, contributor
 * management, and basic validation flow.
 *
 * @author Janusch Rentenatus
 */
public class EditValidationRunnerNGTest {

    @Test
    public void testValidateWithNullDescriptorReturnsEmpty() {
        EditTree tree = new EditTree("root");
        EditValidationRunner runner = new EditValidationRunner();

        ValidationResult result = runner.validate(tree, null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertTrue(result.isValid());
    }

    @Test
    public void testValidateSingleNodeWithNullDescriptorReturnsEmpty() {
        EditNodeObject node = new EditNodeObject("testNode");
        EditValidationRunner runner = new EditValidationRunner();

        ValidationResult result = runner.validateSingleNode(node, null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testValidateSubtreeWithNullDescriptorReturnsEmpty() {
        EditNodeObject root = new EditNodeObject("root");
        EditValidationRunner runner = new EditValidationRunner();

        ValidationResult result = runner.validateSubtree(root, null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testValidateRequiresNonNullTree() {
        EditValidationRunner runner = new EditValidationRunner();
        JsonModelDescriptor descriptor = null;

        assertThrows(NullPointerException.class,
                () -> runner.validate(null, descriptor));
    }

    @Test
    public void testValidateSingleNodeRequiresNonNullNode() {
        EditValidationRunner runner = new EditValidationRunner();
        assertThrows(NullPointerException.class,
                () -> runner.validateSingleNode(null, null));
    }

    @Test
    public void testValidateSubtreeRequiresNonNullNode() {
        EditValidationRunner runner = new EditValidationRunner();
        assertThrows(NullPointerException.class,
                () -> runner.validateSubtree(null, null));
    }

    @Test
    public void testContributorManagement() {
        EditValidationRunner runner = new EditValidationRunner();
        // CoreValidatorContributor is registered by default
        assertEquals(runner.getContributorCount(), 1);

        // Add a mock contributor
        ValidatorContributor mock = new ValidatorContributor() {
            @Override
            public void contribute(ValidatorRegistry registry) {
                // no-op
            }
        };
        runner.addContributor(mock);
        assertEquals(runner.getContributorCount(), 2);

        // Adding the same contributor should not duplicate
        runner.addContributor(mock);
        assertEquals(runner.getContributorCount(), 2);

        // Remove contributor
        assertTrue(runner.removeContributor(mock));
        assertEquals(runner.getContributorCount(), 1);

        // Remove non-existent returns false
        assertFalse(runner.removeContributor(mock));
    }

    @Test
    public void testClearContributors() {
        EditValidationRunner runner = new EditValidationRunner();
        assertEquals(runner.getContributorCount(), 1);

        runner.clearContributors();
        assertEquals(runner.getContributorCount(), 0);
    }

    @Test
    public void testToString() {
        EditValidationRunner runner = new EditValidationRunner();
        String str = runner.toString();
        assertTrue(str.contains("contributors=1"));
    }
}
