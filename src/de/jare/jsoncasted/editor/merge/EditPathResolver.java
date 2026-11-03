/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.merge;

import de.jare.jsoncasted.diff.DifferableNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import java.util.List;

/**
 * Resolves a diff match key ({@code $.a.b[2].#0}) against an edit tree root by walking the diff projection
 * (annotations are not part of the indices). Shared by the merge application and the diff view: every consumer that
 * needs the node behind a reported {@link de.jare.jsoncasted.diff.DeltaOp} key resolves through here.
 *
 * @author Janusch Rentenatus
 */
public final class EditPathResolver {

    private EditPathResolver() {
        // utility class, no instances
    }

    /**
     * Resolves a diff match key ({@code $.a.b[2].c}) against a tree root.
     *
     * @param root the tree root, never {@code null}.
     * @param path the match key, never {@code null}.
     * @return the node at the path.
     * @throws PathUnresolvableException when the path does not exist in this tree.
     */
    public static EditNodeAbstract resolve(EditNodeAbstract root, String path) {
        EditNodeAbstract node = root;
        int i = 1; // skip the leading $
        while (i < path.length()) {
            final char c = path.charAt(i);
            if (c == '.') {
                int j = i + 1;
                while (j < path.length() && path.charAt(j) != '.' && path.charAt(j) != '[') {
                    j++;
                }
                node = childByKey(node, path.substring(i + 1, j));
                i = j;
            } else if (c == '[') {
                final int j = path.indexOf(']', i);
                node = childByIndex(node, Integer.parseInt(path.substring(i + 1, j)));
                i = j + 1;
            } else {
                throw new PathUnresolvableException(path);
            }
            if (node == null) {
                throw new PathUnresolvableException(path);
            }
        }
        return node;
    }

    private static EditNodeAbstract childByKey(EditNodeAbstract parent, String key) {
        if (key.startsWith("#")) {
            // positional data child of a property (object data), see the diff projection
            return childByIndex(parent, Integer.parseInt(key.substring(1)));
        }
        for (DifferableNode child : parent.children()) {
            if (key.equals(child.getMemberKey())) {
                return (EditNodeAbstract) child;
            }
        }
        return null;
    }

    private static EditNodeAbstract childByIndex(EditNodeAbstract parent, int index) {
        final List<DifferableNode> children = parent.children();
        return index >= 0 && index < children.size() ? (EditNodeAbstract) children.get(index) : null;
    }

    /**
     * A diff path that does not exist in the target tree — the structure drifted further than the analysis assumed.
     */
    public static final class PathUnresolvableException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private PathUnresolvableException(String path) {
            super(path);
        }
    }
}
