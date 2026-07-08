package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.core.EditNode;

/**
 *
 * @author Jansuch Rentenatus
 */
public interface ATestTools {

    default void printTestHeader(String testName) {
        System.out.println("===============================================");
        System.out.println(testName);
        System.out.println("===============================================");
    }

    default void printTestFooter() {
        System.out.println("===============================================");
    }

    default void printCommandResult(String label, CommandResult result) {
        System.out.println(label + ": " + result);
    }

    default void printEditorState(TreeEditorModel editor, String label) {
        System.out.println("~~>" + label + ": " + editor.toDebugString());
        System.out.println(editor.toHistoryString());
    }

    default void printSubtree(TreeEditorModel editor, String label, EditNode node) {
        System.out.println(label + ":");
        System.out.println(editor.toTreeString(node));
    }
}
