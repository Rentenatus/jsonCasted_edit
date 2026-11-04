/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.io.JsonParseException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The session clamp of doc/JSON_PROJECT.md (doc/PLAN_JsonProject.md phase 3): EditProject converts every
 * resource of ONE JsonProject load exactly once into an EditTree - B exists as a single EditTree shared by
 * the A and E sides. Fixtures: the A/E/B constellation of the registry tests (temp files).
 *
 * @author Janusch Rentenatus
 */
public class EditProjectNGTest {

    private static Path tempDir;

    @BeforeClass
    public static void createTempDir() throws IOException {
        tempDir = Files.createTempDirectory("editproject-session");
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

    private static String write(String name, String content) throws IOException {
        final Path file = tempDir.resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file.toAbsolutePath().toString();
    }

    private static String escaped(String path) {
        return path.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String box(String... lines) {
        return "\"_woodProviders\": [" + String.join(", ", lines) + "]";
    }

    private static String provider(String synonym, String path) {
        return "{\"synonym\": \"" + synonym + "\", \"filename\": \"" + escaped(path) + "\"}";
    }

    /**
     * The classic constellation: A and E both reference B under the same synonym dep; the main P references
     * A and E.
     */
    private void buildSharedFixture() throws IOException {
        final String b = write("B.json", "{\"shared\": {\"_woodObjectId\": \"5\", \"text\": \"B\"}}");
        final String a = write("A.json", "{" + box(provider("dep", b))
                + ", \"ref\": {\"_woodLink\": \"dep::5\"}}");
        final String e = write("E.json", "{" + box(provider("dep", b))
                + ", \"ref\": {\"_woodLink\": \"dep::5\"}}");
        write("P.json", "{" + box(provider("subA", a), provider("subE", e)) + ", \"anything\": 1}");
    }

    @Test
    public void openConvertsEachResourceExactlyOnce() throws IOException, JsonParseException {
        buildSharedFixture();
        final EditProject project = EditProject.open(tempDir.resolve("P.json").toFile());

        assertNotNull(project.getMainTree(), "the main tree exists");
        assertEquals(project.getSortedSynonyms().size(), 4, "self, subA, subE, dep");
        assertNotNull(project.findBySynonym("subA"), "A converted once, findable");
        assertNotNull(project.findBySynonym("dep"), "B converted once, findable");
        project.close();
    }

    @Test(dependsOnMethods = "openConvertsEachResourceExactlyOnce")
    public void bIsOneEditTreeForBothConsumers() throws IOException, JsonParseException {
        buildSharedFixture();
        final EditProject project = EditProject.open(tempDir.resolve("P.json").toFile());

        final EditTree bTree = project.findBySynonym("dep");
        assertNotNull(bTree, "B's tree exists");
        final File bFile = new File(bTree.getBaseSnapshot().getResourceFile());
        assertSame(project.findByPath(bFile.getCanonicalPath()), bTree,
                "B == B' on the session side too - one EditTree shared by A and E");
        project.close();
    }

    @Test(dependsOnMethods = "bIsOneEditTreeForBothConsumers")
    public void sessionTreesStandOnTheParseLayersSharedInstances() throws IOException, JsonParseException {
        buildSharedFixture();
        final EditProject project = EditProject.open(tempDir.resolve("P.json").toFile());

        final EditTree bTree = project.findBySynonym("dep");
        assertSame(bTree.getBaseSnapshot(), project.getParseProject().findBySynonym("dep"),
                "the edit layer consumes the parse layer's SHARED instance - no second parse anywhere");
        assertFalse(bTree.isEdited(), "the fresh conversion is CLEAN");
        assertTrue(bTree.isCurrent(), "and CURRENT - the adopted fingerprint matches the file");
        project.close();
    }

    @Test(dependsOnMethods = "sessionTreesStandOnTheParseLayersSharedInstances")
    public void editOnOneTreeIsTheEditTextsOwnState() throws IOException, JsonParseException {
        buildSharedFixture();
        final EditProject project = EditProject.open(tempDir.resolve("P.json").toFile());

        for (final EditTree tree : new EditTree[]{project.getMainTree(),
            project.findBySynonym("subA"), project.findBySynonym("subE"),
            project.findBySynonym("dep")}) {
            assertFalse(tree.isEdited(), "nothing touched - CLEAN");
            assertTrue(tree.isCurrent(), "no file changed - CURRENT");
        }
        project.close();
    }

    @Test(dependsOnMethods = "editOnOneTreeIsTheEditTextsOwnState")
    public void closedSessionRefusesAccess() throws IOException, JsonParseException {
        buildSharedFixture();
        final EditProject project = EditProject.open(tempDir.resolve("P.json").toFile());
        project.close();

        try {
            project.findBySynonym("dep");
            fail("a closed session must refuse registry access");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }
}
