/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.events.HistoryManager;

/**
 * Main facade for the headless JSON tree editor core.
 *
 * <p>
 * This class combines the editable tree model, command history, and event
 * dispatching behind a single API. It is the primary entry point for loading,
 * modifying, exporting, and inspecting editor state.</p>
 *
 * @author Jansuch Rentenatus
 *
 */
public class TreeEditorModel extends TreeEditorAbstract {

    private final EditTree tree;

    /**
     * Creates a new editor with a default object root named {@code root}.
     */
    public TreeEditorModel() {
        this("root");
    }

    /**
     * Creates a new editor for the given root node.
     *
     * @param rootText
     * @throws IllegalArgumentException if {@code root} is {@code null}
     */
    public TreeEditorModel(String rootText) {
        this(new EditTree(rootText));
    }

    /**
     * Creates a new editor for the given root node.
     *
     * @param tree
     * @throws IllegalArgumentException if {@code root} is {@code null}
     */
    public TreeEditorModel(EditTree tree) {
        super(new HistoryManager(tree));
        this.tree = tree;
    }

    // -------------------------------------------------------------------------
    // Core accessors
    // -------------------------------------------------------------------------
    /**
     * Returns the editable tree managed by this editor.
     *
     * @return the tree instance
     */
    public EditTree getTree() {
        return tree;
    }

    // -------------------------------------------------------------------------
    // Debug / inspection helpers
    // -------------------------------------------------------------------------
    /**
     * Returns a compact debug representation of the editor state.
     *
     * @return a one-line debug string
     */
    @Override
    public String toString() {
        return toDebugString();
    }

    /**
     * Returns a compact debug representation of the editor, including tree,
     * history, listener count, and validation state.
     *
     * @return a one-line debug string
     */
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TreeEditor{")
                .append("tree=").append(tree != null ? tree.toString() : "null")
                .append(", history=").append(historyManager != null ? historyManager.toString() : "null");
        sb.append('}');
        return sb.toString();
    }

    /**
     * Returns a formatted representation of the complete tree.
     *
     * @return the formatted tree output
     */
    public String toTreeString() {
        if (tree == null || tree.getRoot() == null) {
            return "<empty tree>";
        }
        return toTreeString(tree.getRoot());
    }

    /**
     * Returns a formatted representation of the subtree starting at the node
     * with the given ID.
     *
     * @param startNodeId the start node ID
     * @return the formatted subtree output, or a not-found marker
     */
    public String toTreeString(long startNodeId) {
        if (tree == null) {
            return "<empty tree>";
        }

        EditNode startNode = tree.findNodeById(startNodeId);
        if (startNode == null) {
            return "<node not found: " + startNodeId + ">";
        }

        return toTreeString(startNode);
    }

    /**
     * Returns a formatted representation of the subtree starting at the given
     * node.
     *
     * @param startNode the subtree root
     * @return the formatted subtree output
     */
    public String toTreeString(EditNode startNode) {
        if (startNode == null) {
            return "<null node>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Subtree from ").append(formatNodeHeader(startNode)).append('\n');
        appendNode(sb, startNode, 0, getIndexInParent(startNode));
        return sb.toString();
    }

    /**
     * Appends a formatted subtree representation to the given string builder.
     *
     * @param sb the target builder
     * @param node the current node
     * @param depth the current depth
     * @param indexInParent the index within the parent, or {@code -1} for root
     */
    private void appendNode(StringBuilder sb, EditNode node, int depth, int indexInParent) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }

        sb.append(indexInParent >= 0 ? "[" + indexInParent + "] " : "")
                .append(formatNodeHeader(node))
                .append('\n');

        for (int i = 0; i < node.getChildCount(); i++) {
            appendNode(sb, node.getChildAt(i), depth + 1, i);
        }
    }

}
