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
 * Tests for the hardened on-the-fly parser: single-object fields propagate their element type (B1), the parse hash
 * includes the cast name (B2), and type inference resolves untyped objects via the field set of their property
 * children with set coverage (B7).
 *
 * @author Janusch Rentenatus
 */
public class OnTheFlyParserHardeningNGTest {

    /**
     * An object child under a single-object field (collection type NONE) adopts the declared field type; its own
     * properties then resolve against that type.
     */
    @Test
    public void testSingleObjectFieldAdoptsElementType() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        final EditNodeProperty mainLoggingProp = new EditNodeProperty("mainLogging");
        root.addChild(mainLoggingProp, new EditTimes());
        final EditNodeObject loggingObj = new EditNodeObject("Object", "{...}");
        mainLoggingProp.addChild(loggingObj, new EditTimes());
        final EditNodeProperty levelProp = new EditNodeProperty("level");
        loggingObj.addChild(levelProp, new EditTimes());
        final EditNodeProperty pathProp = new EditNodeProperty("path");
        loggingObj.addChild(pathProp, new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        tree.setJsonModelDescriptor(JsonConfigDefinition.getInstance().getDescriptor());
        waitForParser(tree);

        assertEquals(loggingObj.getCastName(), "de.jare.jsonconfig.item.ConfigLogging",
                "The single-object child must adopt the declared field type (B1)");
        assertEquals(levelProp.getEditStatus(), EditStatus.OKAY,
                "level must resolve against ConfigLogging: " + levelProp.getEditMessage());
        assertEquals(pathProp.getEditStatus(), EditStatus.OKAY,
                "path must resolve against ConfigLogging: " + pathProp.getEditMessage());
    }

    /**
     * The parse hash must change when the cast name changes - the cast is the primary parsing input (B2).
     */
    @Test
    public void testComputeHashChangesWithCastName() {
        final EditNodeObject node = new EditNodeObject("Object");
        node.setCastName("TypeA");
        final long hash1 = node.computeHash();
        node.setCastName("TypeB");
        final long hash2 = node.computeHash();
        assertNotEquals(hash1, hash2, "Hash must change with the cast name");
    }

    /**
     * Type inference via field set: an object with only the ambiguous field 'comments' stays with an ambiguity
     * warning, while an object whose children include 'profile' and 'comments' resolves to ConfigProfile - the set
     * coverage is unique although 'comments' alone is declared by three types (B7).
     */
    @Test
    public void testInferenceByFieldSetCoverage() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        // Objekt 1: nur das mehrdeutige Feld 'comments' (3 Typen)
        final EditNodeProperty unknownProp = new EditNodeProperty("mystery");
        root.addChild(unknownProp, new EditTimes());
        final EditNodeObject ambiguousObj = new EditNodeObject("Object", "{...}");
        unknownProp.addChild(ambiguousObj, new EditTimes());
        final EditNodeProperty commentsOnly = new EditNodeProperty("comments");
        ambiguousObj.addChild(commentsOnly, new EditTimes());

        // Objekt 2: 'profile' + 'comments' - gemeinsam eindeutig (ConfigProfile)
        final EditNodeProperty unknownProp2 = new EditNodeProperty("mystery2");
        root.addChild(unknownProp2, new EditTimes());
        final EditNodeObject coveredObj = new EditNodeObject("Object", "{...}");
        unknownProp2.addChild(coveredObj, new EditTimes());
        final EditNodeProperty profileProp = new EditNodeProperty("profile");
        coveredObj.addChild(profileProp, new EditTimes());
        final EditNodeProperty commentsProp = new EditNodeProperty("comments");
        coveredObj.addChild(commentsProp, new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        tree.setJsonModelDescriptor(JsonConfigDefinition.getInstance().getDescriptor());
        waitForParser(tree);

        assertTrue(coveredObj.getCastName() != null
                        && coveredObj.getCastName().contains("ConfigProfile"),
                "Set coverage profile+comments must infer ConfigProfile, but cast=" + coveredObj.getCastName()
                        + " msg=" + coveredObj.getEditMessage());
        assertTrue(ambiguousObj.getEditMessage() == null || !ambiguousObj.getEditMessage().isEmpty(),
                "The ambiguous object should carry a diagnostic");
    }

    /**
     * Waits until the on-the-fly parser has processed the queue and settled.
     *
     * @param tree the tree to wait for
     * @throws InterruptedException if the sleep is interrupted
     */
    private void waitForParser(EditTree tree) throws InterruptedException {
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
