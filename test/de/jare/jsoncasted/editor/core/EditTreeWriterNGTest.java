/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsonconfig.def.JsonConfigDefinition;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Tests for {@link EditTreeWriter}: field level annotations are flattened into their composite keys, transient
 * annotations are filtered when their declaration is loaded, scalars keep their JSON type and the written string
 * reloads into an equivalent tree.
 *
 * @author Janusch Rentenatus
 */
public class EditTreeWriterNGTest {

    /**
     * Field annotations flatten into their composite key right before their field, object annotations keep their
     * simple key, and transient annotations disappear from the output when the declaration is loaded.
     */
    @Test
    public void testCompositeFlatteningAndTransientFilter() throws Exception {
        final EditTree tree = buildAnnotatedTree();
        final String json = EditTreeWriter.toJsonString(tree);

        assertTrue(json.contains("@hint: ["), "The simple object annotation keeps its unquoted key: " + json);
        assertTrue(json.contains("\"@doc:comments\": ["),
                "The field annotation is flattened to its composite key: " + json);
        assertTrue(json.indexOf("\"@doc:comments\"") < json.indexOf("comments: ["),
                "The composite key is written before its field: " + json);
        assertFalse(json.contains("@javadoc"), "The transient annotation is filtered on save: " + json);
        assertFalse(json.contains("hint\""), "Simple keys stay unquoted: " + json);
    }

    /**
     * Scalars keep their JSON type: strings are quoted, numbers and booleans stay raw, a missing value becomes null.
     */
    @Test
    public void testScalarTypesAreTypeFaithful() {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        final EditNodeProperty text = new EditNodeProperty("text", JsonNodeType.STRING);
        text.setValue("10");
        root.addChild(text, new EditTimes());
        final EditNodeProperty number = new EditNodeProperty("count", JsonNodeType.LONG);
        number.setValue("11434");
        root.addChild(number, new EditTimes());
        final EditNodeProperty flag = new EditNodeProperty("enabled", JsonNodeType.BOOLEAN);
        flag.setValue("false");
        root.addChild(flag, new EditTimes());
        final EditNodeProperty nothing = new EditNodeProperty("nothing", JsonNodeType.NULL);
        root.addChild(nothing, new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        final String json = EditTreeWriter.toJsonString(tree);

        assertTrue(json.contains("text: \"10\""), "A string value stays quoted: " + json);
        assertTrue(json.contains("count: 11434"), "A long value stays raw: " + json);
        assertTrue(json.contains("enabled: false"), "A boolean value stays raw: " + json);
        assertTrue(json.contains("nothing: null"), "A missing value becomes null: " + json);
    }

    /**
     * The written string reloads into an equivalent tree: the composite key anchors under its target field again and
     * the annotation rows survive.
     */
    @Test
    public void testRoundtripAnchorsTheCompositeAgain() throws Exception {
        final EditTree tree = buildAnnotatedTree();
        final String json = EditTreeWriter.toJsonString(tree);

        final java.io.File temp = java.io.File.createTempFile("woodroundtrip", ".json");
        temp.deleteOnExit();
        java.nio.file.Files.write(temp.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        final EditTree reloaded = JsonTreeConverter.fromJsonFile(temp);
        waitForParser(reloaded);
        final EditNodeObject root = (EditNodeObject) reloaded.getRoot();
        final EditNode comments = findChild(root, "comments");
        assertNotNull(comments, "The field reloads: " + json);
        final EditNode doc = findChild(comments, "@doc");
        assertNotNull(doc, "The composite annotation anchors under its field again: " + json);
        assertEquals(doc.getChildCount(), 1, "The annotation row survives the roundtrip");
    }

    // ========== Helpers ==========

    /**
     * Builds a tree with a declared object annotation (hint), a transient object annotation (javadoc) and a field
     * annotation (doc on comments), bound against a fresh Config definition with exactly these declarations.
     */
    private static EditTree buildAnnotatedTree() throws InterruptedException {
        final JsonConfigDefinition definition = new JsonConfigDefinition();
        definition.getRootClass().addAnnotation("hint");
        definition.getRootClass().addAnnotation("javadoc", true);
        definition.getRootClass().getField("comments").addAnnotation("doc");

        final EditNodeObject root = new EditNodeObject("seedConfig");
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");

        final EditNodeProperty hint = new EditNodeProperty("@hint", JsonNodeType.ARRAY);
        root.addChild(hint, new EditTimes());
        hint.addChild(new EditNodeObject("declared object annotation"), new EditTimes());

        final EditNodeProperty javadoc = new EditNodeProperty("@javadoc", JsonNodeType.ARRAY);
        root.addChild(javadoc, new EditTimes());
        javadoc.addChild(new EditNodeObject("session-local note"), new EditTimes());

        final EditNodeProperty comments = new EditNodeProperty("comments", JsonNodeType.ARRAY);
        root.addChild(comments, new EditTimes());
        final EditNodeProperty doc = new EditNodeProperty("@doc", JsonNodeType.ARRAY);
        comments.addChild(doc, new EditTimes());
        doc.addChild(new EditNodeObject("field annotation row"), new EditTimes());
        comments.addChild(new EditNodeObject("Das ist ein Json Config Datei."), new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        tree.setJsonModelDescriptor(definition.getDescriptor());
        waitForParser(tree);
        return tree;
    }

    private static EditNode findChild(EditNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i).getName().equals(name)) {
                return parent.getChildAt(i);
            }
        }
        return null;
    }

    private static void waitForParser(EditTree tree) throws InterruptedException {
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
