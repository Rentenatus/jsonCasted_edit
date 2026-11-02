/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.merge;

import de.jare.jsoncasted.diff.DeltaOp;
import de.jare.jsoncasted.diff.DeltaProperty;
import de.jare.jsoncasted.diff.DeltaType;
import de.jare.jsoncasted.diff.DifferableNode;
import de.jare.jsoncasted.diff.MergePlan;
import de.jare.jsoncasted.diff.MergePolicy;
import de.jare.jsoncasted.diff.ThreeWayDiffer;
import de.jare.jsoncasted.editor.command.AddNodeCommand;
import de.jare.jsoncasted.editor.command.CompositeEditCommand;
import de.jare.jsoncasted.editor.command.DeleteNodeCommand;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.command.RenameNodeCommand;
import de.jare.jsoncasted.editor.command.SetAttributeCommand;
import de.jare.jsoncasted.editor.command.SetValueCommand;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.editor.events.HistoryManager;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.lang.JsonResource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The 3-way merge of an edited tree against an externally changed file (use case U1 of doc/TREE_DIFF.md in
 * jsonCasted). Base is the retained import state (gap G1, {@link EditTree#getBaseSnapshot()}); theirs is a fresh
 * parse handed in as a {@link JsonResource}. The diff runs homogeneously on EditNode level: base and theirs are
 * converted forward through the same import path as ours once was — cheaper than serializing ours backward.
 *
 * <p>
 * Application is the command layer: every applicable theirs delta becomes one command, all of them wrapped in one
 * {@link CompositeEditCommand} — one history entry, atomic undo (gap G2). {@link MergePolicy#SAFE_AUTO} applies the
 * non-overlapping deltas and leaves the conflicts to the decision UI; {@link MergePolicy#ASK} and
 * {@link MergePolicy#NONE} apply nothing. V1 scope: MOVE/REORDER of theirs never reach the application (they are
 * conflicts), and a cast or link target that theirs removed to {@code null} is recorded as a problem instead of
 * applied.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public final class EditTreeMerger {

    private static final String ATTR_CAST_NAME = "|cast name";
    private static final String ATTR_LINK_TARGET = "|linkTarget";

    private EditTreeMerger() {
        // utility class, no instances
    }

    /**
     * Analyzes the three sides without touching anything: base (retained import state) against ours and theirs.
     *
     * @param oursTree the edited tree, must have an import state.
     * @param theirsResource the fresh parse of the externally changed file, never {@code null}.
     * @return the merge plan, never {@code null}.
     */
    public static MergePlan analyze(EditTree oursTree, JsonResource theirsResource) {
        return analyze(oursTree, convert(oursTree, theirsResource).getRoot());
    }

    /**
     * Analyzes the three sides against a theirs that is already an edit tree root — for example another open editor
     * playing the external version. The base still comes from ours' import state.
     *
     * @param oursTree the edited tree, must have an import state.
     * @param theirsRoot the root of the external version, never {@code null}.
     * @return the merge plan, never {@code null}.
     */
    public static MergePlan analyze(EditTree oursTree, EditNodeAbstract theirsRoot) {
        final EditTree baseEdit = convert(oursTree, requiredBase(oursTree));
        return ThreeWayDiffer.analyze(baseEdit.getRoot(), oursTree.getRoot(), theirsRoot);
    }

    /**
     * Merges according to the policy. {@code SAFE_AUTO} applies the non-overlapping theirs deltas as one
     * transaction through the given history (one undo step); {@code ASK} and {@code NONE} only report.
     *
     * @param oursTree the edited tree, must have an import state.
     * @param theirsResource the fresh parse of the externally changed file, never {@code null}.
     * @param policy the merge policy.
     * @param history the history to book the merge transaction on, or {@code null} to execute without history.
     * @return the outcome, never {@code null}.
     */
    public static MergeOutcome merge(EditTree oursTree, JsonResource theirsResource, MergePolicy policy,
            HistoryManager history) {
        final MergePlan plan = analyze(oursTree, theirsResource);
        if (policy != MergePolicy.SAFE_AUTO || plan.getApplyOps().isEmpty()) {
            return new MergeOutcome(plan, false, List.of());
        }
        final EditTree theirsEdit = convert(oursTree, theirsResource);
        final EditNodeAbstract oursRoot = oursTree.getRoot();
        final EditNodeAbstract theirsRoot = theirsEdit.getRoot();

        final List<EditCommand> commands = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        for (DeltaOp op : inApplicationOrder(plan.getApplyOps())) {
            try {
                final EditCommand command = buildCommand(oursRoot, theirsRoot, op);
                if (command != null) {
                    commands.add(command);
                } else {
                    problems.add("not applicable: " + op);
                }
            } catch (PathUnresolvableException e) {
                problems.add("unresolvable " + e.getMessage() + ": " + op);
            }
        }
        if (commands.isEmpty()) {
            return new MergeOutcome(plan, false, problems);
        }
        final CompositeEditCommand transaction = new CompositeEditCommand(
                "Merge: " + commands.size() + " external change(s)", commands);
        if (history != null) {
            history.execute(transaction);
        } else {
            transaction.execute(oursTree, false);
        }
        return new MergeOutcome(plan, true, problems);
    }

    private static JsonResource requiredBase(EditTree oursTree) {
        final JsonResource base = oursTree.getBaseSnapshot();
        if (base == null) {
            throw new IllegalArgumentException(
                    "the tree has no import state to merge against (built without import)");
        }
        return base;
    }

    /**
     * Re-imports a resource through the normal path, transiently, with the same root name as ours — the root name is
     * part of the Merkle hash, so base and theirs must adopt ours' name to stay comparable.
     */
    private static EditTree convert(EditTree oursTree, JsonResource resource) {
        try {
            return JsonTreeConverter.convertRessourceToEditTree(resource, oursTree.getRoot().getName());
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("re-import failed: " + e.getMessage(), e);
        }
    }

    /**
     * Removes first, then modifies, then adds: the base-side indices of the resolve step stay valid while the
     * commands are built, and adds land in the parent after the removals executed.
     */
    private static List<DeltaOp> inApplicationOrder(List<DeltaOp> ops) {
        final List<DeltaOp> result = new ArrayList<>(ops.size());
        for (DeltaType type : new DeltaType[]{DeltaType.REMOVE, DeltaType.MODIFY, DeltaType.ADD}) {
            for (DeltaOp op : ops) {
                if (op.getType() == type) {
                    result.add(op);
                }
            }
        }
        return result;
    }

    private static EditCommand buildCommand(EditNodeAbstract oursRoot, EditNodeAbstract theirsRoot, DeltaOp op) {
        switch (op.getType()) {
            case REMOVE:
                return new DeleteNodeCommand(resolve(oursRoot, op.getKey()));
            case MODIFY:
                return modifyCommand(oursRoot, theirsRoot, op);
            case ADD:
                return addCommand(oursRoot, theirsRoot, op);
            default:
                // MOVE/REORDER are conflicts, they never reach the application
                throw new IllegalStateException("unsupported application: " + op.getType());
        }
    }

    private static EditCommand modifyCommand(EditNodeAbstract oursRoot, EditNodeAbstract theirsRoot, DeltaOp op) {
        final EditNodeAbstract oursNode = resolve(oursRoot, op.getKey());
        final EditNodeAbstract theirsNode = resolve(theirsRoot, op.getKey());
        if (op.getProperty() == DeltaProperty.VALUE) {
            return new SetValueCommand(oursNode, theirsNode.getValue());
        }
        if (op.getProperty() == DeltaProperty.NAME) {
            return new RenameNodeCommand(oursNode, theirsNode.getName());
        }
        if (op.getProperty() == DeltaProperty.CLASS) {
            return theirsNode.getCastName() == null ? null : attributeCommand(oursNode, ATTR_CAST_NAME,
                    theirsNode.getCastName());
        }
        if (op.getProperty() == DeltaProperty.LINK_TARGET) {
            return theirsNode.getLinkTarget() == null ? null : attributeCommand(oursNode, ATTR_LINK_TARGET,
                    theirsNode.getLinkTarget());
        }
        return null; // OBJECT_ID is never a MODIFY (REMOVE+ADD carries it)
    }

    private static EditCommand attributeCommand(EditNodeAbstract oursNode, String key, Object value) {
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put(key, value);
        return new SetAttributeCommand(oursNode, attributes);
    }

    private static EditCommand addCommand(EditNodeAbstract oursRoot, EditNodeAbstract theirsRoot, DeltaOp op) {
        final EditNodeAbstract parent = resolve(oursRoot, op.getParentKey());
        final EditNodeAbstract source = resolve(theirsRoot, op.getKey());
        final EditNodeAbstract copy = source.deepCopy(true); // fresh edit ids, detached from theirs
        final int index = op.getFieldOrIndex() != null && op.getFieldOrIndex().matches("\\d+")
                ? Integer.parseInt(op.getFieldOrIndex()) : -1;
        return new AddNodeCommand(parent, copy, index);
    }

    /**
     * Resolves a diff match key ($.a.b[2].c) against a tree root by walking the diff projection (annotations are not
     * part of the indices).
     *
     * @param root the tree root, never {@code null}.
     * @param path the match key, never {@code null}.
     * @return the node at the path.
     * @throws PathUnresolvableException when the path does not exist in this tree.
     */
    private static EditNodeAbstract resolve(EditNodeAbstract root, String path) {
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
    private static final class PathUnresolvableException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private PathUnresolvableException(String path) {
            super(path);
        }
    }
}
