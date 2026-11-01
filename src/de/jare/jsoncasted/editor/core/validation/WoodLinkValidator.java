/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditLinkingSet;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditTree;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validator for the wood link semantics.
 *
 * <p>Validates:</p>
 * <ul>
 *   <li>Every reference stub in the tree resolves its link target - neither in the tree nor in the linking set
 *   means a dangling link (ERROR)</li>
 *   <li>Each wood object id is carried by at most one tree node - a copied original keeps its identity and produces
 *   a duplicate (WARNING)</li>
 *   <li>An object held by the linking set without any tree position and without references is a cleanup candidate
 *   (INFO)</li>
 * </ul>
 *
 * <p>An object that hangs in the air while references exist is valid - exactly that is the holding space
 * semantics of the delete. The export writes such objects into the _woodDefinitions section.</p>
 *
 * @author Janusch Rentenatus
 */
public class WoodLinkValidator implements EditTreeValidator {

    @Override
    public void validate(EditTree tree, ValidationContext context) {
        final EditLinkingSet linkingSet = tree.getLinkingSet();
        final Set<EditNodeAbstract> visited = new HashSet<>();
        final Map<String, List<EditNodeObject>> identitiesInTree = new LinkedHashMap<>();

        walkTree(tree.getRoot(), linkingSet, context, identitiesInTree, visited);

        if (linkingSet == null) {
            return;
        }

        // Duplicate identities in the tree: a deep copy of an original keeps its wood object id.
        for (Map.Entry<String, List<EditNodeObject>> entry : identitiesInTree.entrySet()) {
            final List<EditNodeObject> carriers = entry.getValue();
            if (carriers.size() > 1) {
                for (EditNodeObject carrier : carriers) {
                    context.addWarning("woodlink.duplicateId",
                            "Wood object id '" + entry.getKey() + "' is carried by "
                            + carriers.size() + " nodes in the tree", carrier);
                }
            }
        }

        // Held objects without references are cleanup candidates: the export drops them anyway.
        for (Map.Entry<String, EditNodeAbstract> entry : linkingSet.getObjectIdMap().entrySet()) {
            final EditNodeAbstract held = entry.getValue();
            if (held == null || visited.contains(held)) {
                continue;
            }
            if (!linkingSet.hasReferencesTo(entry.getKey())) {
                context.addInfo("woodlink.unreferenced",
                        "Object '" + held.getName() + "' is held without a tree position and without references",
                        held);
            }
        }
    }

    /**
     * Walks the tree, checking reference stubs and collecting identities.
     *
     * @param node the current node
     * @param linkingSet the linking set, may be null
     * @param context the validation context to report to
     * @param identitiesInTree the collected identities (raw id to carriers)
     * @param visited the visited set for cycle protection
     */
    private void walkTree(EditNodeAbstract node, EditLinkingSet linkingSet, ValidationContext context,
            Map<String, List<EditNodeObject>> identitiesInTree, Set<EditNodeAbstract> visited) {
        if (node == null || !visited.add(node)) {
            return;
        }
        if (node instanceof EditNodeObject obj) {
            if (obj.isLinkReference() && linkingSet != null
                    && linkingSet.findLinkTarget(obj.getLinkTarget()) == null) {
                context.addError("woodlink.dangling",
                        "Link target '" + obj.getLinkTarget() + "' does not resolve in this tree", obj);
            }
            if (obj.getObjektId() != null && !obj.getObjektId().isEmpty()) {
                identitiesInTree.computeIfAbsent(obj.getObjektId(), key -> new ArrayList<>()).add(obj);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeAbstract child) {
                walkTree(child, linkingSet, context, identitiesInTree, visited);
            }
        }
    }
}
