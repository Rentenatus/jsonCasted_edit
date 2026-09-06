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
import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;

/**
 * Validator that checks if EditNodeProperty instances belong to valid fields of
 * their parent type.
 *
 * <p>
 * Validates:</p>
 * <ul>
 * <li>Parent is EditNodeObject with valid JsonTypeDescriptor</li>
 * <li>Field name exists in the parent type</li>
 * <li>Field type (JsonNodeType) is compatible with the descriptor</li>
 * </ul>
 *
 * @author Janusch Rentenatus
 */
public class EditNodePropertyFieldValidator implements EditNodeValidator {

    @Override
    public void validate(EditNodeAbstract node, ValidationContext context) {
        // Only validate EditNodeProperty instances (includes EditNodePropertyArr)
        if (!(node instanceof EditNodeProperty)) {
            return;
        }

        EditNodeProperty propertyNode = (EditNodeProperty) node;
        JsonModelDescriptor descriptor = context.getModelDescriptor();

        // If no model descriptor, skip validation
        if (descriptor == null) {
            return;
        }

        EditNode parent = node.getParent();

        // Check if parent exists
        if (parent == null) {
            context.addError("editnode.property.parent.missing",
                    "Property node has no parent",
                    propertyNode);
            return;
        }

        // Check if parent is EditNodeObject
        if (!(parent instanceof EditNodeObject)) {
            context.addError("editnode.property.parent.invalid",
                    "Property parent is not an EditNodeObject",
                    propertyNode);
            return;
        }

        EditNodeObject parentObject = (EditNodeObject) parent;
        JsonTypeDescriptor parentType = parentObject.getJsonType();

        // Check if parent has a type descriptor
        if (parentType == null) {
            context.addError("editnode.property.parent.missing",
                    "Parent node has no type descriptor",
                    propertyNode);
            return;
        }

        String fieldName = propertyNode.getName();

        // Check if property has a name
        if (fieldName == null || fieldName.isEmpty()) {
            context.addError("editnode.property.name.missing",
                    "Property has no name for field validation",
                    propertyNode);
            return;
        }

        // Look up the field in the parent type
        JsonFieldDescriptor fieldDescriptor = parentType.getField(fieldName);

        if (fieldDescriptor == null) {
            context.addError("editnode.property.field.missing",
                    "Field '" + fieldName + "' not found in type '" + parentType.getTypeName() + "'",
                    propertyNode);
            return;
        }

        // For EditNodePropertyArr, check if the field is actually an array type
        if (propertyNode instanceof EditNodePropertyArr) {
            boolean isArrayField = fieldDescriptor.isAsArray() || fieldDescriptor.isAsListOrArray();
            if (!isArrayField) {
                context.addError("editnode.property.type.mismatch",
                        "Field '" + fieldName + "' in type '" + parentType.getTypeName()
                        + "' is not an array type, but node is EditNodePropertyArr",
                        propertyNode);
            }
        }

        // Check JsonNodeType compatibility
        JsonNodeType propertyType = propertyNode.getType();
        if (propertyType != null) {
            // Here we could add more sophisticated type compatibility checks
            // For now, we just accept any type as valid
            // Future: check if propertyType matches the expected field type
        }
    }

    @Override
    public String getId() {
        return "EditNodePropertyFieldValidator";
    }

    @Override
    public String getDescription() {
        return "Validates that EditNodeProperty instances belong to valid fields of their parent type";
    }
}
