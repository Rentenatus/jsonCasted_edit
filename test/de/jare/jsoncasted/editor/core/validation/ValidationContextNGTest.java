package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Tests for ValidationContext: path tracking, getPathString ordering
 * (root to current), and diagnostic convenience methods.
 *
 * @author Janusch Rentenatus
 */
public class ValidationContextNGTest {

    @Test
    public void testPathStringRootToCurrentOrder() {
        ValidationResult result = new ValidationResult();
        EditNodeObject root = new EditNodeObject("root");
        ValidationContext context = new ValidationContext(root, null, result);

        context.pushPath(root);
        assertEquals(context.getPathString(), "root");

        EditNodeProperty child = new EditNodeProperty("child");
        context.pushPath(child);
        assertEquals(context.getPathString(), "root -> child");

        EditNodeProperty grandchild = new EditNodeProperty("grandchild");
        context.pushPath(grandchild);
        assertEquals(context.getPathString(), "root -> child -> grandchild");

        context.popPath();
        assertEquals(context.getPathString(), "root -> child");

        context.popPath();
        assertEquals(context.getPathString(), "root");
    }

    @Test
    public void testPeekPath() {
        ValidationResult result = new ValidationResult();
        EditNodeObject root = new EditNodeObject("root");
        ValidationContext context = new ValidationContext(root, null, result);

        context.pushPath(root);
        assertEquals(context.peekPath(), root);

        EditNodeProperty child = new EditNodeProperty("child");
        context.pushPath(child);
        assertEquals(context.peekPath(), child);

        context.popPath();
        assertEquals(context.peekPath(), root);

        context.popPath();
        assertNull(context.peekPath());
    }

    @Test
    public void testClearPath() {
        ValidationResult result = new ValidationResult();
        EditNodeObject root = new EditNodeObject("root");
        ValidationContext context = new ValidationContext(root, null, result);

        context.pushPath(root);
        context.pushPath(new EditNodeProperty("child"));
        assertFalse(context.getPath().isEmpty());

        context.clearPath();
        assertTrue(context.getPath().isEmpty());
        assertEquals(context.getPathString(), "");
    }

    @Test
    public void testGetPathImmutable() {
        ValidationResult result = new ValidationResult();
        EditNodeObject root = new EditNodeObject("root");
        ValidationContext context = new ValidationContext(root, null, result);

        context.pushPath(root);
        var path = context.getPath();
        try {
            path.add(new EditNodeProperty("intruder"));
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testAddErrorDelegatesToResult() {
        ValidationResult result = new ValidationResult();
        EditNodeObject root = new EditNodeObject("root");
        ValidationContext context = new ValidationContext(root, null, result);

        context.addError("test.error", "Test error message", root);
        assertEquals(result.getErrorCount(), 1);
        assertFalse(result.isValid());
    }

    @Test
    public void testAddWarningDelegatesToResult() {
        ValidationResult result = new ValidationResult();
        EditNodeObject root = new EditNodeObject("root");
        ValidationContext context = new ValidationContext(root, null, result);

        context.addWarning("test.warning", "Test warning message", root);
        assertEquals(result.getWarningCount(), 1);
        assertTrue(result.isValid());
    }

    @Test
    public void testAddInfoDelegatesToResult() {
        ValidationResult result = new ValidationResult();
        EditNodeObject root = new EditNodeObject("root");
        ValidationContext context = new ValidationContext(root, null, result);

        context.addInfo("test.info", "Test info message", root);
        assertEquals(result.getInfoCount(), 1);
        assertTrue(result.isValid());
    }

    @Test
    public void testGetRootNode() {
        ValidationResult result = new ValidationResult();
        EditNodeObject root = new EditNodeObject("rootNode");
        ValidationContext context = new ValidationContext(root, null, result);

        assertEquals(context.getRootNode(), root);
    }
}
