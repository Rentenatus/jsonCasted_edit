/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;

/**
 * Hard parse dry run for paste (parse mode concept, section 6.4): the clipboard content is cloned and kept in the
 * air - the clone carries the parent and tree references of the target anchor but is never docked - and is parsed
 * synchronously against that anchor. The parser itself is the oracle: no parallel rule logic, the real binding
 * paths decide whether the clipboard fits. Everything OKAY means the regular paste command can dock (its
 * synchronous parse re-confirms deterministically); anything else leaves the tree untouched - the caller shows
 * the break dialog and lets the user decide between soft parse and cancel.
 *
 * @author Janusch Rentenatus
 */
public final class HardPasteProbe {

    /**
     * The probe outcome: fits with no problem, or the first problem of the unfitting candidate.
     */
    public static final class Result {

        private final boolean fits;
        private final String problem;

        private Result(boolean fits, String problem) {
            this.fits = fits;
            this.problem = problem;
        }

        public boolean fits() {
            return fits;
        }

        /**
         * Returns the first problem of the probe.
         *
         * @return the problem message, or null when the content fits
         */
        public String getProblem() {
            return problem;
        }
    }

    private HardPasteProbe() {
    }

    /**
     * Probes whether the clipboard candidates fit the target anchor: each candidate is cloned into the air, wired
     * to the anchor, parsed synchronously and unwired again - the tree is never touched.
     *
     * @param tree the edit tree of the target (provides the parser and the model context)
     * @param target the target anchor (owning object, field property or collection property)
     * @param candidates the clipboard content
     * @return the probe result
     */
    public static Result probe(EditTree tree, EditNodeAbstract target, EditNodeAbstract[] candidates) {
        if (tree == null || target == null || candidates == null || candidates.length == 0) {
            return new Result(false, "Nothing to paste.");
        }
        for (EditNodeAbstract candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            final String structural = structuralProblem(target, candidate);
            if (structural != null) {
                return new Result(false, structural);
            }
            final EditNodeAbstract clone = candidate.deepCopy(true);
            wire(clone, target, tree);
            tree.parseDetached(clone);
            final String valueMismatch = valueCompatibilityProblem(tree, target, clone);
            final EditNode offender = firstNotOkay(clone);
            unwire(clone);
            if (valueMismatch != null) {
                return new Result(false, valueMismatch);
            }
            if (offender != null) {
                return new Result(false, problemOf(offender));
            }
        }
        return new Result(true, null);
    }

    /**
     * Cheap structural pre-checks the parser cannot see: the node kind must fit, and the value of a 0:1 property
     * must not run double.
     *
     * @param target the target anchor
     * @param candidate the clipboard candidate
     * @return the problem, or null when structurally fine
     */
    private static String structuralProblem(EditNodeAbstract target, EditNodeAbstract candidate) {
        if (!candidate.canBeChildOf(target)) {
            return "The node kind does not fit the target.";
        }
        if (target instanceof EditNodeProperty property
                && !(target instanceof EditNodeAnnotation)
                && candidate instanceof EditNodeObject
                && isOneToOne(property)
                && hasObjectChild(property)) {
            return "The 0:1 value is already present.";
        }
        return null;
    }

    private static boolean isOneToOne(EditNodeProperty property) {
        final JsonFieldDescriptor field = property.getJsonField();
        return field != null
                && (field.getCollectionType() == null || field.getCollectionType() == de.jare.jsoncasted.model.JsonCollectionType.NONE);
    }

    private static boolean hasObjectChild(EditNodeProperty property) {
        for (int i = 0; i < property.getChildCount(); i++) {
            if (property.getChildAt(i) instanceof EditNodeObject) {
                return true;
            }
        }
        return false;
    }

    /**
     * Closes the one seam the in-air parse cannot see: the element type propagation of the anchor runs over the
     * anchor's real children, so a detached value object never receives it. The check mirrors the confirmed
     * decision of the propagation - the pasted value must be the expected type, a contained subtype, or castless
     * (then the real paste adopts the expected type like any propagation).
     *
     * @param tree the edit tree of the target
     * @param anchor the target anchor
     * @param clone the parsed clone
     * @return the problem, or null when the value fits
     */
    private static String valueCompatibilityProblem(EditTree tree, EditNodeAbstract anchor, EditNodeAbstract clone) {
        if (!(anchor instanceof EditNodeProperty property) || anchor instanceof EditNodeAnnotation
                || !(clone instanceof EditNodeObject value)) {
            return null;
        }
        final JsonFieldDescriptor field = property.getJsonField();
        if (field == null || tree.getJsonModelDescriptor() == null) {
            return null; // unresolved anchor - the parse above reported already
        }
        final JsonTypeDescriptor expected = tree.getJsonModelDescriptor().getType(field.getTypeName());
        if (expected == null || expected.isPrimitive()) {
            return null; // scalar targets take no object children anyway
        }
        final String castName = value.getCastName();
        if (castName == null || castName.isBlank()) {
            return null; // castless: the real paste adopts the expected type
        }
        final JsonTypeDescriptor actual = tree.getJsonModelDescriptor().getType(castName);
        if (actual == null) {
            return "The type '" + castName + "' of the pasted value is unknown in the model.";
        }
        if (actual != expected && !expected.contains(actual) && !actual.containsSuper(expected)) {
            return "The type '" + castName + "' of the pasted value does not match '"
                    + expected.getTypeName() + "'.";
        }
        return null;
    }

    /**
     * Wires the detached clone to the anchor: parent reference for the binding context and tree reference for the
     * propagation, recursively for the whole subtree. The clone stays outside the anchor's children.
     */
    private static void wire(EditNodeAbstract node, EditNodeAbstract anchor, EditTree tree) {
        node.setParent(anchor);
        node.setEditTree(tree);
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeAbstract child) {
                wire(child, node, tree);
            }
        }
    }

    /**
     * Unwires the detached clone completely, so no dangling reference survives the probe.
     */
    private static void unwire(EditNodeAbstract node) {
        node.setParent(null);
        node.setEditTree(null);
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeAbstract child) {
                unwire(child);
            }
        }
    }

    /**
     * Returns the first node of the detached subtree that did not bind OKAY.
     */
    private static EditNode firstNotOkay(EditNodeAbstract node) {
        if (node.getEditStatus() != EditStatus.OKAY) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeAbstract child) {
                final EditNode offender = firstNotOkay(child);
                if (offender != null) {
                    return offender;
                }
            }
        }
        return null;
    }

    private static String problemOf(EditNode offender) {
        final String message = offender.getEditMessage();
        return message == null || message.isBlank()
                ? "The node '" + offender.getName() + "' does not bind at the target."
                : message;
    }
}
