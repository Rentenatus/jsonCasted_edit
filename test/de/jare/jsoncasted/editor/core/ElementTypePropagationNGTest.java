/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

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
