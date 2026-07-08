package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.clipboard.ClipboardManager;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.CopyToStashCommand;
import de.jare.jsoncasted.editor.command.CutToStashCommand;
import de.jare.jsoncasted.editor.command.PasteFromStashCommand;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author Jansuch Rentenatus
 */
public class ClipboardArrayTestNG implements ATestTools {

    public ClipboardArrayTestNG() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("===============================================");
        System.out.println("## Start ClipboardArrayTestNG.");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        System.out.println("## End ClipboardArrayTestNG.");
        System.out.println("===============================================");
    }
    private ClipboardManager clipboard;

    private EditTree treeA;
    private EditTree treeB;

    private TreeEditorModel editorA;
    private TreeEditorModel editorB;

    private EditNodeAbstract rootA;
    private EditNodeAbstract rootB;

    private EditNodeAbstract childA1;
    private EditNodeAbstract childA2;
    private EditNodeAbstract childA3;

    @BeforeMethod
    public void setUp() {
        clipboard = new ClipboardManager();
        editorA = new TreeEditorModel();
        editorB = new TreeEditorModel();

        treeA = editorA.getTree();
        rootA = treeA.getRoot();
        treeB = editorB.getTree();
        rootB = treeB.getRoot();

        childA1 = treeA.addNewChild(rootA, "a1", false);
        childA2 = treeA.addNewChild(rootA, "a2", false);
        childA3 = treeA.addNewChild(rootA, "a3", false);
        treeA.addNewChild(rootA, "a4", false);
        treeB.addNewChild(rootB, "b1", false);

    }

    @Test
    public void testCopyAndPasteThreeNodesAcrossTwoTreesWithUndoRedo() {
        printTestHeader("testCopyAndPasteThreeNodesAcrossTwoTreesWithUndoRedo");

        printEditorState(editorA, "Initial editorA");
        printEditorState(editorB, "Initial editorB");
        printSubtree(editorA, "Initial subtree A/root", rootA);
        printSubtree(editorB, "Initial subtree B/root", rootB);

        EditNodeAbstract[] copied = {
            childA1,
            childA2,
            childA3
        };

        CopyToStashCommand copyCmd = new CopyToStashCommand(
                clipboard,
                ClipboardManager.CLIPBOARD_STASH_NAME,
                copied
        );
        CommandResult copyExecuteResult = copyCmd.execute(treeA, false);
        printCommandResult("COPY[3] execute", copyExecuteResult);
        printEditorState(editorA, "After COPY[3] on editorA");

        PasteFromStashCommand pasteCmd = new PasteFromStashCommand(
                clipboard,
                ClipboardManager.CLIPBOARD_STASH_NAME,
                rootB,
                -1
        );
        CommandResult pasteExecuteResult = pasteCmd.execute(treeB, false);
        printCommandResult("PASTE[3] execute", pasteExecuteResult);
        printEditorState(editorB, "After PASTE[3] on editorB");
        printSubtree(editorB, "Subtree B/root after paste[3]", rootB);

        assertEquals(rootB.getChildCount(), 4,
                "Tree B should contain original child plus three pasted nodes");

        EditNodeAbstract[] pasted = pasteExecuteResult.getAddedNodes();
        assertEquals(pasted.length, 3,
                "Exactly three nodes must have been pasted");

        for (EditNodeAbstract pa : pasted) {
            assertNotNull(treeB.findNodeByIdAndRange(pa),
                    "Every pasted node must exist in tree B");
        }

        CommandResult pasteUndoResult = pasteCmd.undo(treeB);
        printCommandResult("PASTE[3] undo", pasteUndoResult);
        printEditorState(editorB, "After undo PASTE[3] on editorB");
        printSubtree(editorB, "Subtree B/root after undo paste[3]", rootB);

        assertEquals(rootB.getChildCount(), 1,
                "Undo should remove all three pasted nodes again");

        CommandResult pasteRedoResult = pasteCmd.execute(treeB, false);
        printCommandResult("PASTE[3] redo", pasteRedoResult);
        printEditorState(editorB, "After redo PASTE[3] on editorB");
        printSubtree(editorB, "Subtree B/root after redo paste[3]", rootB);

        assertEquals(rootB.getChildCount(), 4,
                "Redo should add all three pasted nodes again");

        printTestFooter();
    }

    @Test
    public void testCutAndPasteThreeNodesAcrossTwoTreesWithUndoRedo() {
        printTestHeader("testCutAndPasteThreeNodesAcrossTwoTreesWithUndoRedo");

        printEditorState(editorA, "Initial editorA");
        printEditorState(editorB, "Initial editorB");
        printSubtree(editorA, "Initial subtree A/root", rootA);
        printSubtree(editorB, "Initial subtree B/root", rootB);

        EditNodeAbstract[] cut = {
            childA1,
            childA2,
            childA3
        };

        CutToStashCommand cutCmd = new CutToStashCommand(
                clipboard,
                ClipboardManager.CLIPBOARD_STASH_NAME,
                cut
        );
        CommandResult cutExecuteResult = cutCmd.execute(treeA, false);
        printCommandResult("CUT[3] execute", cutExecuteResult);
        printEditorState(editorA, "After CUT[3] on editorA");
        printSubtree(editorA, "Subtree A/root after cut[3]", rootA);

        assertEquals(rootA.getChildCount(), 1,
                "After cutting three nodes, only one child should remain in tree A");
        assertNull(treeA.findNodeById(childA1.getEditId()), "a1 must be removed from tree A");
        assertNull(treeA.findNodeById(childA2.getEditId()), "a2 must be removed from tree A");
        assertNull(treeA.findNodeById(childA3.getEditId()), "a3 must be removed from tree A");

        CommandResult cutUndoResult = cutCmd.undo(treeA);
        printCommandResult("CUT[3] undo", cutUndoResult);
        printEditorState(editorA, "After undo CUT[3] on editorA");
        printSubtree(editorA, "Subtree A/root after undo cut[3]", rootA);

        assertEquals(rootA.getChildCount(), 4,
                "Undo should restore all three cut nodes");
        assertNotNull(treeA.findNodeById(childA1.getEditId()), "a1 must be restored");
        assertNotNull(treeA.findNodeById(childA2.getEditId()), "a2 must be restored");
        assertNotNull(treeA.findNodeById(childA3.getEditId()), "a3 must be restored");

        CommandResult cutRedoResult = cutCmd.execute(treeA, false);
        printCommandResult("CUT[3] redo", cutRedoResult);
        printEditorState(editorA, "After redo CUT[3] on editorA");
        printSubtree(editorA, "Subtree A/root after redo cut[3]", rootA);

        assertEquals(rootA.getChildCount(), 1,
                "Redo should cut all three nodes again");

        PasteFromStashCommand pasteCmd = new PasteFromStashCommand(
                clipboard,
                ClipboardManager.CLIPBOARD_STASH_NAME,
                rootB,
                -1
        );
        CommandResult pasteExecuteResult = pasteCmd.execute(treeB, false);
        printCommandResult("PASTE(cut)[3] execute", pasteExecuteResult);
        printEditorState(editorB, "After PASTE(cut)[3] on editorB");
        printSubtree(editorB, "Subtree B/root after paste(cut)[3]", rootB);

        assertEquals(rootB.getChildCount(), 4,
                "Tree B should contain original child plus three pasted cut nodes");

        EditNodeAbstract[] pasted = pasteExecuteResult.getAddedNodes();
        assertEquals(pasted.length, 3,
                "Exactly three cut nodes must have been pasted");

        for (EditNodeAbstract pa : pasted) {
            assertNotNull(treeB.findNodeByIdAndRange(pa),
                    "Every pasted cut node must exist in tree B");
        }

        CommandResult pasteUndoResult = pasteCmd.undo(treeB);
        printCommandResult("PASTE(cut)[3] undo", pasteUndoResult);
        printEditorState(editorB, "After undo PASTE(cut)[3] on editorB");
        printSubtree(editorB, "Subtree B/root after undo paste(cut)[3]", rootB);

        assertEquals(rootB.getChildCount(), 1,
                "Undo should remove all three pasted cut nodes from tree B");

        CommandResult pasteRedoResult = pasteCmd.execute(treeB, false);
        printCommandResult("PASTE(cut)[3] redo", pasteRedoResult);
        printEditorState(editorB, "After redo PASTE(cut)[3] on editorB");
        printSubtree(editorB, "Subtree B/root after redo paste(cut)[3]", rootB);

        assertEquals(rootB.getChildCount(), 4,
                "Redo should add all three pasted cut nodes again");

        printTestFooter();
    }

}
