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
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import java.util.HashSet;
import java.util.Set;

/**
 * Validator that checks type hierarchy and inheritance relationships.
 * 
 * <p>Validates:</p>
 * <ul>
 *   <li>EditNodeObject with extends attribute reference existing parent types</li>
 *   <li>Polymorphic nodes have valid type descriptors</li>
 *   <li>Interface implementations are correct</li>
 *   <li>No cyclic inheritance</li>
 * </ul>
 * 
 * @author Janusch Rentenatus
 */
public class EditNodeTypeHierarchyValidator implements EditTreeValidator {
    
    @Override
    public void validate(EditTree tree, ValidationContext context) {
        JsonModelDescriptor descriptor = context.getModelDescriptor();
        
        // If no model descriptor, we can't validate hierarchy
        if (descriptor == null) {
            return;
        }
        
        // Traverse the tree to validate all object nodes
        validateObjectHierarchy(tree.getRoot(), descriptor, context, new HashSet<>());
    }
    
    /**
     * Validates type hierarchy for object nodes.
     *
     * @param node the current node to validate
     * @param descriptor the model descriptor
     * @param context the validation context
     * @param visitedTypes set to track visited types for cycle detection
     */
    private void validateObjectHierarchy(EditNodeAbstract node, JsonModelDescriptor descriptor, 
                                         ValidationContext context, Set<String> visitedTypes) {
        if (node instanceof EditNodeObject) {
            EditNodeObject objectNode = (EditNodeObject) node;
            JsonTypeDescriptor typeDescriptor = objectNode.getJsonType();
            
            // If the object doesn't have a type descriptor yet, try to get it from the model
            if (typeDescriptor == null) {
                String typeName = objectNode.getName();
                if (typeName != null && !typeName.isEmpty()) {
                    typeDescriptor = descriptor.getType(typeName);
                    if (typeDescriptor == null) {
                        typeDescriptor = descriptor.getTypePerceptive(typeName);
                    }
                }
            }
            
            if (typeDescriptor != null) {
                // Check for cyclic inheritance
                if (hasCyclicInheritance(typeDescriptor, new HashSet<>())) {
                    context.addError("editnode.type.hierarchy.cyclic",
                                  "Cyclic inheritance detected in type '" + typeDescriptor.getTypeName() + "'",
                                  objectNode);
                }
                
                // Check if parent type exists (if specified)
                JsonTypeDescriptor parentType = typeDescriptor.getParent();
                if (parentType != null) {
                    // Verify that the parent type is registered in the model
                    String parentTypeName = parentType.getTypeName();
                    if (!descriptor.containsType(parentTypeName)) {
                        context.addError("editnode.type.hierarchy.invalid",
                                      "Parent type '" + parentTypeName + "' of type '" + typeDescriptor.getTypeName() + "' not found in model",
                                      objectNode);
                    }
                }
                
                // Check interface implementations
                for (JsonTypeDescriptor implementor : typeDescriptor.getImplementors()) {
                    String implementorName = implementor.getTypeName();
                    if (!descriptor.containsType(implementorName)) {
                        context.addWarning("editnode.type.implementor.missing",
                                         "Implementor type '" + implementorName + "' of type '" + typeDescriptor.getTypeName() + "' not found in model",
                                         objectNode);
                    }
                }
            }
        }
        
        // Recursively validate children
        for (int i = 0; i < node.getChildCount(); i++) {
            EditNode child = node.getChildAt(i);
            if (child instanceof EditNodeAbstract) {
                validateObjectHierarchy((EditNodeAbstract) child, descriptor, context, visitedTypes);
            }
        }
    }
    
    /**
     * Checks if a type has cyclic inheritance.
     *
     * @param typeDescriptor the type descriptor to check
     * @param visited set to track visited types
     * @return true if cyclic inheritance is detected
     */
    private boolean hasCyclicInheritance(JsonTypeDescriptor typeDescriptor, Set<String> visited) {
        if (typeDescriptor == null) {
            return false;
        }
        
        String typeName = typeDescriptor.getTypeName();
        
        // Check if we've already visited this type (cycle detected)
        if (visited.contains(typeName)) {
            return true;
        }
        
        // Add to visited set
        visited.add(typeName);
        
        try {
            // Check parent type
            JsonTypeDescriptor parentType = typeDescriptor.getParent();
            if (parentType != null) {
                if (hasCyclicInheritance(parentType, new HashSet<>(visited))) {
                    return true;
                }
            }
            
            // Check implementors (interfaces)
            for (JsonTypeDescriptor implementor : typeDescriptor.getImplementors()) {
                if (hasCyclicInheritance(implementor, new HashSet<>(visited))) {
                    return true;
                }
            }
            
            return false;
        } finally {
            visited.remove(typeName);
        }
    }
    
    @Override
    public String getId() {
        return "EditNodeTypeHierarchyValidator";
    }
    
    @Override
    public String getDescription() {
        return "Validates type hierarchy and inheritance relationships";
    }
}
