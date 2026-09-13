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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        String oldName = this.propName;
        this.propName = name;
        
        // Notify parser listener about name change
        EditTree tree = getEditTree();
        if (tree != null) {
            tree.notifyNodeNameChanged(this, oldName, name);
            // Trigger Typzuordnung neu, falls sich der Name ändert
            if (tree.getJsonModelDescriptor() != null) {
                tree.assignTypesForNode(this);
            }
        }
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
        String oldValue = this.primValue;
        this.primValue = value;
        
        // Notify parser listener about value change
        EditTree tree = getEditTree();
        if (tree != null) {
            tree.notifyNodeValueChanged(this, oldValue, value);
        }
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
        
        // Trigger Typzuordnung neu, falls sich der Typ ändert
        EditTree tree = getEditTree();
        if (tree != null && tree.getJsonModelDescriptor() != null) {
            tree.assignTypesForNode(this);
        }
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

    @Override
    public boolean tryAssignType(JsonModelDescriptor descriptor) {
        if (descriptor == null) {
            setEditStatus(EditStatus.STATELESS);
            setEditMessage(null);
            return false;
        }

        // Pruefen: Hat Parent einen JsonTypeDescriptor?
        EditNode parent = getParent();
        if (!(parent instanceof EditNodeObject)) {
            setEditStatus(EditStatus.WARNING);
            setEditMessage("Cannot resolve field: parent has no type");
            return false;
        }

        EditNodeObject parentObject = (EditNodeObject) parent;
        JsonTypeDescriptor parentType = parentObject.getJsonType();

        String fieldName = getName();
        if (fieldName == null || fieldName.isEmpty()) {
            setEditStatus(EditStatus.WARNING);
            setEditMessage("Property has no name for field assignment");
            return false;
        }

        // 1. Schnelle Existenzpruefung ueber die gesamte Modell-Feldkarte.
        //    Jeder Eintrag fasst alle Felddefinitionen gleichen Namens zusammen,
        //    die Listengroesse ist also die Anzahl der Typen, die das Feld
        //    deklarieren (Mass fuer Mehrdeutigkeit).
        Map<String, List<JsonFieldDescriptor>> fieldMap = descriptor.getOrCreateFieldMap();
        List<JsonFieldDescriptor> knownFields = fieldMap.get(fieldName);
        if (knownFields == null || knownFields.isEmpty()) {
            setEditStatus(EditStatus.ERROR);
            setEditMessage("Field '" + fieldName + "' is unknown in model '" + descriptor.getModelName() + "'");
            return false;
        }

        // 2. Parent hat noch keinen Typ - Mehrdeutigkeit mit Kontext melden.
        if (parentType == null) {
            setEditStatus(EditStatus.WARNING);
            if (knownFields.size() == 1) {
                setEditMessage("Parent has no type descriptor; field '" + fieldName
                        + "' is unique in the model");
            } else {
                setEditMessage("Parent has no type descriptor; field '" + fieldName
                        + "' is ambiguous (declared in " + knownFields.size() + " types)");
            }
            return false;
        }

        // 3. Feld innerhalb des Parent-Typs aufloesen.
        JsonFieldDescriptor foundField = parentType.getField(fieldName);
        if (foundField != null) {
            setJsonField(foundField);
            setEditStatus(EditStatus.OKAY);
            setEditMessage(null);
            return true;
        }

        // Feld existiert im Modell, aber nicht im Parent-Typ - Kontext liefern.
        List<String> declaringTypeNames = collectDeclaringTypeNames(descriptor, fieldName);
        setEditStatus(EditStatus.ERROR);
        setEditMessage("Field '" + fieldName + "' not found in type '" + parentType.getTypeName()
                + "' (declared in " + declaringTypeNames.size() + " type(s): "
                + String.join(", ", declaringTypeNames) + ")");
        return false;
    }

    /**
     * Collects the names of all types in the model that declare a field with the
     * given name. Used to enrich error messages with context about where a field
     * is actually defined.
     *
     * @param descriptor the JsonModelDescriptor to search
     * @param fieldName the field name to look up
     * @return list of type names declaring the field (never null, may be empty)
     */
    private List<String> collectDeclaringTypeNames(JsonModelDescriptor descriptor, String fieldName) {
        List<String> names = new ArrayList<>();
        if (descriptor == null || fieldName == null) {
            return names;
        }
        for (JsonTypeDescriptor type : descriptor.getTypes()) {
            if (type != null && type.getField(fieldName) != null) {
                names.add(type.getTypeName());
            }
        }
        return names;
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
