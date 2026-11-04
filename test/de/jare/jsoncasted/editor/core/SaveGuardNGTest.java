/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.io.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The save guard quadrant of doc/JSON_PROJECT.md: the two EditTree axes map onto the four decisions before
 * overwriting a file. The conflict quadrant (EDITED + OUTDATED) never resolves automatically - it is the 3-way
 * case with the base available for free.
 *
 * @author Janusch Rentenatus
 */
public class SaveGuardNGTest {

    private static Path tempDir;

    @BeforeClass
    public static void createTempDir() throws IOException {
        tempDir = Files.createTempDirectory("editproject-saveguard");
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

    private void changeExternally(String name, String content) throws IOException {
        Files.write(tempDir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void cleanAndCurrentHasNothingToDo() throws IOException, JsonParseException {
        final EditTree tree = importTree("one.json", "{\"one\": 1}");
        assertEquals(SaveGuard.decide(tree), SaveGuard.Decision.NOTHING_TO_DO,
                "CLEAN + CURRENT - nobody touched anything");
    }

    @Test(dependsOnMethods = "cleanAndCurrentHasNothingToDo")
    public void editedAndCurrentIsThePlainSave() throws IOException, JsonParseException {
        final EditTree tree = importTree("two.json", "{\"one\": 1}");
        tree.markEdited();
        assertEquals(SaveGuard.decide(tree), SaveGuard.Decision.PROCEED_SAVE,
                "EDITED + CURRENT - plain overwrite allowed");
    }

    @Test(dependsOnMethods = "editedAndCurrentIsThePlainSave")
    public void cleanButOutdatedReimports() throws IOException, JsonParseException {
        final EditTree tree = importTree("three.json", "{\"one\": 1}");
        changeExternally("three.json", "{\"one\": 999999}");
        assertEquals(SaveGuard.decide(tree), SaveGuard.Decision.REIMPORT,
                "CLEAN + OUTDATED - no user work at risk, re-import");
    }

    @Test(dependsOnMethods = "cleanButOutdatedReimports")
    public void editedAndOutdatedIsTheRealConflict() throws IOException, JsonParseException {
        final EditTree tree = importTree("four.json", "{\"one\": 1}");
        tree.markEdited();
        changeExternally("four.json", "{\"one\": 888888}");
        assertEquals(SaveGuard.decide(tree), SaveGuard.Decision.CONFLICT,
                "EDITED + OUTDATED - never a blind overwrite, the 3-way decision (policy asks)");
    }

    @Test(dependsOnMethods = "editedAndOutdatedIsTheRealConflict")
    public void inMemoryTreeAlwaysSaves() throws IOException, JsonParseException {
        final EditTree tree = JsonTreeConverter.fromJsonString("{\"one\": 1}", "mem");
        tree.markEdited();
        assertEquals(SaveGuard.decide(tree), SaveGuard.Decision.PROCEED_SAVE,
                "no origin file - no conflict possible, the plain save");

        // and back to nothing after a declared save
        tree.markClean();
        assertEquals(SaveGuard.decide(tree), SaveGuard.Decision.NOTHING_TO_DO);
    }
}
