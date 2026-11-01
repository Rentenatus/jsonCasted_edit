package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.command.JackUpdateAction;
import de.jare.jsoncasted.editor.command.RenameNodeCommand;
import de.jare.jsoncasted.editor.command.SetNodeValueCommand;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.debug.JsonDebugLevel;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Regression tests for the value command: setting the value of a property or a primitive row
 * object renames nothing but updates the value, notifies the parse machinery and reports the
 * repaint actions on execute, redo and undo - a command result without update actions leaves
 * the JTree untouched (the tree applies update actions only).
 *
 * @author Janusch Rentenatus
 */
public class SetNodeValueCommandNGTest {

    private static EditTree importTree() throws java.io.IOException, de.jare.jsoncasted.io.JsonParseException {
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse("{ \"matrix\": 5, \"values\": [ \"alpha\" ] }", JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        return JsonTreeConverter.convertRessourceToEditTree(resource, "root");
    }

    private static EditNodeProperty propertyOf(EditTree tree, String name) {
        for (int i = 0; i < tree.getRoot().getChildCount(); i++) {
            if (tree.getRoot().getChildAt(i) instanceof EditNodeProperty prop
                    && name.equals(prop.getName())) {
                return prop;
            }
        }
        fail("no property named " + name);
        return null;
    }

    private static void assertRepaintActions(de.jare.jsoncasted.editor.command.CommandResult result) {
        boolean rebuild = false;
        boolean select = false;
        for (JackUpdateAction action : result.getUpdateActions()) {
            rebuild |= action == JackUpdateAction.REBUILD_AFFECTED;
            select |= action == JackUpdateAction.SELECT_UPDATED;
        }
        assertTrue(rebuild, "the edited node is repainted");
        assertTrue(select, "the edited node stays selected");
    }

    /**
     * Execute, redo and undo of a value change report the repaint actions and set the value of
     * a property.
     */
    @Test
    public void testValueChangeOnProperty() throws Exception {
        final EditTree tree = importTree();
        final EditNodeProperty matrix = propertyOf(tree, "matrix");

        final SetNodeValueCommand command = new SetNodeValueCommand(matrix, "42");
        assertRepaintActions(command.execute(tree, false));
        assertEquals(matrix.getName(), "matrix", "a value change never touches the name");
        assertEquals(matrix.getValue(), "42", "execute sets the value");

        assertRepaintActions(command.undo(tree));
        assertEquals(matrix.getValue(), "5", "undo restores the old value");

        assertRepaintActions(command.execute(tree, true));
        assertEquals(matrix.getValue(), "42", "redo sets the value again");
    }

    /**
     * The value of a primitive row object (a scalar array row) is set as well - symmetric to
     * the property, through the same command.
     */
    @Test
    public void testValueChangeOnPrimitiveRowObject() throws Exception {
        final EditTree tree = importTree();
        final EditNodeProperty valuesField = propertyOf(tree, "values");
        assertTrue(valuesField.getChildAt(0) instanceof EditNodeObject);
        final EditNodeObject rowObject = (EditNodeObject) valuesField.getChildAt(0);

        final SetNodeValueCommand command = new SetNodeValueCommand(rowObject, "beta");
        assertRepaintActions(command.execute(tree, false));
        assertEquals(rowObject.getValue(), "beta", "execute sets the value of the row object");

        assertRepaintActions(command.undo(tree));
        assertEquals(rowObject.getValue(), "alpha", "undo restores the row value");

        assertRepaintActions(command.execute(tree, true));
        assertEquals(rowObject.getValue(), "beta", "redo sets the row value again");
    }

    /**
     * The value command and the rename command are orthogonal: a value change keeps the name,
     * a rename keeps the value.
     */
    @Test
    public void testValueAndRenameAreOrthogonal() throws Exception {
        final EditTree tree = importTree();
        final EditNodeProperty matrix = propertyOf(tree, "matrix");

        new SetNodeValueCommand(matrix, "42").execute(tree, false);
        new RenameNodeCommand(matrix, "matrix99").execute(tree, false);
        assertEquals(matrix.getName(), "matrix99");
        assertEquals(matrix.getValue(), "42", "the rename keeps the value");
    }
}
