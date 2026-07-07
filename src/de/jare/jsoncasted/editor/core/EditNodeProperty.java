/**
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a JSON property node in the editable tree structure. Contains
 * property-specific information such as property name, primitive value, type,
 * and field descriptor.
 *
 * @author Janusch Rentenatus
 */
public non-sealed class EditNodeProperty extends EditNodeAbstract implements EditNode {

    /**
     * Type key constant for property nodes.
     */
    public static final String FOREPROPERTY = "fore.property";

    /**
     * Type key constant for array property nodes.
     */
    public static final String FOREARRAY = "fore.array";

    private String propName;
    private String primValue;
    private JsonNodeType type;
    private JsonFieldDescriptor jsonField;

    /**
     * Creates a new EditNodeProperty with the specified name and NULL type.
     *
     * @param propName the property name
     */
    public EditNodeProperty(String propName) {
        this(propName, JsonNodeType.NULL);
    }

    /**
     * Creates a new EditNodeProperty with the specified name and type.
     *
     * @param propName the property name
     * @param type the JSON node type
     */
    public EditNodeProperty(String propName, JsonNodeType type) {
        super();
        this.propName = propName;
        this.primValue = null;
        this.type = type;
    }

    /**
     * Creates a new EditNodeProperty with the specified ID, range values, and
     * properties.
     *
     * @param editId the edit identifier
     * @param leftRange the left range value
     * @param rightRange the right range value
     * @param timesRange the times range value
     * @param propName the property name
     * @param type the JSON node type
     * @param primValue the primitive value
     */
    EditNodeProperty(long editId, long leftRange, long rightRange, long timesRange, String propName, JsonNodeType type, String primValue) {
        super(editId, leftRange, rightRange, timesRange);
        this.propName = propName;
        this.primValue = primValue;
        this.type = type;
    }

    // ========== Name / Label ==========
    @Override
    public String getName() {
        return propName;
    }

    /**
     * Sets the name of this property node.
     *
     * @param name the new name to set
     */
    @Override
    public void setName(String name) {
        this.propName = name;
    }

    // ========== JsonTreeNodeData methods ==========
    /**
     * Returns the property name.
     *
     * @return the property name
     */
    public String getPropName() {
        return propName;
    }

    /**
     * Sets the property name, sanitizing it by replacing '=' with space,
     * trimming whitespace, and replacing spaces with underscores.
     *
     * @param propName the property name to set
     */
    public void setPropName(String propName) {
        this.propName = propName.replace('=', ' ')
                .trim()
                .replace(' ', '_');
    }

    @Override
    public String getValue() {
        return primValue;
    }

    @Override
    public void setValue(String value) {
        this.primValue = value;
    }

    /**
     * Returns the JSON node type of this property.
     *
     * @return the JsonNodeType
     */
    public JsonNodeType getType() {
        return type;
    }

    /**
     * Sets the JSON node type of this property.
     *
     * @param type the JsonNodeType to set
     */
    public void setType(JsonNodeType type) {
        this.type = type;
    }

    /**
     * Returns the JSON field descriptor for this property.
     *
     * @return the JsonFieldDescriptor
     */
    public JsonFieldDescriptor getJsonField() {
        return jsonField;
    }

    /**
     * Sets the JSON field descriptor for this property.
     *
     * @param jsonField the JsonFieldDescriptor to set
     */
    public void setJsonField(JsonFieldDescriptor jsonField) {
        this.jsonField = jsonField;
    }

    // ========== Factory methods ==========
    @Override
    public String toString() {
        return maskEscapes(propName + rightString());
    }

    @Override
    public String rightString() {
        if (type == JsonNodeType.ARRAY) {
            return previewChildren();
        }
        String value = getValue();
        return value == null ? " =" : " = '" + value + "'";
    }

    public String previewChildren() {
        StringBuilder sb = new StringBuilder();
        int index = 0;
        for (EditNode child : getChildren()) {
            if (child == null) {
                return "";
            }
            String value = String.valueOf(child.getValue());
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            if (value.length() > 48) {
                sb.append(value.substring(0, 43)).append("(...)");
            } else {
                sb.append(value);
            }
            index++;
            if (index > 7 || sb.length() > 128) {
                break;
            }
        }
        if (index < getChildCount()) {
            sb.append(", ...");
        }
        return " \u00B7" + getChildCount() + ":  [" + (sb.append(']'));
    }

    // ========== Factory methods ==========
    @Override
    public EditNodeObject createChild(String aName) {
        return new EditNodeObject(aName);
    }

    @Override
    public EditNodePropertyArr createArrChild(String aName) {
        return new EditNodePropertyArr();
    }

    @Override
    public EditNodeProperty createNeighbor(String aName) {
        return new EditNodeProperty(aName);
    }

    // ========== Deep Copy ==========
    @Override
    public EditNodeAbstract deepCopy(boolean regenerateEditId) {
        EditNodeProperty copy = new EditNodeProperty(
                regenerateEditId ? IdGenerator.EDIT_ID_GENERATOR.nextId() : getEditId(),
                getLeftRange(), getRightRange(), getTimesRange(),
                propName, type, getValue());

        for (EditNodeAbstract child : getAbstractChildren()) {
            final EditNodeAbstract deepCopy = child.deepCopy(regenerateEditId);
            copy.addChildPhase1(deepCopy, copy.getChildCount());
            copy.addChildPhase2Fast(deepCopy);
        }
        return copy;
    }

    // ========== Removal callback ==========
    @Override
    public void sayOnRemoved(EditNode parent) {
        // No special handling for properties
    }

    @Override
    public void onChildObjectDataRemoved(EditNodeObject child) {
        // When an object child is removed, store its primitive value
        setValue(child.getValue());
    }

    // ========== Type constraints ==========
    @Override
    public boolean canBeChildOf(EditNode parent) {
        return parent != null && parent.canBeParentOfPropertyData();
    }

    @Override
    public boolean canBeParentOfObjectData() {
        return true;
    }

    @Override
    public boolean canBeParentOfPropertyArrData() {
        return true;
    }

    @Override
    public String getTypeKey() {
        return type == JsonNodeType.ARRAY ? FOREARRAY : FOREPROPERTY;
    }

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
        JackAttribut typeAttr = props.get("type");
        if (typeAttr != null) {
            setType((JsonNodeType) typeAttr.getValue());
        }
        JackAttribut fieldAttr = props.get("jsonField");
        if (fieldAttr != null) {
            setJsonField((JsonFieldDescriptor) fieldAttr.getValue());
        }
    }

}
