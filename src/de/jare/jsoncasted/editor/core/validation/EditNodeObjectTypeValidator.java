/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import java.util.ArrayList;
import java.util.List;

/**
 * Validator that checks if EditNodeObject instances correspond to existing types in the model.
 * 
 * <p>Validates:</p>
 * <ul>
 *   <li>EditNodeObject.getName() corresponds to a type in JsonModelDescriptor</li>
 *   <li>Type can be found via exact name or perceptual matching</li>
 * </ul>
 * 
 * @author Janusch Rentenatus
 */
public class EditNodeObjectTypeValidator implements EditNodeValidator {
    
    @Override
    public void validate(EditNodeAbstract node, ValidationContext context) {
        // Only validate EditNodeObject instances
        if (!(node instanceof EditNodeObject)) {
            return;
        }
        
        EditNodeObject objectNode = (EditNodeObject) node;
        JsonModelDescriptor descriptor = context.getModelDescriptor();
        
        // If no model descriptor, skip validation
        if (descriptor == null) {
            return;
        }
        
        String nodeName = objectNode.getName();
        
        // Skip validation if node has no name
        if (nodeName == null || nodeName.isEmpty()) {
            context.addError("editnode.object.name.missing", 
                          "Object node has no name for type validation", 
                          objectNode);
            return;
        }
        
        // Try exact match first
        JsonTypeDescriptor exactType = descriptor.getType(nodeName);
        JsonTypeDescriptor perceptualType = null;
        
        if (exactType == null) {
            // Try perceptual matching
            perceptualType = descriptor.getTypePerceptive(nodeName);
        }
        
        if (exactType != null) {
            // Exact match found - everything is good
            return;
        }
        
        if (perceptualType != null) {
            // Perceptive match found - this is okay but could be ambiguous
            // Check if there are multiple matches (ambiguous case)
            List<String> matchingTypes = findAllPerceptiveMatches(descriptor, nodeName);
            if (matchingTypes.size() > 1) {
                context.addWarning("editnode.object.type.ambiguous",
                                 "Multiple types match '" + nodeName + "' perceptively: " + matchingTypes,
                                 objectNode);
            }
            return;
        }
        
        // No match found - this is an error
        context.addError("editnode.object.type.missing",
                       "Type '" + nodeName + "' not found in model",
                       objectNode);
    }
    
    /**
     * Finds all types that match the given name perceptively.
     *
     * @param descriptor the model descriptor
     * @param typeName the type name to match
     * @return list of matching type names
     */
    private List<String> findAllPerceptiveMatches(JsonModelDescriptor descriptor, String typeName) {
        List<String> matches = new ArrayList<>();
        
        if (typeName == null || typeName.contains(".")) {
            return matches;
        }
        
        String endingName = "." + typeName;
        for (String key : descriptor.getTypesKeys()) {
            if (key.endsWith(endingName)) {
                matches.add(key);
            }
        }
        
        return matches;
    }
    
    @Override
    public String getId() {
        return "EditNodeObjectTypeValidator";
    }
    
    @Override
    public String getDescription() {
        return "Validates that EditNodeObject instances correspond to existing types in the model";
    }
}
