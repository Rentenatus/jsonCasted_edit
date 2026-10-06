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
 * Tests the sleep semantics of the on-the-fly parser: without a model there is nothing to check against, so the
 * parser sleeps exactly like in WITHOUT_SEMANTICS mode - no service runs, no node is queued, no status is touched.
 * Loading a descriptor wakes it up.
 *
 * @author Janusch Rentenatus
 */
public class ParserSleepsNGTest {

    /**
     * A tree without a model: no parser service, no queueing on edits, no status changes - and the wake-up binds
     * the fields once a descriptor arrives.
     */
    @Test
    public void testParserSleepsWithoutModel() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        final EditTree tree = new EditTree(root, new EditTimes());
        final EditNodeProperty comments = new EditNodeProperty("comments");
        root.addChild(comments, new EditTimes());
        final EditNodeObject row = new EditNodeObject("a comment");
        comments.addChild(row, new EditTimes());

        tree.setJsonModelDescriptor(null);
        assertFalse(tree.isParserRunning(), "No parser service runs without a model");
        assertTrue(tree.getParseQueue().isEmpty(), "The parse queue stays empty without a model");

        tree.notifyChildAdded(comments, row);
        tree.notifyTypeDescriptorChanged(root);
        tree.notifyNodeValueChanged(comments, null, "x");
        assertTrue(tree.getParseQueue().isEmpty() && tree.getPendingNodes().isEmpty(),
                "Edits while asleep queue nothing");
        assertEquals(row.getEditStatus(), EditStatus.STATELESS,
                "Nodes keep their stateless status while asleep");

        final EditNodeAbstract added = tree.addNewChild(root, "profiles", root.getChildCount(), true);
        assertNotNull(added, "Tree edits work while the parser sleeps");
        assertTrue(tree.getParseQueue().isEmpty(), "New nodes are not queued while asleep");
        assertEquals(added.getEditStatus(), EditStatus.STATELESS, "New nodes are not parsed while asleep");

        tree.setJsonModelDescriptor(new JsonConfigDefinition().getDescriptor());
        assertTrue(tree.isParserRunning(), "A loaded descriptor wakes the parser up");
        waitForParser(tree);
        assertEquals(((EditNodeProperty) findChild(root, "comments")).getJsonField().getFieldName(),
                "comments", "After waking up the fields bind against the model");
    }

    private static EditNode findChild(EditNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (name.equals(parent.getChildAt(i).getName())) {
                return parent.getChildAt(i);
            }
        }
        return null;
    }

    private static void waitForParser(EditTree tree) throws InterruptedException {
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
