/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which accompanies this distribution, and
 * is available at http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.editor.TreeEditorModel;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.editor.command.AddNodeCommand;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonResource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The edit-layer state model of doc/JSON_PROJECT.md on the EditTree itself: the working-copy axis CLEAN/EDITED
 * (first dirty tracking for editor trees - the command funnels flag, the save clears) and the file-currency axis
 * CURRENT/OUTDATED (the adopted import fingerprint against the file as it is now). Vocabulary boundaries hold:
 * STALE is the parse layer's word, hard/soft parse is no lifecycle dimension.
 *
 * @author Janusch Rentenatus
 */
public class EditTreeStateNGTest {

    private static Path tempDir;

    @BeforeClass
    public static void createTempDir() throws IOException {
        tempDir = Files.createTempDirectory("editproject-state");
    }

    @AfterClass
    public static void removeTempDir() throws IOException {
        if (tempDir != null) {
            for (Path child : Files.list(tempDir).toList()) {
                Files.deleteIfExists(child);
            }
            Files.deleteIfExists(tempDir);
        }
    }

    private EditTree importTree(String name, String content) throws IOException, JsonParseException {
        final Path file = tempDir.resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return JsonTreeConverter.fromJsonFile(file.toFile());
    }

    @Test
    public void fileImportIsCleanAndCurrent() throws IOException, JsonParseException {
        final EditTree tree = importTree("clean.json", "{\"one\": 1}");
        assertNotNull(tree, "the import must work");
        assertFalse(tree.isEdited(), "a fresh import is CLEAN - no unsaved changes");
        assertTrue(tree.isCurrent(), "and CURRENT - the file matches the import state");
    }

    @Test(dependsOnMethods = "fileImportIsCleanAndCurrent")
    public void commandsFlipToEdited() {
        final TreeEditorModel editor = new TreeEditorModel();
        final EditTree tree = editor.getTree();
        assertFalse(tree.isEdited(), "sanity: a fresh editor tree is CLEAN");

        editor.executeCommand(new AddNodeCommand((EditNode) tree.getRoot(),
                new EditNodeProperty("added")));
        assertTrue(tree.isEdited(), "an executed command leaves EDITED");

        editor.undo();
        assertTrue(tree.isEdited(), "undo also counts - conservative EDITED (concept direction)");

        editor.redo();
        assertTrue(tree.isEdited(), "redo naturally stays EDITED");
    }

    @Test(dependsOnMethods = "commandsFlipToEdited")
    public void externalChangeIsOutdated() throws IOException, JsonParseException {
        final EditTree tree = importTree("external.json", "{\"one\": 1}");
        assertTrue(tree.isCurrent(), "sanity: CURRENT at import");

        // the file changes under the working copy (size change is the deterministic part of the fingerprint)
        final Path file = tempDir.resolve("external.json");
        Files.write(file, "{\"one\": 123456}".getBytes(StandardCharsets.UTF_8));

        assertTrue(tree.isOutdated(), "the external change detaches the working copy - OUTDATED");
        assertFalse(tree.isCurrent());
        assertFalse(tree.isEdited(), "the file-currency axis stands alone - no memory change involved");
    }

    @Test(dependsOnMethods = "externalChangeIsOutdated")
    public void successfulSaveRefreshesBothAxes() throws IOException, JsonParseException {
        final EditTree tree = importTree("saved.json", "{\"one\": 1}");
        final TreeEditorModel editor = new TreeEditorModel(tree);
        editor.executeCommand(new AddNodeCommand((EditNode) tree.getRoot(),
                new EditNodeProperty("added")));
        assertTrue(tree.isEdited(), "sanity: EDITED after the command");

        // the save flow parses the saved file and adopts it (R4) - what the save action does after writing
        final File file = tempDir.resolve("saved.json").toFile();
        final JsonResource saved = JsonParserService.parse(file, JsonDebugLevel.SIMPLE);
        tree.adoptSavedBase(saved);

        assertFalse(tree.isEdited(), "the successful save is the new CLEAN state");
        assertTrue(tree.isCurrent(), "and the refreshed fingerprint matches the file again");
    }

    @Test(dependsOnMethods = "successfulSaveRefreshesBothAxes")
    public void stringImportHasNoFileTruth() throws IOException, JsonParseException {
        final EditTree tree = JsonTreeConverter.fromJsonString("{\"one\": 1}", "state");
        assertNotNull(tree);
        assertFalse(tree.isEdited(), "string import is CLEAN");
        assertFalse(tree.isOutdated(), "no origin file - the working copy cannot detach, stays CURRENT");
        assertTrue(tree.isCurrent());
    }

    @Test(dependsOnMethods = "stringImportHasNoFileTruth")
    public void vanishedFileCountsAsOutdated() throws IOException, JsonParseException {
        final EditTree tree = importTree("vanishing.json", "{\"one\": 1}");
        Files.delete(tempDir.resolve("vanishing.json"));

        assertTrue(tree.isOutdated(), "a vanished origin file counts as OUTDATED - never blindly overwrite");
    }
}
