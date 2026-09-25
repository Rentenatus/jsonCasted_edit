package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditNodeObject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Tests for EditValidationResult: diagnostic counting, immutability of
 * getDiagnosticsByNode, and toString output.
 *
 * @author Janusch Rentenatus
 */
public class EditValidationResultNGTest {

    private EditValidationResult result;

    @BeforeMethod
    public void setUp() {
        result = new EditValidationResult();
    }

    @Test
    public void testEmptyResult() {
        assertTrue(result.isEmpty());
        assertEquals(result.getDiagnosticCount(), 0);
        assertEquals(result.getErrorCount(), 0);
        assertEquals(result.getWarningCount(), 0);
        assertEquals(result.getInfoCount(), 0);
        assertTrue(result.isValid());
        assertTrue(result.isClean());
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
        assertFalse(result.hasInfos());
    }

    @Test
    public void testAddError() {
        EditNodeObject node = new EditNodeObject("testNode");
        result.add(EditNodeDiagnostic.error("err.code", "Error message", node, null));

        assertFalse(result.isEmpty());
        assertEquals(result.getDiagnosticCount(), 1);
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

        assertEquals(result.getDiagnosticCount(), 1);
        assertEquals(result.getErrorCount(), 0);
        assertEquals(result.getWarningCount(), 1);
        assertTrue(result.hasWarnings());
        assertTrue(result.isValid()); // warnings don't invalidate
    }

    @Test
    public void testAddInfo() {
        EditNodeObject node = new EditNodeObject("testNode");
        result.add(EditNodeDiagnostic.info("info.code", "Info message", node, null));

        assertEquals(result.getDiagnosticCount(), 1);
        assertEquals(result.getInfoCount(), 1);
        assertTrue(result.hasInfos());
        assertTrue(result.isValid());
    }

    @Test
    public void testMixedDiagnostics() {
        EditNodeObject node = new EditNodeObject("testNode");
        result.add(EditNodeDiagnostic.error("e1", "err", node, null));
        result.add(EditNodeDiagnostic.warning("w1", "warn", node, null));
        result.add(EditNodeDiagnostic.info("i1", "info", node, null));
        result.add(EditNodeDiagnostic.error("e2", "err2", node, null));

        assertEquals(result.getDiagnosticCount(), 4);
        assertEquals(result.getErrorCount(), 2);
        assertEquals(result.getWarningCount(), 1);
        assertEquals(result.getInfoCount(), 1);
        assertEquals(result.getErrors().size(), 2);
        assertEquals(result.getWarnings().size(), 1);
        assertEquals(result.getInfos().size(), 1);
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
        assertTrue(str.contains("errorCount=1"), str);
        assertTrue(str.contains("warningCount=1"), str);
        assertTrue(str.contains("infoCount=1"), str);
        assertTrue(str.contains("total=3"), str);
    }

    @Test
    public void testPrettyPrintContainsNodeNames() {
        EditNodeObject node = new EditNodeObject("testNode");
        result.add(EditNodeDiagnostic.error("e1", "err", node, null));

        String str = result.prettyPrint();
        assertTrue(str.contains("[ERROR] e1 at testNode: err"), str);
    }

    @Test
    public void testClear() {
        EditNodeObject node = new EditNodeObject("testNode");
        result.add(EditNodeDiagnostic.error("e1", "err", node, null));
        assertEquals(result.getDiagnosticCount(), 1);

        result.clear();
        assertTrue(result.isEmpty());
        assertEquals(result.getDiagnosticCount(), 0);
    }
}
