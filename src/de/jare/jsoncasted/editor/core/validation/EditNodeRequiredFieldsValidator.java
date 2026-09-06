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
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validator that checks if all required fields of a type are present in EditNodeObject instances.
 * 
 * <p>Validates:</p>
 * <ul>
 *   <li>For each EditNodeObject with JsonTypeDescriptor:</li>
 *   <li>All fields with isRequired() == true are present as children</li>
 *   <li>Field names match exactly</li>
 * </ul>
 * 
 * @author Janusch Rentenatus
 */
public class EditNodeRequiredFieldsValidator implements EditTreeValidator {
    
    @Override
    public void validate(EditTree tree, ValidationContext context) {
        JsonModelDescriptor descriptor = context.getModelDescriptor();
        
        // If no model descriptor, skip validation
        if (descriptor == null) {
            return;
        }
        
        // Traverse the tree to find all object nodes
        validateObjectNode(tree.getRoot(), descriptor, context);
    }
    
    /**
     * Validates a single object node for required fields.
     *
     * @param node the node to validate
     * @param descriptor the model descriptor
     * @param context the validation context
     */
    private void validateObjectNode(EditNodeAbstract node, JsonModelDescriptor descriptor, ValidationContext context) {
        if (node instanceof EditNodeObject) {
            EditNodeObject objectNode = (EditNodeObject) node;
            JsonTypeDescriptor typeDescriptor = objectNode.getJsonType();
            
            // If the object doesn't have a type descriptor yet, we can't validate required fields
            // This might happen if the on-the-fly type assignment hasn't been done
            if (typeDescriptor == null) {
                // Try to get the type from the model by name
                String typeName = objectNode.getName();
                if (typeName != null && !typeName.isEmpty()) {
                    typeDescriptor = descriptor.getType(typeName);
                    if (typeDescriptor == null) {
                        typeDescriptor = descriptor.getTypePerceptive(typeName);
                    }
                }
            }
            
            if (typeDescriptor != null) {
                // Get all required fields for this type
                List<JsonFieldDescriptor> requiredFields = getRequiredFields(typeDescriptor);
                
                if (!requiredFields.isEmpty()) {
                    // Collect all field names present as children
                    Set<String> presentFieldNames = getPresentFieldNames(objectNode);
                    
                    // Check for missing required fields
                    for (JsonFieldDescriptor requiredField : requiredFields) {
                        String fieldName = requiredField.getFieldName();
                        if (!presentFieldNames.contains(fieldName)) {
                            context.addWarning("editnode.type.required.missing",
                                             "Required field '" + fieldName + "' is missing in type '" + typeDescriptor.getTypeName() + "'",
                                             objectNode);
                        }
                    }
                }
            }
        }
        
        // Recursively validate children
        for (int i = 0; i < node.getChildCount(); i++) {
            EditNode child = node.getChildAt(i);
            if (child instanceof EditNodeAbstract) {
                validateObjectNode((EditNodeAbstract) child, descriptor, context);
            }
        }
    }
    
    /**
     * Extracts all required fields from a type descriptor.
     *
     * @param typeDescriptor the type descriptor
     * @return list of required field descriptors
     */
    private List<JsonFieldDescriptor> getRequiredFields(JsonTypeDescriptor typeDescriptor) {
        List<JsonFieldDescriptor> requiredFields = new ArrayList<>();
        
        // Check all fields (both constructor params and regular fields)
        for (JsonFieldDescriptor field : typeDescriptor.getAllFields()) {
            if (field.isRequired()) {
                requiredFields.add(field);
            }
        }
        
        return requiredFields;
    }
    
    /**
     * Collects all field names present as children of an object node.
     *
     * @param objectNode the object node
     * @return set of present field names
     */
    private Set<String> getPresentFieldNames(EditNodeObject objectNode) {
        Set<String> fieldNames = new HashSet<>();
        
        for (int i = 0; i < objectNode.getChildCount(); i++) {
            EditNode child = objectNode.getChildAt(i);
            if (child instanceof EditNodeProperty) {
                String name = child.getName();
                if (name != null && !name.isEmpty()) {
                    fieldNames.add(name);
                }
            } else if (child instanceof EditNodePropertyArr) {
                String name = child.getName();
                if (name != null && !name.isEmpty()) {
                    fieldNames.add(name);
                }
            }
        }
        
        return fieldNames;
    }
    
    @Override
    public String getId() {
        return "EditNodeRequiredFieldsValidator";
    }
    
    @Override
    public String getDescription() {
        return "Validates that all required fields of a type are present in EditNodeObject instances";
    }
}
