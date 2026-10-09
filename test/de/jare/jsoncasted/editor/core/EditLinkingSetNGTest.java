package de.jare.jsoncasted.editor.core;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Tests for the wood link support on EditNodeObject and the reverse index of
 * EditLinkingSet: reference stubs, identity copy, registration, lookup and the
 * final delete cleanup.
 *
 * @author Janusch Rentenatus
 */
public class EditLinkingSetNGTest {

    // ========== EditNodeObject reference semantics ==========

    @Test
    public void testNodeWithoutLinkTargetIsNoReference() {
        EditNodeObject obj = new EditNodeObject("config");
        assertFalse(obj.isLinkReference());
        assertTrue(obj.canBeParentOfPropertyData());
        assertTrue(obj.canBeParentOfAnnotation());
    }

    @Test
    public void testLinkTargetTurnsNodeIntoReferenceStub() {
        EditNodeObject ref = new EditNodeObject("refName");
        ref.setLinkTarget("save::123");
        assertTrue(ref.isLinkReference());
        assertFalse(ref.canBeParentOfPropertyData(), "a reference stub must not carry field children");
        assertFalse(ref.canBeParentOfAnnotation(), "a reference stub must not carry annotations");
    }

    @Test
    public void testEmptyLinkTargetIsNoReference() {
        EditNodeObject ref = new EditNodeObject("refName");
        ref.setLinkTarget("");
        assertFalse(ref.isLinkReference());
    }

    @Test
    public void testAttributesExposeLinkTarget() {
        EditNodeObject ref = new EditNodeObject("refName");
        ref.setLinkTarget("save::123");
        assertNotNull(ref.getAttributes().get("|linkTarget"));
        assertEquals(ref.getAttributes().get("|linkTarget").getValue(), "save::123");
    }

    @Test
    public void testDeepCopyCarriesIdentityAndLinkTarget() {
        EditNodeObject original = new EditNodeObject("original");
        original.setObjektId("77");
        EditNodeObject ref = new EditNodeObject("refName");
        ref.setLinkTarget("save::77");

        EditNodeAbstract originalCopy = original.deepCopy(true);
        EditNodeAbstract refCopy = ref.deepCopy(true);

        assertTrue(originalCopy instanceof EditNodeObject);
        assertEquals(((EditNodeObject) originalCopy).getObjektId(), "77");
        assertTrue(((EditNodeObject) refCopy).isLinkReference());
        assertEquals(((EditNodeObject) refCopy).getLinkTarget(), "save::77");
    }

    // ========== EditLinkingSet reverse index ==========

    private EditLinkingSet newSet() {
        return new EditLinkingSet("save");
    }

    @Test
    public void testRegisterAndLookupLinkReferences() {
        EditLinkingSet set = newSet();
        EditNodeObject refA = new EditNodeObject("refA");
        refA.setLinkTarget("save::1");
        EditNodeObject refB = new EditNodeObject("refB");
        refB.setLinkTarget("save::1");

        assertTrue(set.registerLinkReference(refA));
        assertTrue(set.registerLinkReference(refB));
        assertFalse(set.registerLinkReference(refA), "double registration must not add a duplicate");

        assertEquals(set.getReferencesTo("save::1").size(), 2);
        assertTrue(set.hasReferencesTo("save::1"));
        assertFalse(set.hasReferencesTo("save::9"));
        assertTrue(set.getReferencesTo(null).isEmpty());
    }

    @Test
    public void testRegisterWithoutLinkTargetIsRejected() {
        EditLinkingSet set = newSet();
        EditNodeObject plain = new EditNodeObject("plain");
        assertFalse(set.registerLinkReference(plain));
        assertTrue(set.getReferencesTo("save::1").isEmpty());
    }

    @Test
    public void testRemoveLinkReference() {
        EditLinkingSet set = newSet();
        EditNodeObject ref = new EditNodeObject("ref");
        ref.setLinkTarget("save::1");
        set.registerLinkReference(ref);

        assertTrue(set.removeLinkReference(ref));
        assertFalse(set.hasReferencesTo("save::1"));
        assertFalse(set.removeLinkReference(ref), "second removal must report false");
    }

    @Test
    public void testFinalDeleteRemovesTargetAndIndex() {
        EditLinkingSet set = newSet();
        EditNodeObject target = new EditNodeObject("target");
        target.setObjektId("1");
        set.registerObject(target);
        EditNodeObject ref = new EditNodeObject("ref");
        ref.setLinkTarget("save::1");
        set.registerLinkReference(ref);
        set.registerLink("save::1", target);

        assertTrue(set.removeObject(target));
        assertFalse(set.hasReferencesTo("save::1"), "final delete must purge the reverse index entry");
        assertNull(set.findObjectById("1"));
        assertFalse(set.containsLink("save::1"), "link map entries pointing at the removed object must be cleaned");
    }

    @Test
    public void testRemoveObjectWithoutIdIsNoOp() {
        EditLinkingSet set = newSet();
        EditNodeObject plain = new EditNodeObject("plain");
        assertFalse(set.removeObject(plain));
    }

    @Test
    public void testClearResetsReverseIndex() {
        EditLinkingSet set = newSet();
        EditNodeObject ref = new EditNodeObject("ref");
        ref.setLinkTarget("save::1");
        set.registerLinkReference(ref);

        set.clear();
        assertFalse(set.hasReferencesTo("save::1"));
    }
}
