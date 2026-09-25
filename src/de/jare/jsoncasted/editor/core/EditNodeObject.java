/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
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
    private volatile JsonTypeDescriptor jsonType;

    /**
     * Cast name extracted from the {@code TERM_CLASS} (_class) entry during
     * JSON-to-EditNode conversion. Serves as the primary lookup key for type
     * resolution in {@link #tryAssignType(JsonModelDescriptor)} so that the
     * correct substructure can be parsed even when the node name differs from
     * the type name (e.g. node "config1" vs. type "ConfigRoot").
     * <p>
     * May also be set by the parser's parent-type inference or root-type
     * handling to cache an inferred type name for subsequent re-parses. Remains
     * {@code null} when no {@code TERM_CLASS} was present and no inference has
     * assigned a type.
     * </p>
     */
    private volatile String castName;

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
        String oldName = this.objektValue;
        this.objektValue = name;

        // Notify parser listener about name change — this triggers
        // asynchronous re-parsing via the parse queue.
        EditTree tree = getEditTree();
        if (tree != null) {
            tree.notifyNodeNameChanged(this, oldName, name);
        }
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
     * Returns the cast name for this node. This is the {@code TERM_CLASS}
     * content extracted during JSON conversion, or a type name set by the
     * parser's inference logic. Used as the primary lookup key in
     * {@link #tryAssignType(JsonModelDescriptor)}.
     *
     * @return the cast name, or {@code null} if none was set
     */
    public String getCastName() {
        return castName;
    }

    /**
     * Sets the cast name. When set, {@link #tryAssignType} uses
     * {@code descriptor.getType(castName)} as the primary lookup before falling
     * back to the node name.
     *
     * @param castName the cast name to set, or {@code null} to clear
     */
    public void setCastName(String castName) {
        final String oldCast = this.castName;
        this.castName = castName;

        // Notify parser listener about a cast change - the cast name is the
        // primary parsing input for object nodes, so a changed cast must
        // trigger asynchronous re-parsing via the parse queue.
        EditTree tree = getEditTree();
        if (tree != null && !java.util.Objects.equals(oldCast, castName)) {
            tree.notifyTypeDescriptorChanged(this);
        }
    }

    @Override
    public boolean tryAssignType(JsonModelDescriptor descriptor) {
        if (descriptor == null) {
            setEditStatus(EditStatus.STATELESS);
            setEditMessage(null);
            return false;
        }

        // Primary search key: castName (from TERM_CLASS). 
        // Fallback to the node name if no castName is available.
        String cast = getCastName();
        String lookupKey = (cast != null && !cast.isEmpty()) ? cast : getName();
        if (lookupKey == null || lookupKey.isEmpty()) {
            setEditStatus(EditStatus.WARNING);
            setEditMessage("Object node has no cast name or name for type assignment");
            return false;
        }

        JsonTypeDescriptor foundType = descriptor.getType(lookupKey);

        if (foundType == null) { // Fallback: try a more perceptive search (e.g., case-insensitive, partial match)
            foundType = descriptor.getTypePerceptive(lookupKey);
        }

        if (foundType != null) {
            setJsonType(foundType);
            setCastName(foundType.getTypeName());
            markOkay(descriptor);
            return true;
        } else {
            setEditStatus(EditStatus.WARNING);
            setEditMessage("Type '" + lookupKey + "' not found in model");
            return false;
        }
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
        copy.setCastName(castName);
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
        attributes.put("|cast name", new JackAttribut("cast name", getCastName()));
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
        JackAttribut castNameAttr = props.get("|cast name");
        if (castNameAttr != null) {
            setCastName((String) castNameAttr.getValue());
        }
    }

}
