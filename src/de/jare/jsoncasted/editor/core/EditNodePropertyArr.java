/**
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a JSON array property node in the tree structure. This is a
 * specialized property that is always of type ARRAY.
 *
 * @author Jansuch Rentenatus
 */
public final class EditNodePropertyArr extends EditNodeProperty implements EditNode {

    /**
     * Creates a new array property with the given name. The type is
     * automatically set to ARRAY.
     *
     */
    public EditNodePropertyArr() {
        super("array");
        setType(JsonNodeType.ARRAY);
    }

    @Override
    public boolean tryAssignType(JsonModelDescriptor descriptor) {
        if (descriptor == null) {
            setEditStatus(EDIT_STATELESS);
            setEditMessage(null);
            return false;
        }

        // Pruefen: Hat Parent einen JsonTypeDescriptor?
        EditNode parent = getParent();
        if (!(parent instanceof EditNodeObject)) {
            setEditStatus(EDIT_WARNING);
            setEditMessage("Cannot resolve field: parent has no type");
            return false;
        }

        EditNodeObject parentObject = (EditNodeObject) parent;
        JsonTypeDescriptor parentType = parentObject.getJsonType();

        if (parentType == null) {
            setEditStatus(EDIT_WARNING);
            setEditMessage("Cannot resolve field: parent has no type descriptor");
            return false;
        }

        String fieldName = getName();
        if (fieldName == null || fieldName.isEmpty()) {
            setEditStatus(EDIT_WARNING);
            setEditMessage("Property has no name for field assignment");
            return false;
        }

        // Suche nach dem Field im Parent-Typ
        JsonFieldDescriptor foundField = parentType.getField(fieldName);

        if (foundField != null) {
            // Zusätzlich prüfen: Feld muss Array-Typ unterstützen
            if (foundField.isAsArray() || foundField.isAsListOrArray()) {
                setJsonField(foundField);
                setEditStatus(EDIT_OKAY);
                setEditMessage(null);
                return true;
            } else {
                setEditStatus(EDIT_ERROR);
                setEditMessage("Field '" + fieldName + "' in type '" + parentType.getTypeName() + "' is not an array type");
                return false;
            }
        } else {
            setEditStatus(EDIT_ERROR);
            setEditMessage("Field '" + fieldName + "' not found in type '" + parentType.getTypeName() + "'");
            return false;
        }
    }

    /**
     * Creates a new array property with the given parameters. The type is
     * automatically set to ARRAY.
     *
     * @param editId the edit identifier
     * @param leftRange the left range value
     * @param rightRange the right range value
     * @param timesRange the times range value
     * @param propName the name of the array property
     * @param primValue the primitive value (may be null for arrays)
     */
    private EditNodePropertyArr(long editId, long leftRange, long rightRange, long timesRange, String propName, String primValue) {
        super(editId, leftRange, rightRange, timesRange, propName, JsonNodeType.ARRAY, primValue);
    }

    // ========== Type constraints ==========
    @Override
    public void setType(JsonNodeType type) {
        // Array properties are always of type ARRAY
        // Ignore any attempts to change the type
        if (type != JsonNodeType.ARRAY) {
            throw new IllegalArgumentException("Array property type cannot be changed. It must always be ARRAY.");
        }
        super.setType(type);
    }

    @Override
    public JsonNodeType getType() {
        return JsonNodeType.ARRAY;
    }

    @Override
    public boolean canBeChildOf(EditNode parent) {
        // Array properties can only be children of properties (not objects)
        return parent != null && parent.canBeParentOfPropertyArrData();
    }

    // ========== Factory methods ==========
    @Override
    public EditNodePropertyArr createNeighbor(String aName) {
        return new EditNodePropertyArr();
    }

    // ========== Deep Copy ==========
    @Override
    public EditNodeAbstract deepCopy(boolean regenerateEditId) {
        EditNodePropertyArr copy = new EditNodePropertyArr(
                regenerateEditId ? IdGenerator.EDIT_ID_GENERATOR.nextId() : getEditId(),
                getLeftRange(), getRightRange(), getTimesRange(),
                getPropName(), getValue());

        for (EditNodeAbstract child : getAbstractChildren()) {
            final EditNodeAbstract deepCopy = child.deepCopy(regenerateEditId);
            copy.addChildPhase1(deepCopy, copy.getChildCount());
            copy.addChildPhase2Fast(deepCopy);
        }
        copy.setJsonField(getJsonField());
        return copy;
    }

    // ========== Type identification ==========
    @Override
    public String getTypeKey() {
        return FOREARRAY;
    }

    // ========== Attributes ==========
    @Override
    public Map<String, JackAttribut> getAttributes() {
        Map<String, JackAttribut> attributes = new HashMap<>();
        attributes.put("name", new JackAttribut("name", getName()));
        attributes.put("primValue", new JackAttribut("primValue", getValue()));
        attributes.put("type", new JackAttribut("type", getType()));
        attributes.put("jsonField", new JackAttribut("jsonField", getJsonField()));
        return putEditAttributes(attributes);
    }

    @Override
    public void setAttributes(Map<String, JackAttribut> props) {
        if (props == null) {
            return;
        }
        JackAttribut nameAttr = props.get("name");
        if (nameAttr != null) {
            setName((String) nameAttr.getValue());
        }
        JackAttribut valueAttr = props.get("primValue");
        if (valueAttr != null) {
            setValue((String) valueAttr.getValue());
        }
        // Type is always ARRAY, so we ignore type from props
        JackAttribut fieldAttr = props.get("jsonField");
        if (fieldAttr != null) {
            setJsonField((JsonFieldDescriptor) fieldAttr.getValue());
        }
    }

    @Override
    public String getValue() {
        return "array";
    }

    @Override
    public String toString() {
        return maskEscapes(getName() + rightString());
    }

    @Override
    public String rightString() {
        return previewChildren();
    }
}
