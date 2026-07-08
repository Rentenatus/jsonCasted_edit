/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.command.AddNodeCommand;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.command.MoveNodeCommand;
import de.jare.jsoncasted.editor.command.RenameNodeCommand;
import de.jare.jsoncasted.editor.command.SetAttributeCommand;
import de.jare.jsoncasted.editor.command.SetValueCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.JackAttribut;
import java.util.HashMap;
import java.util.Map;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for content-oriented command operations such as rename and set value.
 *
 * @author Jansuch Rentenatus
 */
public class TreeEditorContentNGTest implements ATestTools {

    public TreeEditorContentNGTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("===============================================");
        System.out.println("## Start TreeEditorContentNGTest.");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        System.out.println("## End TreeEditorContentNGTest.");
        System.out.println("===============================================");
    }

    /**
     * Test: Rename 3 nodes using array-based RenameNodeCommand.
     */
    @Test
    public void testRenameThreeNodes() {
        printTestHeader("testRenameThreeNodes");

        TreeEditorModel editor = new TreeEditorModel();
        EditNode root = editor.getTree().getRoot();

        EditNodeProperty node1 = new EditNodeProperty("oldName1");
        EditNodeProperty node2 = new EditNodeProperty("oldName2");
        EditNodeProperty node3 = new EditNodeProperty("oldName3");

        editor.executeCommand(new AddNodeCommand(root, node1));
        editor.executeCommand(new AddNodeCommand(root, node2));
        editor.executeCommand(new AddNodeCommand(root, node3));

        EditNode treeNode1 = root.getChildAt(0);
        EditNode treeNode2 = root.getChildAt(1);
        EditNode treeNode3 = root.getChildAt(2);

        editor.clearHistory();
        printEditorState(editor, "before rename");

        CommandResult renameResult = editor.executeCommand(new RenameNodeCommand(
                new EditNode[]{treeNode1, treeNode2, treeNode3},
                new String[]{"newName1", "newName2", "newName3"}
        ));
        printCommandResult("renameResult", renameResult);

        assertEquals(treeNode1.getName(), "newName1");
        assertEquals(treeNode2.getName(), "newName2");
        assertEquals(treeNode3.getName(), "newName3");

        printSubtree(editor, "tree after rename", root);

        CommandResult undoResult = editor.undo();
        printCommandResult("undoResult", undoResult);

        assertEquals(treeNode1.getName(), "oldName1");
        assertEquals(treeNode2.getName(), "oldName2");
        assertEquals(treeNode3.getName(), "oldName3");

        CommandResult redoResult = editor.redo();
        printCommandResult("redoResult", redoResult);

        assertEquals(treeNode1.getName(), "newName1");
        assertEquals(treeNode2.getName(), "newName2");
        assertEquals(treeNode3.getName(), "newName3");

        printSubtree(editor, "tree after redo", root);
        printTestFooter();
    }

    /**
     * Test: Set values for 3 nodes using array-based SetValueCommand.
     */
    @Test
    public void testSetValueThreeNodes() {
        printTestHeader("testSetValueThreeNodes");

        TreeEditorModel editor = new TreeEditorModel();
        EditNode root = editor.getTree().getRoot();

        EditNodeProperty node1 = new EditNodeProperty("valueNode1");
        EditNodeProperty node2 = new EditNodeProperty("valueNode2");
        EditNodeProperty node3 = new EditNodeProperty("valueNode3");

        editor.executeCommand(new AddNodeCommand(root, node1));
        editor.executeCommand(new AddNodeCommand(root, node2));
        editor.executeCommand(new AddNodeCommand(root, node3));

        EditNode treeNode1 = root.getChildAt(0);
        EditNode treeNode2 = root.getChildAt(1);
        EditNode treeNode3 = root.getChildAt(2);

        editor.clearHistory();
        printEditorState(editor, "before setValue");

        CommandResult setValueResult = editor.executeCommand(new SetValueCommand(
                new EditNode[]{treeNode1, treeNode2, treeNode3},
                new String[]{"value1", "value2", "value3"}
        ));
        printCommandResult("setValueResult", setValueResult);

        assertEquals(treeNode1.getValue(), "value1");
        assertEquals(treeNode2.getValue(), "value2");
        assertEquals(treeNode3.getValue(), "value3");

        printSubtree(editor, "tree after setValue", root);

        CommandResult undoResult = editor.undo();
        printCommandResult("undoResult", undoResult);

        assertEquals(root.getChildAt(0).getValue(), "");
        assertEquals(root.getChildAt(1).getValue(), "");
        assertEquals(root.getChildAt(2).getValue(), "");

        CommandResult redoResult = editor.redo();
        printCommandResult("redoResult", redoResult);

        assertEquals(treeNode1.getValue(), "value1");
        assertEquals(treeNode2.getValue(), "value2");
        assertEquals(treeNode3.getValue(), "value3");

        printSubtree(editor, "tree after redo", root);
        printTestFooter();
    }

    /**
     * Test: Combine rename and set value on the same nodes.
     */
    @Test
    public void testRenameAndSetValueCombined() {
        printTestHeader("testRenameAndSetValueCombined");

        TreeEditorModel editor = new TreeEditorModel();
        EditNode root = editor.getTree().getRoot();

        EditNodeProperty node1 = new EditNodeProperty("node1");
        EditNodeProperty node2 = new EditNodeProperty("node2");
        EditNodeProperty node3 = new EditNodeProperty("node3");

        editor.executeCommand(new AddNodeCommand(root, node1));
        editor.executeCommand(new AddNodeCommand(root, node2));
        editor.executeCommand(new AddNodeCommand(root, node3));

        EditNode treeNode1 = root.getChildAt(0);
        EditNode treeNode2 = root.getChildAt(1);
        EditNode treeNode3 = root.getChildAt(2);

        editor.clearHistory();
        printEditorState(editor, "before combined content commands");

        CommandResult renameResult = editor.executeCommand(new RenameNodeCommand(
                new EditNode[]{treeNode1, treeNode2, treeNode3},
                new String[]{"renamed1", "renamed2", "renamed3"}
        ));
        printCommandResult("renameResult", renameResult);

        CommandResult setValueResult = editor.executeCommand(new SetValueCommand(
                new EditNode[]{treeNode1, treeNode2, treeNode3},
                new String[]{"text1", "text2", "text3"}
        ));
        printCommandResult("setValueResult", setValueResult);

        assertEquals(treeNode1.getName(), "renamed1");
        assertEquals(treeNode2.getName(), "renamed2");
        assertEquals(treeNode3.getName(), "renamed3");

        assertEquals(treeNode1.getValue(), "text1");
        assertEquals(treeNode2.getValue(), "text2");
        assertEquals(treeNode3.getValue(), "text3");

        printSubtree(editor, "tree after rename + setValue", root);

        CommandResult undoSetValueResult = editor.undo();
        printCommandResult("undoSetValueResult", undoSetValueResult);

        assertEquals(treeNode1.getValue(), "");
        assertEquals(treeNode2.getValue(), "");
        assertEquals(treeNode3.getValue(), "");

        assertEquals(treeNode1.getName(), "renamed1");
        assertEquals(treeNode2.getName(), "renamed2");
        assertEquals(treeNode3.getName(), "renamed3");

        CommandResult undoRenameResult = editor.undo();
        printCommandResult("undoRenameResult", undoRenameResult);

        assertEquals(treeNode1.getName(), "node1");
        assertEquals(treeNode2.getName(), "node2");
        assertEquals(treeNode3.getName(), "node3");

        CommandResult redoRenameResult = editor.redo();
        printCommandResult("redoRenameResult", redoRenameResult);

        CommandResult redoSetValueResult = editor.redo();
        printCommandResult("redoSetValueResult", redoSetValueResult);

        assertEquals(treeNode1.getName(), "renamed1");
        assertEquals(treeNode2.getName(), "renamed2");
        assertEquals(treeNode3.getName(), "renamed3");

        assertEquals(treeNode1.getValue(), "text1");
        assertEquals(treeNode2.getValue(), "text2");
        assertEquals(treeNode3.getValue(), "text3");

        printSubtree(editor, "tree after redo chain", root);
        printTestFooter();
    }

    /**
     * Test: Undo to the beginning, skip redo of move, then redo rename and set
     * value.
     */
    @Test
    public void testSkipRedoMoveThenRedoContentCommands() {
        printTestHeader("testSkipRedoMoveThenRedoContentCommands");

        TreeEditorModel editor = new TreeEditorModel();
        EditNodeAbstract root = editor.getTree().getRoot();

        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("sourceProp")));
        EditNodeAbstract sourceProp = root.getChildAt(0);

        editor.executeCommand(new AddNodeCommand(sourceProp, new EditNodeObject("source")));
        EditNodeAbstract sourceParent = sourceProp.getChildAt(0);

        editor.executeCommand(new AddNodeCommand(sourceParent, new EditNodeProperty("node1")));
        editor.executeCommand(new AddNodeCommand(sourceParent, new EditNodeProperty("node2")));
        editor.executeCommand(new AddNodeCommand(sourceParent, new EditNodeProperty("node3")));

        editor.executeCommand(new AddNodeCommand(root, new EditNodeProperty("targetProp")));
        EditNodeAbstract targetProp = root.getChildAt(1);

        editor.executeCommand(new AddNodeCommand(targetProp, new EditNodeObject("target")));
        EditNodeAbstract targetParent = targetProp.getChildAt(0);

        EditNodeAbstract node1 = sourceParent.getChildAt(0);
        EditNodeAbstract node2 = sourceParent.getChildAt(1);
        EditNodeAbstract node3 = sourceParent.getChildAt(2);

        editor.clearHistory();
        printEditorState(editor, "before move/rename/setValue");
        printSubtree(editor, "source before move/rename/setValue", sourceParent);
        printSubtree(editor, "target before move/rename/setValue", targetParent);

        CommandResult moveCmd = editor.executeCommand(new MoveNodeCommand(
                new EditNodeAbstract[]{node1, node2, node3},
                targetParent,
                0
        ));
        assertNotNull(moveCmd);
        printCommandResult("moveResult", moveCmd);

        printSubtree(editor, "source after move, before rename/setValue", sourceParent);
        printSubtree(editor, "target after move, before rename/setValue", targetParent);

        node1 = targetParent.getChildAt(0);
        node2 = targetParent.getChildAt(1);
        node3 = targetParent.getChildAt(2);

        CommandResult renameCmd = editor.executeCommand(new RenameNodeCommand(
                new EditNode[]{node1, node2, node3},
                new String[]{"renamed1", "renamed2", "renamed3"}
        ));
        assertNotNull(renameCmd);
        printCommandResult("renameResult", renameCmd);

        CommandResult setValueCmd = editor.executeCommand(new SetValueCommand(
                new EditNode[]{node1, node2, node3},
                new String[]{"value1", "value2", "value3"}
        ));
        assertNotNull(setValueCmd);
        printCommandResult("setValueResult", setValueCmd);

        assertEquals(sourceParent.getChildCount(), 0);
        assertEquals(targetParent.getChildCount(), 3);
        assertEquals(node1.getName(), "renamed1");
        assertEquals(node2.getName(), "renamed2");
        assertEquals(node3.getName(), "renamed3");
        assertEquals(node1.getValue(), "value1");
        assertEquals(node2.getValue(), "value2");
        assertEquals(node3.getValue(), "value3");

        printSubtree(editor, "source after move/rename/setValue", sourceParent);
        printSubtree(editor, "target after move/rename/setValue", targetParent);

        CommandResult undoSetValue = editor.undo();
        printCommandResult("undoSetValue", undoSetValue);

        CommandResult undoRename = editor.undo();
        printCommandResult("undoRename", undoRename);

        CommandResult undoMove = editor.undo();
        printCommandResult("undoMove", undoMove);

        node1 = sourceParent.getChildAt(0);
        node2 = sourceParent.getChildAt(1);
        node3 = sourceParent.getChildAt(2);

        assertEquals(sourceParent.getChildCount(), 3, "After undo to start, nodes must be back in source");
        assertEquals(targetParent.getChildCount(), 0, "After undo to start, target must be empty");

        assertEquals(node1.getName(), "node1");
        assertEquals(node2.getName(), "node2");
        assertEquals(node3.getName(), "node3");

        assertEquals(node1.getValue(), "");
        assertEquals(node2.getValue(), "");
        assertEquals(node3.getValue(), "");

        printSubtree(editor, "source after undo chain", sourceParent);
        printSubtree(editor, "target after undo chain", targetParent);
        printEditorState(editor, "after undo chain");

        EditCommand skipped = editor.skipRedo();
        assertNotNull(skipped, "Skipped command should not be null");
        assertTrue(skipped instanceof MoveNodeCommand, "First redo entry should be the move command");
        System.out.println("skippedRedo: " + skipped);

        assertEquals(sourceParent.getChildCount(), 3, "Skip redo must not execute the move");
        assertEquals(targetParent.getChildCount(), 0, "Skip redo must leave target unchanged");

        CommandResult redoneRename = editor.redo();
        assertNotNull(redoneRename);
        assertTrue(redoneRename.getTrigger() instanceof RenameNodeCommand);
        printCommandResult("redoneRename", redoneRename);

        assertEquals(node1.getName(), "renamed1");
        assertEquals(node2.getName(), "renamed2");
        assertEquals(node3.getName(), "renamed3");

        assertEquals(sourceParent.getChildCount(), 3, "Rename redo should not move nodes");
        assertEquals(targetParent.getChildCount(), 0);

        CommandResult redoneSetValue = editor.redo();
        assertNotNull(redoneSetValue);
        assertTrue(redoneSetValue.getTrigger() instanceof SetValueCommand);
        printCommandResult("redoneSetValue", redoneSetValue);

        assertEquals(node1.getValue(), "value1");
        assertEquals(node2.getValue(), "value2");
        assertEquals(node3.getValue(), "value3");

        assertEquals(sourceParent.getChildCount(), 3, "SetValue redo should not move nodes");
        assertEquals(targetParent.getChildCount(), 0);

        printSubtree(editor, "source after redo rename/setValue", sourceParent);
        printSubtree(editor, "target after redo rename/setValue", targetParent);
        printEditorState(editor, "final editor state");

        printTestFooter();
    }

    /**
     * Test: Set attributes for a single node using SetAttributeCommand.
     */
    @Test
    public void testSetAttributeSingleNode() {
        printTestHeader("testSetAttributeSingleNode");

        TreeEditorModel editor = new TreeEditorModel();
        EditNode root = editor.getTree().getRoot();

        EditNodeProperty node = new EditNodeProperty("attrNode");
        editor.executeCommand(new AddNodeCommand(root, node));
        EditNode treeNode = root.getChildAt(0);

        editor.clearHistory();
        printEditorState(editor, "before setAttribute");

        Map<String, Object> newAttributes = new HashMap<>();
        newAttributes.put("name", "newName");
        newAttributes.put("primValue", "testValue");

        CommandResult setAttrResult = editor.executeCommand(new SetAttributeCommand(treeNode, newAttributes));
        printCommandResult("setAttrResult", setAttrResult);

        assertEquals(treeNode.getName(), "newName");
        assertEquals(treeNode.getValue(), "testValue");

        printSubtree(editor, "tree after setAttribute", root);

        CommandResult undoResult = editor.undo();
        printCommandResult("undoResult", undoResult);

        assertEquals(treeNode.getName(), "attrNode");
        assertEquals(treeNode.getValue(), null);

        CommandResult redoResult = editor.redo();
        printCommandResult("redoResult", redoResult);

        assertEquals(treeNode.getName(), "newName");
        assertEquals(treeNode.getValue(), "testValue");

        printTestFooter();
    }

    /**
     * Test: Set attributes for multiple nodes and combine with other commands.
     * Tests that read-only attributes (editStatus, editMessage) are preserved
     * in getAttributes.
     */
    @Test
    public void testSetAttributeCombined() {
        printTestHeader("testSetAttributeCombined");

        TreeEditorModel editor = new TreeEditorModel();
        EditNode root = editor.getTree().getRoot();

        EditNodeProperty node1 = new EditNodeProperty("node1");
        EditNodeProperty node2 = new EditNodeProperty("node2");
        EditNodeProperty node3 = new EditNodeProperty("node3");

        editor.executeCommand(new AddNodeCommand(root, node1));
        editor.executeCommand(new AddNodeCommand(root, node2));
        editor.executeCommand(new AddNodeCommand(root, node3));

        EditNode treeNode1 = root.getChildAt(0);
        EditNode treeNode2 = root.getChildAt(1);
        EditNode treeNode3 = root.getChildAt(2);

        // Directly set read-only attributes
        ((EditNodeAbstract) treeNode1).setEditStatus("okay");
        ((EditNodeAbstract) treeNode1).setEditMessage("All good");
        ((EditNodeAbstract) treeNode2).setEditStatus("warning");
        ((EditNodeAbstract) treeNode2).setEditMessage("Needs attention");
        ((EditNodeAbstract) treeNode3).setEditStatus("error");
        ((EditNodeAbstract) treeNode3).setEditMessage("Critical issue");

        editor.clearHistory();
        printEditorState(editor, "before setAttribute");

        // Set writable attributes via SetAttributeCommand
        Map<String, Object> attrs1 = new HashMap<>();
        attrs1.put("name", "renamed1");
        attrs1.put("primValue", "value1");

        Map<String, Object> attrs2 = new HashMap<>();
        attrs2.put("name", "renamed2");
        attrs2.put("primValue", "value2");

        Map<String, Object> attrs3 = new HashMap<>();
        attrs3.put("name", "renamed3");
        attrs3.put("primValue", "value3");

        CommandResult setAttrResult = editor.executeCommand(new SetAttributeCommand(
                new EditNode[]{treeNode1, treeNode2, treeNode3},
                new Map[]{attrs1, attrs2, attrs3}
        ));
        printCommandResult("setAttrResult", setAttrResult);

        assertEquals(treeNode1.getName(), "renamed1");
        assertEquals(treeNode1.getValue(), "value1");
        assertEquals(treeNode1.getEditStatus(), "okay");
        assertEquals(treeNode1.getEditMessage(), "All good");

        assertEquals(treeNode2.getName(), "renamed2");
        assertEquals(treeNode2.getValue(), "value2");
        assertEquals(treeNode2.getEditStatus(), "warning");
        assertEquals(treeNode2.getEditMessage(), "Needs attention");

        assertEquals(treeNode3.getName(), "renamed3");
        assertEquals(treeNode3.getValue(), "value3");
        assertEquals(treeNode3.getEditStatus(), "error");
        assertEquals(treeNode3.getEditMessage(), "Critical issue");

        // Verify read-only attributes are in getAttributes
        Map<String, JackAttribut> node1Attrs = treeNode1.getAttributes();
        assertNotNull(node1Attrs.get("|edit status"));
        assertNotNull(node1Attrs.get("|edit message"));
        assertNotNull(node1Attrs.get("|edit id"));

        printSubtree(editor, "tree after setAttribute", root);

        // Undo setAttribute
        CommandResult undoSetAttr = editor.undo();
        printCommandResult("undoSetAttr", undoSetAttr);

        // Names and values should be reverted, but read-only attributes remain
        assertEquals(treeNode1.getName(), "node1");
        assertEquals(treeNode1.getValue(), null);
        assertEquals(treeNode1.getEditStatus(), "okay");
        assertEquals(treeNode1.getEditMessage(), "All good");

        assertEquals(treeNode2.getName(), "node2");
        assertEquals(treeNode2.getValue(), null);
        assertEquals(treeNode2.getEditStatus(), "warning");
        assertEquals(treeNode2.getEditMessage(), "Needs attention");

        // Redo
        CommandResult redoSetAttr = editor.redo();
        printCommandResult("redoSetAttr", redoSetAttr);

        assertEquals(treeNode1.getName(), "renamed1");
        assertEquals(treeNode1.getValue(), "value1");
        assertEquals(treeNode1.getEditStatus(), "okay");
        assertEquals(treeNode1.getEditMessage(), "All good");

        printTestFooter();
    }

}
