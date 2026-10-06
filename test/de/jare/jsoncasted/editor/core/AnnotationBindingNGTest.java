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
 * annotations bind OKAY, undeclared ones are tolerated free annotations with a WARNING, composite keys
 * ({@code @doc:comments}) move under their field node through the parse cascade, and annotations survive the
 * deletion of their field - they stay anchored at the owning object and re-bind when the field appears again.
 *
 * @author Janusch Rentenatus
 */
public class AnnotationBindingNGTest {

    /**
     * Declared object annotations bind OKAY, undeclared ones are tolerated free annotations with a WARNING,
     * and the rows of an annotation adopt the implicit String element type.
     */
    @Test
    public void testDeclaredAndFreeObjectAnnotations() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        final EditNodeProperty hintProp = new EditNodeProperty("@hint", JsonNodeType.ARRAY);
        root.addChild(hintProp, new EditTimes());
        final EditNodeObject hintRow = new EditNodeObject("declared object annotation");
        hintProp.addChild(hintRow, new EditTimes());
        final EditNodeProperty dokProp = new EditNodeProperty("@dok", JsonNodeType.ARRAY);
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
     * A composite annotation key ({@code @doc:comments}) anchors at its target field: the parse cascade moves the
     * annotation under the field node and binds it against the field's declared annotations.
     */
    @Test
    public void testCompositeAnnotationMovesUnderFieldNode() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        final EditNodeProperty orphan = new EditNodeProperty("@doc:comments", JsonNodeType.ARRAY);
        root.addChild(orphan, new EditTimes());
        final EditNodeObject docRow = new EditNodeObject("composite annotation row");
        orphan.addChild(docRow, new EditTimes());
        final EditNodeProperty commentsProp = new EditNodeProperty("comments", JsonNodeType.ARRAY);
        root.addChild(commentsProp, new EditTimes());
        final EditNodeObject commentRow = new EditNodeObject("Das ist ein Json Config Datei.");
        commentsProp.addChild(commentRow, new EditTimes());

        final EditTree tree = new EditTree(root, new EditTimes());
        tree.setJsonModelDescriptor(newConfigRootDescriptor());
        waitForParser(tree);

        assertEquals(orphan.getName(), "@doc",
                "The composite anchor must be stripped after the move under the field node");
        assertSame(orphan.getParent(), commentsProp,
                "The annotation must be a child of its target field node (Kind von comments)");
        assertEquals(orphan.getEditStatus(), EditStatus.OKAY,
                "A declared field annotation must bind OKAY: " + orphan.getEditMessage());
        assertEquals(commentRow.getEditStatus(), EditStatus.OKAY,
                "The field's own rows must stay untouched: " + commentRow.getEditMessage());
    }

    /**
     * Deleting the field never removes a persistent annotation: it falls back to the owning object with its
     * composite anchor and a WARNING, and the parse cascade re-binds it as soon as a field of that name appears
     * again.
     */
    @Test
    public void testFieldDeletionRescuesAndReappearanceRebinds() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        final EditNodeProperty commentsProp = new EditNodeProperty("comments", JsonNodeType.ARRAY);
        root.addChild(commentsProp, new EditTimes());
        final EditNodeProperty docProp = new EditNodeProperty("@doc", JsonNodeType.ARRAY);
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

        assertEquals(docProp.getName(), "@doc:comments",
                "The rescued annotation must carry its composite anchor again");
        assertSame(docProp.getParent(), root,
                "The rescued annotation must stay anchored at the owning object, never silently removed");
        assertEquals(docProp.getEditStatus(), EditStatus.WARNING,
                "The rescued annotation must fall back to a WARNING: " + docProp.getEditMessage());

        final EditNodeProperty newComments = (EditNodeProperty) tree.addNewChild(root, "comments",
                root.getChildCount(), true);
        waitForParser(tree);

        assertEquals(docProp.getName(), "@doc", "The re-bound annotation must use its plain annotation name");
        assertSame(docProp.getParent(), newComments,
                "The re-appeared field must re-bind the waiting annotation under it");
        assertEquals(docProp.getEditStatus(), EditStatus.OKAY,
                "The re-bound annotation must be OKAY again: " + docProp.getEditMessage());
    }


    /**
     * The * miniregex for composite targets: a pure wildcard declaration (doc:*) covers every field of the type and
     * the object level, a prefix pattern covers only matching fields, an explicit field declaration wins, and
     * undeclared names stay tolerated warnings.
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
        final EditNodeProperty docOnComments = new EditNodeProperty("@doc", JsonNodeType.ARRAY);
        comments.addChild(docOnComments, new EditTimes());
        final EditNodeProperty hintOnComments = new EditNodeProperty("@hint", JsonNodeType.ARRAY);
        comments.addChild(hintOnComments, new EditTimes());

        final EditNodeProperty profiles = new EditNodeProperty("profiles", JsonNodeType.ARRAY);
        root.addChild(profiles, new EditTimes());
        final EditNodeProperty docOnProfiles = new EditNodeProperty("@doc", JsonNodeType.ARRAY);
        profiles.addChild(docOnProfiles, new EditTimes());
        final EditNodeProperty hintOnProfiles = new EditNodeProperty("@hint", JsonNodeType.ARRAY);
        profiles.addChild(hintOnProfiles, new EditTimes());

        final EditNodeProperty docType = new EditNodeProperty("@doc", JsonNodeType.ARRAY);
        root.addChild(docType, new EditTimes());
        final EditNodeProperty dokType = new EditNodeProperty("@dok", JsonNodeType.ARRAY);
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
     * Builds a fresh Config definition with the annotations declared for the binding matrix: {@code hint} on the
     * root type and {@code doc} on the comments field. A fresh instance keeps the test independent of the cached
     * descriptor of the shared singleton.
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
