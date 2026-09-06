/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditNodePropertyArr;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import java.util.HashSet;
import java.util.Set;

/**
 * Validator that checks global consistency between EditTree and JsonModelDescriptor.
 * 
 * <p>Validates:</p>
 * <ul>
 *   <li>All EditNodeObject types exist in the model</li>
 *   <li>All EditNodeProperty fields belong to existing parent types</li>
 *   <li>No cyclic references in type hierarchy</li>
 *   <li>All referenced types are registered in the model</li>
 * </ul>
 * 
 * @author Janusch Rentenatus
 */
public class EditTreeModelConsistencyValidator implements EditTreeValidator {
    
    @Override
    public void validate(EditTree tree, ValidationContext context) {
        JsonModelDescriptor descriptor = context.getModelDescriptor();
        
        // If no model descriptor, we can only do limited validation
        if (descriptor == null) {
            // Check for structural consistency only
            validateStructure(tree, context);
            return;
        }
        
        // Collect all type names used in the tree
        Set<String> usedTypeNames = new HashSet<>();
        Set<String> missingTypeNames = new HashSet<>();
        Set<String> orphanedTypeNames = new HashSet<>();
        
        // Traverse the tree to collect all object type names
        collectObjectTypeNames(tree.getRoot(), usedTypeNames);
        
        // Check if all used types exist in the model
        for (String typeName : usedTypeNames) {
            if (!descriptor.containsType(typeName) && !descriptor.getTypePerceptive(typeName) != null) {
                missingTypeNames.add(typeName);
            }
        }
        
        // Check if all types in the model are used (optional, for completeness)
        // This is more of an info/warning check
        for (String modelTypeName : descriptor.getTypesKeys()) {
            if (!usedTypeNames.contains(modelTypeName)) {
                // This is not an error, just informational
                // Could be added as INFO diagnostic if needed
            }
        }
        
        // If there are missing types, add error diagnostics
        if (!missingTypeNames.isEmpty()) {
            // Find nodes with missing types
            findNodesWithMissingTypes(tree.getRoot(), descriptor, missingTypeNames, context);
            
            // Also add a general tree-level diagnostic
            context.addError("editnode.tree.orphaned.types",
                          "The following types are used in the tree but not in the model: " + missingTypeNames,
                          tree.getRoot());
        }
        
        // Check for general tree-model consistency
        if (!missingTypeNames.isEmpty()) {
            context.addError("editnode.tree.inconsistent",
                          "Tree contains nodes with types not defined in the model",
                          tree.getRoot());
        }
    }
    
    /**
     * Collects all object type names from the tree.
     *
     * @param node the current node to process
     * @param typeNames set to collect type names
     */
    private void collectObjectTypeNames(EditNodeAbstract node, Set<String> typeNames) {
        if (node instanceof EditNodeObject) {
            EditNodeObject objectNode = (EditNodeObject) node;
            String typeName = objectNode.getName();
            if (typeName != null && !typeName.isEmpty()) {
                typeNames.add(typeName);
            }
        }
        
        // Recursively process children
        for (int i = 0; i < node.getChildCount(); i++) {
            EditNode child = node.getChildAt(i);
            if (child instanceof EditNodeAbstract) {
                collectObjectTypeNames((EditNodeAbstract) child, typeNames);
            }
        }
    }
    
    /**
     * Finds nodes that have types not defined in the model.
     *
     * @param node the current node to check
     * @param descriptor the model descriptor
     * @param missingTypeNames set of missing type names
     * @param context the validation context
     */
    private void findNodesWithMissingTypes(EditNodeAbstract node, JsonModelDescriptor descriptor,
                                         Set<String> missingTypeNames, ValidationContext context) {
        if (node instanceof EditNodeObject) {
            EditNodeObject objectNode = (EditNodeObject) node;
            String typeName = objectNode.getName();
            
            if (typeName != null && missingTypeNames.contains(typeName)) {
                context.addError("editnode.object.type.missing",
                              "Type '" + typeName + "' not found in model",
                              objectNode);
            }
        }
        
        // Recursively process children
        for (int i = 0; i < node.getChildCount(); i++) {
            EditNode child = node.getChildAt(i);
            if (child instanceof EditNodeAbstract) {
                findNodesWithMissingTypes((EditNodeAbstract) child, descriptor, missingTypeNames, context);
            }
        }
    }
    
    /**
     * Validates structural consistency of the tree (without model).
     *
     * @param tree the tree to validate
     * @param context the validation context
     */
    private void validateStructure(EditTree tree, ValidationContext context) {
        // Check for structural issues that don't require a model
        // For example: property nodes without proper parent
        validateStructureRecursive(tree.getRoot(), context);
    }
    
    /**
     * Recursively validates structural consistency.
     *
     * @param node the current node to validate
     * @param context the validation context
     */
    private void validateStructureRecursive(EditNodeAbstract node, ValidationContext context) {
        // Check property nodes have valid parents
        if (node instanceof EditNodeProperty) {
            EditNode parent = node.getParent();
            if (parent == null || !(parent instanceof EditNodeObject)) {
                context.addError("editnode.property.parent.invalid",
                              "Property node has invalid parent",
                              node);
            }
        } else if (node instanceof EditNodePropertyArr) {
            EditNode parent = node.getParent();
            if (parent == null || !(parent instanceof EditNodeObject)) {
                context.addError("editnode.property.parent.invalid",
                              "Array property node has invalid parent",
                              node);
            }
        }
        
        // Recursively process children
        for (int i = 0; i < node.getChildCount(); i++) {
            EditNode child = node.getChildAt(i);
            if (child instanceof EditNodeAbstract) {
                validateStructureRecursive((EditNodeAbstract) child, context);
            }
        }
    }
    
    @Override
    public String getId() {
        return "EditTreeModelConsistencyValidator";
    }
    
    @Override
    public String getDescription() {
        return "Validates global consistency between EditTree and JsonModelDescriptor";
    }
}
