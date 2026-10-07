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
 * Tests for the annotation binding of the on-the-fly parser (annotation concept, decisions 6 to 8): declared
 * annotations bind OKAY, undeclared ones are tolerated free annotations with a WARNING. The composite target is
 * structural - anchored under a field the annotation is composite and binds against the field declaration, anchored
 * under an object it is a simple object annotation. The annotation hangs at its structural parent: deleting the field
 * removes the annotation with it, anchoring it under a matching field re-binds it.
 *
 * @author Janusch Rentenatus
 */
public class AnnotationBindingNGTest {

    /**
     * Declared object annotations bind OKAY, undeclared ones are tolerated free annotations with a WARNING, and the
     * rows of an annotation adopt the implicit String element type.
     */
    @Test
    public void testDeclaredAndFreeObjectAnnotations() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        final EditNodeAnnotation hintProp = new EditNodeAnnotation("hint");
        root.addChild(hintProp, new EditTimes());
        final EditNodeObject hintRow = new EditNodeObject("declared object annotation");
        hintProp.addChild(hintRow, new EditTimes());
        final EditNodeAnnotation dokProp = new EditNodeAnnotation("dok");
        root.addChild(dokProp, new EditTimes());
        final EditNodeObject dokRow = new EditNodeObject("free object annotation");
        dokProp.addChild(dokRow, new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        tree.setJsonModelDescriptor(newConfigRootDescriptor());
        waitForParser(tree);

        assertEquals(hintProp.getEditStatus(), EditStatus.OKAY,
                "A declared object annotation must bind OKAY: " + hintProp.getEditMessage());
        assertEquals(hintRow.getCastName(), "String",
                "Annotation rows must adopt the implicit String element type");
        assertEquals(hintRow.getEditStatus(), EditStatus.OKAY,
                "Annotation rows must parse OKAY: " + hintRow.getEditMessage());
        assertEquals(dokProp.getEditStatus(), EditStatus.WARNING,
                "An undeclared object annotation must be a tolerated WARNING, not an error: " + dokProp.getEditMessage());
    }

    /**
     * The composite target is structural: an annotation anchored under a field node shows its composite name, adopts
     * the field as its target and binds against the field's declared annotations. Renaming edits the annotation name
     * only - the kind and the target never change through a rename.
     */
    @Test
    public void testCompositeAnnotationAnchorsUnderFieldNode() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        final EditNodeProperty commentsProp = new EditNodeProperty("comments", JsonNodeType.ARRAY);
        root.addChild(commentsProp, new EditTimes());
        final EditNodeObject commentRow = new EditNodeObject("Das ist ein Json Config Datei.");
        commentsProp.addChild(commentRow, new EditTimes());
        final EditNodeAnnotation docProp = new EditNodeAnnotation("doc");
        commentsProp.addChild(docProp, new EditTimes());
        final EditNodeObject docRow = new EditNodeObject("composite annotation row");
        docProp.addChild(docRow, new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        tree.setJsonModelDescriptor(newConfigRootDescriptor());
        waitForParser(tree);

        assertEquals(docProp.getName(), "@doc:comments",
                "Anchored under its field the annotation shows the composite key (what gets saved)");
        assertTrue(docProp.isComposite(), "Anchored under a field the annotation is composite");
        assertEquals(docProp.getTargetField(), "comments", "The target field is derived from the parent");
        assertSame(docProp.getParent(), commentsProp,
                "The annotation is a child of its target field node (Kind von comments)");
        assertEquals(docProp.getEditStatus(), EditStatus.OKAY,
                "A declared field annotation must bind OKAY: " + docProp.getEditMessage());
        assertEquals(commentRow.getEditStatus(), EditStatus.OKAY,
                "The field's own rows must stay untouched: " + commentRow.getEditMessage());

        docProp.setName("dok");
        assertEquals(docProp.getName(), "@dok:comments",
                "A rename edits the annotation name only, never kind or target");
        assertEquals(docProp.getAnnotationName(), "dok");
    }

    /**
     * Deleting the field removes the annotation with it - it hangs at its structural parent, there is no rescue and no
     * parallel anchor state. Anchoring the same detached annotation under a new matching field re-binds it immediately.
     */
    @Test
    public void testFieldDeletionRemovesTheAnnotationWithIt() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        final EditNodeProperty commentsProp = new EditNodeProperty("comments", JsonNodeType.ARRAY);
        root.addChild(commentsProp, new EditTimes());
        final EditNodeAnnotation docProp = new EditNodeAnnotation("doc");
        commentsProp.addChild(docProp, new EditTimes());
        final EditNodeObject docRow = new EditNodeObject("annotation of the comments field");
        docProp.addChild(docRow, new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        tree.setJsonModelDescriptor(newConfigRootDescriptor());
        waitForParser(tree);
        assertEquals(docProp.getEditStatus(), EditStatus.OKAY,
                "The field annotation must bind OKAY before the deletion: " + docProp.getEditMessage());

        assertTrue(tree.removeNode(commentsProp), "The comments node must be removable");
        waitForParser(tree);

        assertSame(docProp.getParent(), commentsProp,
                "The annotation stays with its field - the detached subtree keeps the structural anchor");
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) == docProp) {
                fail("The annotation must not be rescued to the owning object");
            }
        }

        final EditNodeProperty newComments = (EditNodeProperty) tree.addNewChild(root, "comments",
                root.getChildCount(), true);
        waitForParser(tree);

        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) == docProp) {
                fail("A new field must not steal the detached annotation - anchoring is explicit");
            }
        }

        tree.addChild(newComments, docProp);
        waitForParser(tree);

        assertEquals(docProp.getName(), "@doc:comments",
                "Anchored under the new field the annotation is composite again");
        assertSame(docProp.getParent(), newComments,
                "The annotation must be a child of the new field node");
        assertEquals(docProp.getEditStatus(), EditStatus.OKAY,
                "The structurally re-bound annotation must be OKAY again: " + docProp.getEditMessage());
    }

    /**
     * The * miniregex for declarations: a pure wildcard declaration (doc:*) covers every field of the type and the
     * object level, a prefix pattern covers only matching fields, an explicit field declaration wins, and undeclared
     * names stay tolerated warnings.
     */
    @Test
    public void testWildcardDeclarationCoversFields() throws Exception {
        final JsonConfigDefinition definition = new JsonConfigDefinition();
        definition.getRootClass().addAnnotation("hint:comm*");
        definition.getRootClass().getField("comments").addAnnotation("doc");

        final EditNodeObject root = new EditNodeObject("seedConfig");
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");

        final EditNodeProperty comments = new EditNodeProperty("comments", JsonNodeType.ARRAY);
        root.addChild(comments, new EditTimes());
        final EditNodeAnnotation docOnComments = new EditNodeAnnotation("doc");
        comments.addChild(docOnComments, new EditTimes());
        final EditNodeAnnotation hintOnComments = new EditNodeAnnotation("hint");
        comments.addChild(hintOnComments, new EditTimes());

        final EditNodeProperty profiles = new EditNodeProperty("profiles", JsonNodeType.ARRAY);
        root.addChild(profiles, new EditTimes());
        final EditNodeAnnotation docOnProfiles = new EditNodeAnnotation("doc");
        profiles.addChild(docOnProfiles, new EditTimes());
        final EditNodeAnnotation hintOnProfiles = new EditNodeAnnotation("hint");
        profiles.addChild(hintOnProfiles, new EditTimes());

        final EditNodeAnnotation docType = new EditNodeAnnotation("doc");
        root.addChild(docType, new EditTimes());
        final EditNodeAnnotation dokType = new EditNodeAnnotation("dok");
        root.addChild(dokType, new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        tree.setJsonModelDescriptor(definition.getDescriptor());
        waitForParser(tree);

        assertEquals(docOnComments.getEditStatus(), EditStatus.OKAY,
                "An explicit field doc declaration binds OKAY: " + docOnComments.getEditMessage());
        assertEquals(docOnProfiles.getEditStatus(), EditStatus.OKAY,
                "The doc:* wildcard covers fields without an explicit declaration: " + docOnProfiles.getEditMessage());
        assertEquals(hintOnComments.getEditStatus(), EditStatus.OKAY,
                "A prefix pattern covers matching fields: " + hintOnComments.getEditMessage());
        assertEquals(hintOnProfiles.getEditStatus(), EditStatus.WARNING,
                "A prefix pattern leaves other fields undeclared: " + hintOnProfiles.getEditMessage());
        assertEquals(docType.getEditStatus(), EditStatus.OKAY,
                "A pure wildcard covers the object level too: " + docType.getEditMessage());
        assertEquals(dokType.getEditStatus(), EditStatus.WARNING,
                "Undeclared names stay tolerated warnings: " + dokType.getEditMessage());
    }

    // ========== Helpers ==========
    /**
     * Builds a fresh Config definition with the annotations declared for the binding matrix: {@code hint} on the root
     * type and {@code doc} on the comments field. A fresh instance keeps the test independent of the cached descriptor
     * of the shared singleton.
     *
     * @return the model descriptor of the fresh definition
     */
    private static de.jare.jsoncasted.model.descriptor.JsonModelDescriptor newConfigRootDescriptor() {
        final JsonConfigDefinition definition = new JsonConfigDefinition();
        definition.getRootClass().addAnnotation("hint");
        definition.getRootClass().getField("comments").addAnnotation("doc");
        return definition.getDescriptor();
    }

    /**
     * Waits until the on-the-fly parser has processed the queue and settled.
     *
     * @param tree the tree to wait for
     * @throws InterruptedException if the sleep is interrupted
     */
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
