package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditNodeObject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Tests for ValidationResult: diagnostic counting, immutability of
 * getDiagnosticsByNode, and toString output.
 *
 * @author Janusch Rentenatus
 */
public class ValidationResultNGTest {

    private ValidationResult result;

    @BeforeMethod
    public void setUp() {
        result = new ValidationResult();
    }

    @Test
    public void testEmptyResult() {
        assertTrue(result.isEmpty());
        assertEquals(result.getTotalCount(), 0);
        assertEquals(result.getErrorCount(), 0);
        assertEquals(result.getWarningCount(), 0);
        assertEquals(result.getInfoCount(), 0);
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
        assertFalse(result.hasInfo());
    }

    @Test
    public void testAddError() {
        EditNodeObject node = new EditNodeObject("testNode");
        result.add(EditNodeDiagnostic.error("err.code", "Error message", node, null));

        assertFalse(result.isEmpty());
        assertEquals(result.getTotalCount(), 1);
        assertEquals(result.getErrorCount(), 1);
        assertEquals(result.getWarningCount(), 0);
        assertEquals(result.getInfoCount(), 0);
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
    }

    @Test
    public void testAddWarning() {
        EditNodeObject node = new EditNodeObject("testNode");
        result.add(EditNodeDiagnostic.warning("warn.code", "Warning message", node, null));

        assertEquals(result.getTotalCount(), 1);
        assertEquals(result.getErrorCount(), 0);
        assertEquals(result.getWarningCount(), 1);
        assertTrue(result.hasWarnings());
        assertTrue(result.isValid()); // warnings don't invalidate
    }

    @Test
    public void testAddInfo() {
        EditNodeObject node = new EditNodeObject("testNode");
        result.add(EditNodeDiagnostic.info("info.code", "Info message", node, null));

        assertEquals(result.getTotalCount(), 1);
        assertEquals(result.getInfoCount(), 1);
        assertTrue(result.hasInfo());
        assertTrue(result.isValid());
    }

    @Test
    public void testMixedDiagnostics() {
        EditNodeObject node = new EditNodeObject("testNode");
        result.add(EditNodeDiagnostic.error("e1", "err", node, null));
        result.add(EditNodeDiagnostic.warning("w1", "warn", node, null));
        result.add(EditNodeDiagnostic.info("i1", "info", node, null));
        result.add(EditNodeDiagnostic.error("e2", "err2", node, null));

        assertEquals(result.getTotalCount(), 4);
        assertEquals(result.getErrorCount(), 2);
        assertEquals(result.getWarningCount(), 1);
        assertEquals(result.getInfoCount(), 1);
    }

    @Test
    public void testGetDiagnosticsByNodeImmutable() {
        EditNodeObject node1 = new EditNodeObject("node1");
        EditNodeObject node2 = new EditNodeObject("node2");
        result.add(EditNodeDiagnostic.error("e1", "err", node1, null));
        result.add(EditNodeDiagnostic.warning("w1", "warn", node2, null));

        var byNode = result.getDiagnosticsByNode();
        assertEquals(byNode.size(), 2);
        assertTrue(byNode.containsKey(node1));
        assertTrue(byNode.containsKey(node2));

        // Verify immutability of the outer map
        try {
            byNode.put(new EditNodeObject("intruder"),
                    java.util.Collections.emptyList());
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        // Verify immutability of inner lists
        var list = byNode.get(node1);
        try {
            list.add(EditNodeDiagnostic.error("hack", "hack", node1, null));
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testToStringContainsAllCounts() {
        EditNodeObject node = new EditNodeObject("testNode");
        result.add(EditNodeDiagnostic.error("e1", "err", node, null));
        result.add(EditNodeDiagnostic.warning("w1", "warn", node, null));
        result.add(EditNodeDiagnostic.info("i1", "info", node, null));

        String str = result.toString();
        assertTrue(str.contains("errors=1"));
        assertTrue(str.contains("warnings=1"));
        assertTrue(str.contains("infos=1"));
        assertTrue(str.contains("total=3"));
    }

    @Test
    public void testClear() {
        EditNodeObject node = new EditNodeObject("testNode");
        result.add(EditNodeDiagnostic.error("e1", "err", node, null));
        assertEquals(result.getTotalCount(), 1);

        result.clear();
        assertTrue(result.isEmpty());
        assertEquals(result.getTotalCount(), 0);
    }
}
