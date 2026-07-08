/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.command.AddNodeCommand;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.DeleteNodeCommand;
import de.jare.jsoncasted.editor.command.MoveNodeCommand;
import de.jare.jsoncasted.editor.command.SetValueCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for array-based command operations.
 *
 * <p>
 * The tests resolve affected nodes from the tree after structural changes
 * instead of relying on original Java object references.</p>
 *
 * @author Jansuch Rentenatus
 */
public class TreeEditorArraysNGTest implements ATestTools {

    public TreeEditorArraysNGTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("===============================================");
        System.out.println("## Start TreeEditorArraysNGTest.");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        System.out.println("## End TreeEditorArraysNGTest.");
        System.out.println("===============================================");
    }

    @Test
    public void testAddThreeNodes() {
        printTestHeader("testAddThreeNodes");

        TreeEditorModel editor = new TreeEditorModel();
        EditNode root = editor.getTree().getRoot();
        assertNotNull(root, "Root should not be null");
        assertEquals(root.getChildCount(), 0);

        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("arrayNode1")));
        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("arrayNode2")));
        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("arrayNode3")));
        editor.clearHistory();

        CommandResult result = editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("arrayNode4")));
        printCommandResult("addResult", result);
        printSubtree(editor, "root after add", root);

        assertNotNull(result, "AddNodeCommand result should not be null");
        assertEquals(result.getAddedNodes().length, 1, "One node should be reported as added");

        assertEquals(root.getChildCount(), 4, "Root should have 4 children");
        assertNodeNames(root, "arrayNode1", "arrayNode2", "arrayNode3", "arrayNode4");

        CommandResult undoResult = editor.undo();
        printCommandResult("undoResult", undoResult);
        printSubtree(editor, "root after undo", root);
        assertEquals(root.getChildCount(), 3, "After undo, root should have 3 children");
        assertNodeNames(root, "arrayNode1", "arrayNode2", "arrayNode3");

        CommandResult redoResult = editor.redo();
        printCommandResult("redoResult", redoResult);
        printSubtree(editor, "root after redo", root);
        assertEquals(root.getChildCount(), 4, "After redo, root should have 4 children again");
        assertNodeNames(root, "arrayNode1", "arrayNode2", "arrayNode3", "arrayNode4");

        printTestFooter();
    }

    @Test
    public void testDeleteThreeNodes() {
        printTestHeader("testDeleteThreeNodes");

        TreeEditorModel editor = new TreeEditorModel();
        EditNodeAbstract root = editor.getTree().getRoot();

        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("deleteNode1")));
        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("deleteNode2")));
        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("deleteNode3")));
        assertEquals(root.getChildCount(), 3);

        EditNodeAbstract n1 = root.getChildAt(0);
        EditNodeAbstract n2 = root.getChildAt(1);
        EditNodeAbstract n3 = root.getChildAt(2);

        editor.clearHistory();
        printSubtree(editor, "root before delete", root);

        CommandResult deleteResult = editor.executeCommand(new DeleteNodeCommand(new EditNodeAbstract[]{n1, n2, n3}));
        printCommandResult("deleteResult", deleteResult);
        printSubtree(editor, "root after delete", root);

        assertNotNull(deleteResult, "Delete result should not be null");
        assertEquals(deleteResult.getRemovedNodes().length, 3, "Three nodes should be reported as removed");

        assertEquals(root.getChildCount(), 0, "All 3 nodes should be deleted");

        CommandResult undoResult = editor.undo();
        printCommandResult("undoResult", undoResult);
        printSubtree(editor, "root after undo delete", root);

        assertNotNull(undoResult, "Undo delete result should not be null");
        assertEquals(root.getChildCount(), 3, "After undo, all 3 nodes should be restored");
        assertNodeNames(root, "deleteNode1", "deleteNode2", "deleteNode3");

        printTestFooter();
    }

    @Test
    public void testMoveThreeNodes() {
        printTestHeader("testMoveThreeNodes");

        TreeEditorModel editor = new TreeEditorModel();
        EditNodeAbstract root = editor.getTree().getRoot();

        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("sourceProp")));
        EditNodeAbstract sourceProp = root.getChildAt(0);

        editor.executeCommand(new AddNodeCommand(sourceProp, new EditNodeObject("source")));
        EditNodeAbstract sourceParent = sourceProp.getChildAt(0);

        editor.executeCommand(new AddNodeCommand(sourceParent, new EditNodeProperty("moveNode1")));
        editor.executeCommand(new AddNodeCommand(sourceParent, new EditNodeProperty("moveNode2")));
        editor.executeCommand(new AddNodeCommand(sourceParent, new EditNodeProperty("moveNode3")));

        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("targetProp")));
        EditNodeAbstract targetProp = root.getChildAt(1);

        editor.executeCommand(new AddNodeCommand(targetProp, new EditNodeObject("target")));
        EditNodeAbstract targetParent = targetProp.getChildAt(0);

        EditNodeAbstract node1 = sourceParent.getChildAt(0);
        EditNodeAbstract node2 = sourceParent.getChildAt(1);
        EditNodeAbstract node3 = sourceParent.getChildAt(2);

        editor.clearHistory();

        printEditorState(editor, "before move");
        printSubtree(editor, "source before move", sourceParent);
        printSubtree(editor, "target before move", targetParent);

        CommandResult moveResult = editor.executeCommand(new MoveNodeCommand(
                new EditNodeAbstract[]{node1, node2, node3},
                targetParent,
                0
        ));
        printCommandResult("moveResult", moveResult);
        printSubtree(editor, "source after move", sourceParent);
        printSubtree(editor, "target after move", targetParent);

        assertNotNull(moveResult, "Move result should not be null");
        assertEquals(moveResult.getAffectedNodes().length, 2, "Two parent nodes should be reported as moved");

        assertEquals(sourceParent.getChildCount(), 0, "Source should be empty");
        assertEquals(targetParent.getChildCount(), 3, "Target should have 3 nodes");
        assertNodeNames(targetParent, "moveNode1", "moveNode2", "moveNode3");

        CommandResult undoResult = editor.undo();
        printCommandResult("undoResult", undoResult);
        printSubtree(editor, "source after undo", sourceParent);
        printSubtree(editor, "target after undo", targetParent);

        assertEquals(sourceParent.getChildCount(), 3, "After undo, source should have 3 nodes");
        assertEquals(targetParent.getChildCount(), 0, "After undo, target should be empty");
        assertNodeNames(sourceParent, "moveNode1", "moveNode2", "moveNode3");

        printTestFooter();
    }

    @Test
    public void testMoveThreeNodesAtIndex() {
        printTestHeader("testMoveThreeNodesAtIndex");

        TreeEditorModel editor = new TreeEditorModel();
        EditNodeAbstract root = editor.getTree().getRoot();

        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("sourceProp")));
        EditNodeAbstract sourceProp = root.getChildAt(0);

        editor.executeCommand(new AddNodeCommand(sourceProp, new EditNodeObject("source")));
        EditNodeAbstract sourceParent = sourceProp.getChildAt(0);

        editor.executeCommand(new AddNodeCommand(sourceParent, new EditNodeProperty("moveNode1")));
        editor.executeCommand(new AddNodeCommand(sourceParent, new EditNodeProperty("moveNode2")));
        editor.executeCommand(new AddNodeCommand(sourceParent, new EditNodeProperty("moveNode3")));

        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("targetProp")));
        EditNodeAbstract targetProp = root.getChildAt(1);

        editor.executeCommand(new AddNodeCommand(targetProp, new EditNodeObject("target")));
        EditNodeAbstract targetParent = targetProp.getChildAt(0);

        editor.executeCommand(new AddNodeCommand(targetParent, new EditNodeProperty("existing1")));
        editor.executeCommand(new AddNodeCommand(targetParent, new EditNodeProperty("existing2")));

        EditNodeAbstract node1 = sourceParent.getChildAt(0);
        EditNodeAbstract node2 = sourceParent.getChildAt(1);
        EditNodeAbstract node3 = sourceParent.getChildAt(2);

        editor.clearHistory();

        printSubtree(editor, "source before indexed move", sourceParent);
        printSubtree(editor, "target before indexed move", targetParent);

        CommandResult moveResult = editor.executeCommand(new MoveNodeCommand(
                new EditNodeAbstract[]{node1, node2, node3},
                targetParent,
                1
        ));
        printCommandResult("moveResult", moveResult);
        printSubtree(editor, "source after indexed move", sourceParent);
        printSubtree(editor, "target after indexed move", targetParent);

        assertEquals(targetParent.getChildCount(), 5, "Target should have 5 nodes");
        assertNodeNames(targetParent, "existing1", "moveNode1", "moveNode2", "moveNode3", "existing2");

        printTestFooter();
    }

    @Test
    public void testSetValueThreeNodes() {
        printTestHeader("testSetValueThreeNodes");

        TreeEditorModel editor = new TreeEditorModel();
        EditNode root = editor.getTree().getRoot();

        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("valueNode1")));
        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("valueNode2")));
        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("valueNode3")));

        EditNode node1 = root.getChildAt(0);
        EditNode node2 = root.getChildAt(1);
        EditNode node3 = root.getChildAt(2);

        editor.clearHistory();

        String[] newValues = {"newValue1", "newValue2", "newValue3"};
        CommandResult setResult = editor.executeCommand(new SetValueCommand(
                new EditNode[]{node1, node2, node3},
                newValues
        ));
        printCommandResult("setResult", setResult);
        printSubtree(editor, "root after set value", root);

        assertNotNull(setResult, "SetValue result should not be null");
        assertEquals(setResult.getUpdatedNodes().length, 3, "Three nodes should be reported as updated");

        assertEquals(node1.getValue(), "newValue1", "node1 should have value newValue1");
        assertEquals(node2.getValue(), "newValue2", "node2 should have value newValue2");
        assertEquals(node3.getValue(), "newValue3", "node3 should have value newValue3");

        CommandResult undoResult = editor.undo();
        printCommandResult("undoResult", undoResult);
        printSubtree(editor, "root after undo set value", root);

        assertEquals(node1.getValue(), "", "After undo, node1 should have empty value");
        assertEquals(node2.getValue(), "", "After undo, node2 should have empty value");
        assertEquals(node3.getValue(), "", "After undo, node3 should have empty value");

        CommandResult redoResult = editor.redo();
        printCommandResult("redoResult", redoResult);
        printSubtree(editor, "root after redo set value", root);

        assertEquals(node1.getValue(), "newValue1", "After redo, node1 should have value newValue1");
        assertEquals(node2.getValue(), "newValue2", "After redo, node2 should have value newValue2");
        assertEquals(node3.getValue(), "newValue3", "After redo, node3 should have value newValue3");

        printTestFooter();
    }

    @Test
    public void testArrayCommandsUndoRedo() {
        printTestHeader("testArrayCommandsUndoRedo");

        TreeEditorModel editor = new TreeEditorModel();
        EditNodeAbstract root = editor.getTree().getRoot();

        EditNodeProperty n1 = new EditNodeProperty("test1");
        EditNodeProperty n2 = new EditNodeProperty("test2");
        EditNodeProperty n3 = new EditNodeProperty("test3");

        editor.executeCommand(new AddNodeCommand(root, n1));
        editor.executeCommand(new AddNodeCommand(root, n2));
        editor.executeCommand(new AddNodeCommand(root, n3));
        editor.clearHistory();

        EditNodeAbstract node1 = root.getChildAt(0);
        EditNodeAbstract node2 = root.getChildAt(1);
        EditNodeAbstract node3 = root.getChildAt(2);

        CommandResult setResult = editor.executeCommand(new SetValueCommand(
                new EditNodeAbstract[]{node1, node2, node3},
                new String[]{"val1", "val2", "val3"}
        ));
        printCommandResult("setResult", setResult);
        printSubtree(editor, "root after set value", root);

        assertEquals(node1.getValue(), "val1", "node1 should have value val1");
        assertEquals(node2.getValue(), "val2", "node2 should have value val2");
        assertEquals(node3.getValue(), "val3", "node3 should have value val3");

        CommandResult deleteResult = editor.executeCommand(new DeleteNodeCommand(new EditNodeAbstract[]{
            node1, node2, node3
        }));
        printCommandResult("deleteResult", deleteResult);
        printSubtree(editor, "root after delete", root);

        assertEquals(root.getChildCount(), 0);

        CommandResult undoDelete = editor.undo();
        printCommandResult("undoDelete", undoDelete);
        printSubtree(editor, "root after undo delete", root);

        assertEquals(root.getChildCount(), 3);
        node1 = root.getChildAt(0);
        node2 = root.getChildAt(1);
        node3 = root.getChildAt(2);
        assertEquals(node1.getValue(), "val1", "After undo delete, node1 should have value val1");
        assertEquals(node2.getValue(), "val2", "After undo delete, node2 should have value val2");
        assertEquals(node3.getValue(), "val3", "After undo delete, node3 should have value val3");

        CommandResult undoSet = editor.undo();
        printCommandResult("undoSet", undoSet);
        printSubtree(editor, "root after undo set value", root);

        assertEquals(node1.getValue(), "", "After undo set, node1 should have empty value");
        assertEquals(node2.getValue(), "", "After undo set, node2 should have empty value");
        assertEquals(node3.getValue(), "", "After undo set, node3 should have empty value");
        assertNodeNames(root, "test1", "test2", "test3");

        CommandResult redoSet = editor.redo();
        printCommandResult("redoSet", redoSet);
        printSubtree(editor, "root after redo set value", root);

        assertEquals(node1.getValue(), "val1", "After redo set, node1 should have value val1");
        assertEquals(node2.getValue(), "val2", "After redo set, node2 should have value val2");
        assertEquals(node3.getValue(), "val3", "After redo set, node3 should have value val3");

        CommandResult redoDelete = editor.redo();
        printCommandResult("redoDelete", redoDelete);
        printSubtree(editor, "root after redo delete", root);

        assertEquals(root.getChildCount(), 0);

        printTestFooter();
    }

    private static void assertNodeNames(EditNode parent, String... expectedNames) {
        assertEquals(parent.getChildCount(), expectedNames.length, "Unexpected child count");
        for (int i = 0; i < expectedNames.length; i++) {
            assertEquals(
                    parent.getChildAt(i).getName(),
                    expectedNames[i],
                    "Unexpected node name at index " + i
            );
        }
    }

}
