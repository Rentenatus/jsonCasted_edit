package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.JackUpdateAction;
import de.jare.jsoncasted.editor.command.RenameNodeCommand;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.debug.JsonDebugLevel;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Regression tests for the rename command: the result must carry update actions, so the
 * tree repaints and re-selects the renamed node on execute, redo and undo - a rename with
 * no update actions leaves the JTree untouched (the tree applies update actions only).
 *
 * @author Janusch Rentenatus
 */
public class RenameNodeCommandNGTest {

    private static EditTree importTree() throws java.io.IOException, de.jare.jsoncasted.io.JsonParseException {
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse("{ \"matrix\": 5 }", JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        return JsonTreeConverter.convertRessourceToEditTree(resource, "root");
    }

    private static EditNodeProperty matrixField(EditTree tree) {
        assertTrue(tree.getRoot().getChildAt(0) instanceof EditNodeProperty);
        return (EditNodeProperty) tree.getRoot().getChildAt(0);
    }

    private static void assertRepaintActions(CommandResult result) {
        boolean rebuild = false;
        boolean select = false;
        for (JackUpdateAction action : result.getUpdateActions()) {
            rebuild |= action == JackUpdateAction.REBUILD_AFFECTED;
            select |= action == JackUpdateAction.SELECT_UPDATED;
        }
        assertTrue(rebuild, "the renamed node is repainted: " + result.getUpdateActions().length + " actions");
        assertTrue(select, "the renamed node stays selected");
    }

    /**
     * Execute, redo and undo of a rename report the repaint actions: the tree listens to the
     * command results, but it applies their update actions only.
     */
    @Test
    public void testRenameReportsRepaintActions() throws Exception {
        final EditTree tree = importTree();
        final EditNodeProperty matrix = matrixField(tree);

        final RenameNodeCommand command = new RenameNodeCommand(matrix, "matrix99");
        assertRepaintActions(command.execute(tree, false));
        assertEquals(matrix.getName(), "matrix99", "execute renames the node");

        assertRepaintActions(command.undo(tree));
        assertEquals(matrix.getName(), "matrix", "undo restores the old name");

        assertRepaintActions(command.execute(tree, true));
        assertEquals(matrix.getName(), "matrix99", "redo renames the node again");
    }
}
