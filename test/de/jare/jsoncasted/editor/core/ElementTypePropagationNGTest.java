/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.model.JsonCollectionType;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import de.jare.jsonconfig.def.JsonConfigDefinition;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Tests for the element type propagation of the on-the-fly parser: object nodes under a LIST/ARRAY property adopt
 * the element type from the property's field descriptor, so the anonymous "Object" nodes created by the tree
 * converter for array elements become typed and their fields resolve against them.
 *
 * @author Janusch Rentenatus
 */
public class ElementTypePropagationNGTest {

    /**
     * Array elements under a list-typed property adopt the declared element type; their own properties then resolve
     * against that type.
     */
    @Test
    public void testArrayElementsAdoptElementType() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        final EditNodeProperty profilesProp = new EditNodeProperty("profiles");
        root.addChild(profilesProp, new EditTimes());
        final EditNodeObject profileObj = new EditNodeObject("Object", "{...}");
        profilesProp.addChild(profileObj, new EditTimes());
        final EditNodeProperty profileField = new EditNodeProperty("profile");
        profileObj.addChild(profileField, new EditTimes());
        final EditNodeProperty commentsProp = new EditNodeProperty("comments");
        profileObj.addChild(commentsProp, new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        tree.setJsonModelDescriptor(JsonConfigDefinition.getInstance().getDescriptor());
        waitForParser(tree);

        assertEquals(profilesProp.getEditStatus(), EditStatus.OKAY,
                "profiles must resolve against the root type: " + profilesProp.getEditMessage());
        assertNotNull(profilesProp.getJsonField(), "profiles must carry its field descriptor");

        assertEquals(profileObj.getCastName(), "de.jare.jsonconfig.item.ConfigProfile",
                "The array element must adopt the declared element type");
        assertNotNull(profileObj.getJsonType(), "The array element must have the element type");
        assertEquals(profileObj.getEditStatus(), EditStatus.OKAY,
                "The array element must be okay: " + profileObj.getEditMessage());

        assertEquals(profileField.getEditStatus(), EditStatus.OKAY,
                "The profile property must resolve against ConfigProfile: " + profileField.getEditMessage());
        assertNotNull(profileField.getJsonField(), "The profile property must carry its field descriptor");

        assertEquals(commentsProp.getEditStatus(), EditStatus.OKAY,
                "The comments property must resolve against ConfigProfile: " + commentsProp.getEditMessage());
    }

    /**
     * Array elements of a primitive array adopt the primitive element type; they carry no fields themselves and are
     * therefore simply typed.
     */
    @Test
    public void testPrimitiveArrayElementsAdoptElementType() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        final EditNodeProperty commentsProp = new EditNodeProperty("comments");
        root.addChild(commentsProp, new EditTimes());
        final EditNodeObject commentElement = new EditNodeObject("Ein Kommentar");
        commentsProp.addChild(commentElement, new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        tree.setJsonModelDescriptor(JsonConfigDefinition.getInstance().getDescriptor());
        waitForParser(tree);

        assertEquals(commentElement.getCastName(), "String",
                "The primitive array element must adopt the String element type");
    }

    /**
     * A child added through the tree-level API under a property whose field is already resolved
     * receives the declared element type immediately; the confirming pass marks it OKAY.
     */
    @Test
    public void testNewChildUnderFieldedPropertyIsTyped() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        final EditNodeProperty profilesProp = new EditNodeProperty("profiles");
        root.addChild(profilesProp, new EditTimes());
        final EditNodeObject profileObj = new EditNodeObject("Object", "{...}");
        profilesProp.addChild(profileObj, new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        tree.setJsonModelDescriptor(JsonConfigDefinition.getInstance().getDescriptor());
        waitForParser(tree);

        final EditNodeObject added = new EditNodeObject("Object", "{...}");
        tree.addChild(profilesProp, added, profilesProp.getChildCount());
        waitForParser(tree);

        assertEquals(added.getCastName(), "de.jare.jsonconfig.item.ConfigProfile",
                "The added element must adopt the field's element type");
        assertEquals(added.getEditStatus(), EditStatus.OKAY,
                "The added element must be okay: " + added.getEditMessage());
    }

    /**
     * When the field of a property changes, an inherited element cast is redirected to the new
     * element type and confirmed by the next pass.
     */
    @Test
    public void testInheritedCastIsRedirectedOnFieldChange() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        final EditNodeProperty profilesProp = new EditNodeProperty("profiles");
        root.addChild(profilesProp, new EditTimes());
        final EditNodeObject profileObj = new EditNodeObject("Object", "{...}");
        profilesProp.addChild(profileObj, new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        tree.setJsonModelDescriptor(JsonConfigDefinition.getInstance().getDescriptor());
        waitForParser(tree);

        final JsonTypeDescriptor rootType = tree.getJsonModelDescriptor()
                .getType("de.jare.jsonconfig.item.ConfigRoot");
        profilesProp.setJsonField(rootType.getField("comments"));
        waitForParser(tree);

        assertEquals(profileObj.getCastName(), "String",
                "The inherited cast must follow the new element type");
        assertTrue(profileObj.isCastInherited(), "The redirected cast stays inherited");
        assertEquals(profileObj.getEditStatus(), EditStatus.OKAY,
                "The redirected element must be okay: " + profileObj.getEditMessage());
    }

    /**
     * A confirmed element whose type still fits the changed field is not requeued: parse state,
     * status and hash stay as they were.
     */
    @Test
    public void testConfirmedFittingElementIsUntouchedOnFieldChange() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        final EditNodeProperty commentsProp = new EditNodeProperty("comments");
        root.addChild(commentsProp, new EditTimes());
        final EditNodeObject commentElement = new EditNodeObject("Ein Kommentar");
        commentsProp.addChild(commentElement, new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        tree.setJsonModelDescriptor(JsonConfigDefinition.getInstance().getDescriptor());
        waitForParser(tree);

        final long hashBefore = commentElement.getLastParsedHash();
        final JsonTypeDescriptor profileType = tree.getJsonModelDescriptor()
                .getType("de.jare.jsonconfig.item.ConfigProfile");
        commentsProp.setJsonField(profileType.getField("comments"));
        waitForParser(tree);

        assertEquals(commentElement.getParseState(), ParseState.DONE,
                "A fitting confirmed element must not be requeued");
        assertEquals(commentElement.getEditStatus(), EditStatus.OKAY,
                "A fitting confirmed element keeps its okay status");
        assertEquals(commentElement.getLastParsedHash(), hashBefore,
                "A fitting confirmed element must not be re-parsed");
    }

    /**
     * An explicit cast decision is never overwritten by the propagation; a mismatch against the
     * field's element type is reported as ERROR instead.
     */
    @Test
    public void testExplicitCastMismatchIsReportedNotOverwritten() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        final EditNodeProperty profilesProp = new EditNodeProperty("profiles");
        root.addChild(profilesProp, new EditTimes());
        final EditNodeObject profileObj = new EditNodeObject("Object", "{...}");
        profilesProp.addChild(profileObj, new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        tree.setJsonModelDescriptor(JsonConfigDefinition.getInstance().getDescriptor());
        waitForParser(tree);

        profileObj.setCastName("de.jare.jsonconfig.item.ConfigFeature");
        waitForParser(tree);

        assertEquals(profileObj.getCastName(), "de.jare.jsonconfig.item.ConfigFeature",
                "The explicit cast decision must stand");
        assertEquals(profileObj.getEditStatus(), EditStatus.ERROR,
                "The mismatch must be reported");
        assertTrue(profileObj.getEditMessage() != null
                        && profileObj.getEditMessage().contains("does not match"),
                "The message must explain the mismatch: " + profileObj.getEditMessage());
    }

    /**
     * An inherited interface cast is the approximate answer: the field set inference refines it to
     * the concrete implementor when the field coverage is unique, and the interface type stays
     * without coverage instead of becoming an ambiguity warning.
     */
    @Test
    public void testInheritedInterfaceCastIsRefinedByFieldCoverage() throws Exception {
        final JsonModelDescriptor model = new JsonModelDescriptor("IfaceTest");
        final JsonTypeDescriptor iface = new JsonTypeDescriptor("IValue");
        final JsonTypeDescriptor implA = new JsonTypeDescriptor("ValueA");
        iface.addImplementor(implA);
        implA.addField(new JsonFieldDescriptor("alpha", "String"));
        implA.addField(new JsonFieldDescriptor("beta", "String"));
        final JsonTypeDescriptor rootType = new JsonTypeDescriptor("Root");
        rootType.addField(new JsonFieldDescriptor("items", "IValue", JsonCollectionType.LIST, false, false, null, null));
        model.addType(iface);
        model.addType(implA);
        model.addType(rootType);

        final EditNodeObject root = new EditNodeObject("this");
        root.setCastName("Root");
        final EditTree tree = new EditTree(root, new EditTimes());
        final EditNodeProperty itemsProp = new EditNodeProperty("items");
        root.addChild(itemsProp, new EditTimes());
        final EditNodeObject withFields = new EditNodeObject("Object", "{...}");
        itemsProp.addChild(withFields, new EditTimes());
        withFields.addChild(new EditNodeProperty("alpha"), new EditTimes());
        withFields.addChild(new EditNodeProperty("beta"), new EditTimes());
        final EditNodeObject withoutFields = new EditNodeObject("Object", "{...}");
        itemsProp.addChild(withoutFields, new EditTimes());

        tree.setJsonModelDescriptor(model);
        waitForParser(tree);

        assertEquals(withFields.getCastName(), "ValueA",
                "The inherited interface cast must be refined to the implementor");
        assertEquals(withFields.getEditStatus(), EditStatus.OKAY,
                "The refined element must be okay: " + withFields.getEditMessage());
        assertEquals(withoutFields.getCastName(), "IValue",
                "Without field coverage the inherited interface type stays");
        assertEquals(withoutFields.getEditStatus(), EditStatus.OKAY,
                "The approximate interface answer is not a defect");
    }

    /**
     * Waits until the on-the-fly parser has processed the queue and settled.
     *
     * @param tree the tree to wait for
     * @throws InterruptedException if the sleep is interrupted
     */
    private void waitForParser(EditTree tree) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (tree.getParseQueue().isEmpty() && tree.getPendingNodes().isEmpty()) {
                Thread.sleep(150);
                if (tree.getParseQueue().isEmpty() && tree.getPendingNodes().isEmpty()) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Parser did not settle within the timeout");
    }
}
