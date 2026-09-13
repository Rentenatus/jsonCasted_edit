package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Tests for core EditNode classes: computeHash, tryAssignType, and
 * ParseState/EditStatus enum behavior.
 *
 * @author Janusch Rentenatus
 */
public class EditNodeCoreNGTest {

    // ========== computeHash tests ==========

    @Test
    public void testComputeHashChangesWithName() {
        EditNodeProperty prop = new EditNodeProperty("fieldA");
        long hash1 = prop.computeHash();
        prop.setName("fieldB");
        long hash2 = prop.computeHash();
        assertNotEquals(hash1, hash2, "Hash should change when name changes");
    }

    @Test
    public void testComputeHashChangesWithValue() {
        EditNodeProperty prop = new EditNodeProperty("field");
        long hash1 = prop.computeHash();
        prop.setValue("newValue");
        long hash2 = prop.computeHash();
        assertNotEquals(hash1, hash2, "Hash should change when value changes");
    }

    @Test
    public void testComputeHashChangesWithChildCount() {
        EditNodeObject parent = new EditNodeObject("parent");
        long hash1 = parent.computeHash();

        EditTimes weightMonitor = new EditTimes();
        parent.addNewChild("child1", false, weightMonitor);
        long hash2 = parent.computeHash();
        assertNotEquals(hash1, hash2, "Hash should change when child count changes");
    }

    @Test
    public void testComputeHashStableWithoutChanges() {
        EditNodeProperty prop = new EditNodeProperty("field");
        prop.setValue("value");
        long hash1 = prop.computeHash();
        long hash2 = prop.computeHash();
        assertEquals(hash1, hash2, "Hash should be stable without changes");
    }

    @Test
    public void testComputeHashPositiveLong() {
        EditNodeObject node = new EditNodeObject("test");
        long hash = node.computeHash();
        assertTrue(hash >= 0, "Hash should be non-negative");
    }

    // ========== EditNodeObject.tryAssignType tests ==========

    @Test
    public void testObjectTryAssignTypeNullDescriptor() {
        EditNodeObject node = new EditNodeObject("test");
        boolean result = node.tryAssignType(null);
        assertFalse(result);
        assertEquals(node.getEditStatus(), EditStatus.STATELESS);
    }

    @Test
    public void testObjectTryAssignTypeEmptyName() {
        EditNodeObject node = new EditNodeObject("");
        // Use a real model descriptor to test the empty name path
        JsonModelDescriptor descriptor = de.jare.jsonconfig.def.JsonConfigDefinition.INSTANCE
                .getModel().getOrCreateDescriptor();
        boolean result = node.tryAssignType(descriptor);
        assertFalse(result);
        assertEquals(node.getEditStatus(), EditStatus.WARNING);
    }

    @Test
    public void testObjectTryAssignTypeNotFound() {
        EditNodeObject node = new EditNodeObject("nonExistentType");
        JsonModelDescriptor descriptor = de.jare.jsonconfig.def.JsonConfigDefinition.INSTANCE
                .getModel().getOrCreateDescriptor();
        boolean result = node.tryAssignType(descriptor);
        assertFalse(result);
        assertEquals(node.getEditStatus(), EditStatus.WARNING);
        assertNotNull(node.getEditMessage());
    }

    // ========== EditNodeProperty.tryAssignType tests ==========

    @Test
    public void testPropertyTryAssignTypeNullDescriptor() {
        EditNodeProperty prop = new EditNodeProperty("field");
        boolean result = prop.tryAssignType(null);
        assertFalse(result);
        assertEquals(prop.getEditStatus(), EditStatus.STATELESS);
    }

    @Test
    public void testPropertyTryAssignTypeNoParent() {
        EditNodeProperty prop = new EditNodeProperty("field");
        JsonModelDescriptor descriptor = de.jare.jsonconfig.def.JsonConfigDefinition.INSTANCE
                .getModel().getOrCreateDescriptor();
        boolean result = prop.tryAssignType(descriptor);
        assertFalse(result);
        assertEquals(prop.getEditStatus(), EditStatus.WARNING);
    }

    @Test
    public void testPropertyTryAssignTypeEmptyName() {
        EditNodeObject parent = new EditNodeObject("parent");
        EditNodeProperty prop = new EditNodeProperty("");
        EditTimes weightMonitor = new EditTimes();
        parent.addNewChild("placeholder", false, weightMonitor);
        // Replace placeholder with prop
        parent.removeChild(parent.getChildAt(0));
        parent.addChild(prop, weightMonitor);

        JsonModelDescriptor descriptor = de.jare.jsonconfig.def.JsonConfigDefinition.INSTANCE
                .getModel().getOrCreateDescriptor();
        boolean result = prop.tryAssignType(descriptor);
        assertFalse(result);
        assertEquals(prop.getEditStatus(), EditStatus.WARNING);
    }

    @Test
    public void testPropertyTryAssignTypeUnknownField() {
        EditNodeObject parent = new EditNodeObject("parent");
        EditNodeProperty prop = new EditNodeProperty("nonExistentField");
        EditTimes weightMonitor = new EditTimes();
        parent.addChild(prop, weightMonitor);

        JsonModelDescriptor descriptor = de.jare.jsonconfig.def.JsonConfigDefinition.INSTANCE
                .getModel().getOrCreateDescriptor();
        boolean result = prop.tryAssignType(descriptor);
        assertFalse(result);
        assertEquals(prop.getEditStatus(), EditStatus.ERROR);
        assertTrue(prop.getEditMessage().contains("unknown"));
    }

    @Test
    public void testPropertyTryAssignTypeParentNoTypeError() {
        EditNodeObject parent = new EditNodeObject("parent");
        EditNodeProperty prop = new EditNodeProperty("level");
        EditTimes weightMonitor = new EditTimes();
        parent.addChild(prop, weightMonitor);

        JsonModelDescriptor descriptor = de.jare.jsonconfig.def.JsonConfigDefinition.INSTANCE
                .getModel().getOrCreateDescriptor();
        // Parent has no type, so field resolution should fail with WARNING
        boolean result = prop.tryAssignType(descriptor);
        assertFalse(result);
        // Either WARNING (parent has no type) or ERROR (field unknown)
        assertNotEquals(prop.getEditStatus(), EditStatus.OKAY);
    }

    // ========== ParseState enum tests ==========

    @Test
    public void testParseStateValues() {
        ParseState[] states = ParseState.values();
        assertEquals(states.length, 4);
        assertNotEquals(ParseState.NONE, null);
        assertNotEquals(ParseState.EDITED, null);
        assertNotEquals(ParseState.PENDING, null);
        assertNotEquals(ParseState.DONE, null);
    }

    @Test
    public void testParseStateNeedsParsing() {
        assertTrue(ParseState.NONE.needsParsing());
        assertTrue(ParseState.EDITED.needsParsing());
        assertFalse(ParseState.PENDING.needsParsing());
        assertFalse(ParseState.DONE.needsParsing());
    }

    @Test
    public void testParseStateGetByName() {
        assertEquals(ParseState.get("NONE"), ParseState.NONE);
        assertEquals(ParseState.get("EDITED"), ParseState.EDITED);
        assertEquals(ParseState.get("PENDING"), ParseState.PENDING);
        assertEquals(ParseState.get("DONE"), ParseState.DONE);
    }

    @Test
    public void testParseStateGetByNameCaseSensitive() {
        // get() is case-sensitive (uses toString().equals())
        assertEquals(ParseState.get("NONE"), ParseState.NONE);
        assertNull(ParseState.get("none")); // lowercase does not match
    }

    @Test
    public void testParseStateGetByNameNull() {
        assertNull(ParseState.get(null));
        assertNull(ParseState.get("nonExistent"));
    }

    // ========== EditStatus enum tests ==========

    @Test
    public void testEditStatusValues() {
        EditStatus[] statuses = EditStatus.values();
        assertEquals(statuses.length, 4);
    }

    @Test
    public void testEditStatusGetByName() {
        assertEquals(EditStatus.get("STATELESS"), EditStatus.STATELESS);
        assertEquals(EditStatus.get("OKAY"), EditStatus.OKAY);
        assertEquals(EditStatus.get("WARNING"), EditStatus.WARNING);
        assertEquals(EditStatus.get("ERROR"), EditStatus.ERROR);
    }

    @Test
    public void testEditStatusGetByNameNull() {
        assertNull(EditStatus.get(null));
        assertNull(EditStatus.get("nonExistent"));
    }

    // ========== EditTree queue tests ==========

    @Test
    public void testAddToParseQueueSetsPending() {
        EditTree tree = new EditTree("root");
        EditNodeAbstract root = tree.getRoot();
        root.setParseState(ParseState.EDITED);

        tree.addToParseQueue(root);
        assertEquals(root.getParseState(), ParseState.PENDING);
        assertFalse(tree.getPendingNodes().isEmpty());
        assertFalse(tree.getParseQueue().isEmpty());
    }

    @Test
    public void testRemoveFromPendingClearsPending() {
        EditTree tree = new EditTree("root");
        EditNodeAbstract root = tree.getRoot();

        tree.addToParseQueue(root);
        assertTrue(tree.getPendingNodes().contains(root));

        tree.removeFromPending(root);
        assertFalse(tree.getPendingNodes().contains(root));
    }

    @Test
    public void testAddToParseQueueAlwaysRequeues() {
        EditTree tree = new EditTree("root");
        EditNodeAbstract root = tree.getRoot();

        // Add to queue
        tree.addToParseQueue(root);
        int queueSize1 = tree.getParseQueue().size();

        // Add again — should always re-queue (AP-2 fix)
        tree.addToParseQueue(root);
        int queueSize2 = tree.getParseQueue().size();

        assertTrue(queueSize2 > queueSize1, "addToParseQueue should always re-queue");
        assertEquals(root.getParseState(), ParseState.PENDING);
    }

    @Test
    public void testAddToParseQueueNull() {
        EditTree tree = new EditTree("root");
        tree.addToParseQueue(null);
        assertTrue(tree.getParseQueue().isEmpty());
        assertTrue(tree.getPendingNodes().isEmpty());
    }

    // ========== EditNodeAbstract.needsParsing tests ==========

    @Test
    public void testNeedsParsingNone() {
        EditNodeObject node = new EditNodeObject("test");
        node.setParseState(ParseState.NONE);
        assertTrue(node.needsParsing());
    }

    @Test
    public void testNeedsParsingEdited() {
        EditNodeObject node = new EditNodeObject("test");
        node.setParseState(ParseState.EDITED);
        assertTrue(node.needsParsing());
    }

    @Test
    public void testNeedsParsingDone() {
        EditNodeObject node = new EditNodeObject("test");
        node.setParseState(ParseState.DONE);
        assertFalse(node.needsParsing());
    }

    @Test
    public void testNeedsParsingPending() {
        EditNodeObject node = new EditNodeObject("test");
        node.setParseState(ParseState.PENDING);
        assertFalse(node.needsParsing());
    }
}
