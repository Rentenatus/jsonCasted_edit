/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a JSON object node in the editable tree structure. Contains
 * object-specific properties such as object ID, info, and type descriptor.
 *
 * @author Janusch Rentenatus
 */
public final class EditNodeObject extends EditNodeAbstract implements EditNode {

    /**
     * Type key constant for object nodes.
     */
    public static final String FOREOBJECT = "fore.object";

    private String objektValue;
    private String objektInfo;
    private String objektId;
    private JsonTypeDescriptor jsonType;

    /**
     * Creates a new EditNodeObject with the specified value.
     *
     * @param objektValue the value/name for this object node
     */
    public EditNodeObject(String objektValue) {
        super();
        this.objektValue = objektValue;
        this.objektInfo = "";
    }

    /**
     * Creates a new EditNodeObject with the specified value and info.
     *
     * @param objektValue the value/name for this object node
     * @param objektInfo the info string for this object node
     */
    public EditNodeObject(String objektValue, String objektInfo) {
        super();
        this.objektValue = objektValue;
        this.objektInfo = objektInfo;
    }

    /**
     * Creates a new EditNodeObject with the specified ID and range values.
     *
     * @param editId the edit identifier
     * @param leftRange the left range value
     * @param rightRange the right range value
     * @param timesRange the times range value
     * @param objektInfo the info string for this object node
     */
    private EditNodeObject(long editId, long leftRange, long rightRange, long timesRange, String objektInfo) {
        super(editId, leftRange, rightRange, timesRange);
        this.objektValue = objektInfo;
    }

    // ========== Name / Label ==========
    @Override
    public String getName() {
        return objektValue;
    }

    /**
     * Sets the name of this object node.
     *
     * @param name the new name to set
     */
    @Override
    public void setName(String name) {
        this.objektValue = name;
    }

    /**
     * Returns the value of this node.
     *
     * @return the objektValue
     */
    @Override
    public String getValue() {
        return objektValue;
    }

    // ========== JsonTreeNodeData methods ==========
    /**
     * Returns the info string for this object node.
     *
     * @return the objektInfo
     */
    public String getObjektInfo() {
        return objektInfo;
    }

    /**
     * Sets the info string for this object node.
     *
     * @param objektInfo the info string to set
     */
    public void setObjektInfo(String objektInfo) {
        this.objektInfo = objektInfo;
    }

    /**
     * Returns the object ID for this node.
     *
     * @return the objektId
     */
    public String getObjektId() {
        return objektId;
    }

    /**
     * Sets the object ID for this node.
     *
     * @param objektId the object ID to set
     */
    public void setObjektId(String objektId) {
        this.objektId = objektId;
    }

    /**
     * Returns the JSON type descriptor for this node.
     *
     * @return the jsonType descriptor
     */
    public JsonTypeDescriptor getJsonType() {
        return jsonType;
    }

    /**
     * Sets the JSON type descriptor for this node.
     *
     * @param jsonType the JSON type descriptor to set
     */
    public void setJsonType(JsonTypeDescriptor jsonType) {
        this.jsonType = jsonType;
    }

    @Override
    public String toString() {
        String value = getValue();
        return maskEscapes(value == null ? "" : value + " : " + rightString());
    }

    @Override
    public String rightString() {
        return (objektInfo == null || objektInfo.isEmpty()) ? "" : ": " + objektInfo;
    }

    // ========== Factory methods ==========
    @Override
    public EditNodeProperty createChild(String aName) {
        return new EditNodeProperty(aName);
    }

    @Override
    public EditNodeProperty createArrChild(String aName) {
        return new EditNodeProperty(aName, JsonNodeType.ARRAY);
    }

    @Override
    public EditNodeObject createNeighbor(String aName) {
        return new EditNodeObject(aName);
    }

    // ========== Deep Copy ==========
    @Override
    public EditNodeAbstract deepCopy(boolean regenerateEditId) {
        EditNodeObject copy = new EditNodeObject(
                regenerateEditId ? IdGenerator.EDIT_ID_GENERATOR.nextId() : getEditId(),
                getLeftRange(), getRightRange(), getTimesRange(),
                objektValue);

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
        if (parent instanceof EditNodeProperty prop) {
            prop.onChildObjectDataRemoved(this);
        }
    }

    // ========== Type constraints ==========
    @Override
    public boolean canBeChildOf(EditNode parent) {
        return parent != null && parent.canBeParentOfObjectData();
    }

    @Override
    public boolean canBeParentOfPropertyData() {
        return true;
    }

    @Override
    public String getTypeKey() {
        return FOREOBJECT;
    }

    @Override
    public Map<String, JackAttribut> getAttributes() {
        Map<String, JackAttribut> attributes = new HashMap<>();
        attributes.put("value", new JackAttribut("value", getValue()));
        attributes.put("infotype", new JackAttribut("infotype", getObjektInfo()));
        attributes.put("objektId", new JackAttribut("objektId", getObjektId()));
        attributes.put("jsonType", new JackAttribut("jsonType", getJsonType()));
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
        JackAttribut valueAttr = props.get("value");
        if (valueAttr != null) {
            setValue((String) valueAttr.getValue());
        }
        JackAttribut infoAttr = props.get("infotype");
        if (infoAttr != null) {
            setObjektInfo((String) infoAttr.getValue());
        }
        JackAttribut info2Attr = props.get("info");
        if (info2Attr != null) {
            setObjektInfo((String) info2Attr.getValue());
        }
        JackAttribut idAttr = props.get("objektId");
        if (idAttr != null) {
            setObjektId((String) idAttr.getValue());
        }
        JackAttribut typeAttr = props.get("jsonType");
        if (typeAttr != null) {
            setJsonType((JsonTypeDescriptor) typeAttr.getValue());
        }
    }

}
