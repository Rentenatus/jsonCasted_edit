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
import de.jare.jsoncasted.model.item.JsonAnnotation;
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
    private volatile JsonFieldDescriptor jsonField;

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

        // Notify parser listener about name change — this triggers
        // asynchronous re-parsing via the parse queue.
        EditTree tree = getEditTree();
        if (tree != null) {
            tree.notifyNodeNameChanged(this, oldName, name);
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

        // Notify parser listener about type change — this triggers
        // asynchronous re-parsing via the parse queue.
        EditTree tree = getEditTree();
        if (tree != null) {
            tree.notifyTypeDescriptorChanged(this);
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
        final JsonFieldDescriptor oldField = this.jsonField;
        this.jsonField = jsonField;
        // Field event: a newly resolved field hands the declared element
        // type to the property's object children immediately (state-aware,
        // see EditTree#propagateElementTypeToChildren).
        if (jsonField != oldField) {
            final EditTree tree = getEditTree();
            if (tree != null) {
                tree.propagateElementTypeToChildren(this);
            }
        }
    }

    @Override
    public boolean tryAssignType(JsonModelDescriptor descriptor) {
        if (descriptor == null) {
            setEditStatus(EditStatus.STATELESS);
            setEditMessage(null);
            return false;
        }

        // Annotations never resolve a field: they bind against the declared
        // annotations of the field or type they are anchored at.
        if (AnnotationKeys.isAnnotationKey(getName())) {
            return tryAssignAnnotationType(descriptor);
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

        // Map entry: the parent type is a map (mappingAllFields set). Map
        // keys are dynamic and legitimate - there is no field to resolve.
        // The value semantics come from the mapping and are handed to the
        // children by the element type propagation.
        if (parentType != null && parentType.getMappingAllFields() != null) {
            markOkay(descriptor);
            final EditTree tree = getEditTree();
            if (tree != null) {
                tree.propagateElementTypeToChildren(this);
            }
            return true;
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

        // 2. Parent ohne Typ: eindeutige Felder befruchten den Knoten blind,
        //    mehrdeutige bleiben mit Kontext-Warnung liegen. Eine spaetere
        //    Typkorrektur des Parents re-parsed diesen Knoten und loest das
        //    Feld dann sauber gegen den Parent-Typ auf.
        if (parentType == null) {
            if (knownFields.size() == 1) {
                setJsonField(knownFields.get(0));
                setEditStatus(EditStatus.WARNING);
                setEditMessage("Parent has no type descriptor; field '" + fieldName
                        + "' was adopted as unique in the model");
                return true;
            }
            setEditStatus(EditStatus.WARNING);
            setEditMessage("Parent has no type descriptor; field '" + fieldName
                    + "' is ambiguous (declared in " + knownFields.size() + " types)");
            return false;
        }

        // 3. Feld innerhalb des Parent-Typs aufloesen.
        JsonFieldDescriptor foundField = parentType.getField(fieldName);
        if (foundField != null) {
            if (validateFieldType(foundField, fieldName, parentType)) {
                setJsonField(foundField);
                markOkay(descriptor);
                return true;
            }
            return false;
        }

        // 4. Feld existiert im Modell, aber nicht im Parent-Typ: eindeutige
        //    Felder werden blind uebernommen (mit Hinweis auf den fehlenden
        //    Kontext), mehrdeutige werden als Fehler gegen den Parent gemeldet.
        //    Hinweis: Die Blind-Uebernahme prueft den Wert bewusst nicht
        //    (validateFieldType bleibt aus); die Validierung laeuft nach,
        //    sobald der Parent-Kontext eintrifft und der Korrekturkreis den
        //    Knoten neu parst.
        if (knownFields.size() == 1) {
            setJsonField(knownFields.get(0));
            setEditStatus(EditStatus.WARNING);
            setEditMessage("Field '" + fieldName + "' is unique in the model and was adopted,"
                    + " but is not declared in type '" + parentType.getTypeName() + "'");
            return true;
        }
        List<String> declaringTypeNames = collectDeclaringTypeNames(descriptor, fieldName);
        setEditStatus(EditStatus.ERROR);
        setEditMessage("Field '" + fieldName + "' not found in type '" + parentType.getTypeName()
                + "' (declared in " + declaringTypeNames.size() + " type(s): "
                + String.join(", ", declaringTypeNames) + ")");
        return false;
    }

    /**
     * Annotation binding path (annotation concept, decisions 6 to 8): a property with an {@code @} name is an
     * annotation. Declared annotations bind OKAY; undeclared ones are free annotations and are tolerated with a
     * WARNING instead of an error. Composite anchors ({@code @doc:profile}) resolve their target field and move
     * under the field node; a missing target leaves the annotation anchored at the object with a WARNING - no
     * synthetic null field is ever created.
     *
     * @param descriptor the model descriptor for the declaration lookup
     * @return true when the annotation is declared, false for a tolerated or unresolved annotation
     */
    private boolean tryAssignAnnotationType(JsonModelDescriptor descriptor) {
        final String annName = AnnotationKeys.annotationName(getName());
        final String target = AnnotationKeys.targetField(getName());
        final EditNode parent = getParent();

        if (parent instanceof EditNodeProperty fieldNode) {
            // Field level annotation anchored under its field node. An explicit
            // field declaration wins; without one, a wildcard declaration of
            // the owning type (doc:*) covers the field.
            final JsonFieldDescriptor field = fieldNode.getJsonField();
            if (field == null) {
                setEditStatus(EditStatus.WARNING);
                setEditMessage("Field of the annotated property is not resolved yet");
                typeAnnotationRows();
                return false;
            }
            JsonAnnotation declared = field.getAnnotation(annName);
            if (declared == null && fieldNode.getParent() instanceof EditNodeObject fieldOwner) {
                declared = AnnotationKeys.findWildcardDeclaration(fieldOwner.getJsonType(), annName,
                        fieldNode.getName());
            }
            return checkAnnotationDeclaration(descriptor, declared,
                    "field '" + field.getFieldName() + "'");
        }

        if (parent instanceof EditNodeObject parentObject) {
            if (target == null) {
                // Object level annotation. A pure wildcard declaration (doc:*)
                // covers the type itself as well, prefix patterns stay field
                // specific.
                final JsonTypeDescriptor parentType = parentObject.getJsonType();
                if (parentType == null) {
                    setEditStatus(EditStatus.WARNING);
                    setEditMessage("Parent has no type descriptor; annotation waits for binding");
                    typeAnnotationRows();
                    return false;
                }
                JsonAnnotation declared = parentType.getAnnotation(annName);
                if (declared == null) {
                    declared = AnnotationKeys.findWildcardDeclaration(parentType, annName, "*");
                }
                return checkAnnotationDeclaration(descriptor, declared,
                        "type '" + parentType.getTypeName() + "'");
            }

            // Orphan composite anchor: when the target field property exists,
            // move under it - the parse cascade re-binds this way. Otherwise
            // stay anchored with a WARNING until the field appears.
            final EditNode fieldNode = findChildByName(parentObject, target);
            if (fieldNode instanceof EditNodeProperty fieldProp) {
                rebindToFieldProperty(fieldProp, annName);
                return false;
            }
            final JsonTypeDescriptor parentType = parentObject.getJsonType();
            if (parentType != null && parentType.getField(target) == null) {
                setEditStatus(EditStatus.WARNING);
                setEditMessage("Target field '" + target + "' is not declared in type '"
                        + parentType.getTypeName() + "'");
                typeAnnotationRows();
                return false;
            }
            setEditStatus(EditStatus.WARNING);
            setEditMessage("Target field '" + target + "' is missing; annotation stays anchored");
            typeAnnotationRows();
            return false;
        }

        setEditStatus(EditStatus.WARNING);
        setEditMessage("Annotation has no binding context");
        typeAnnotationRows();
        return false;
    }

    /**
     * Checks the declaration of the annotation and sets the edit status accordingly: OKAY with a confirmation
     * message for a declared annotation, a WARNING for a tolerated free annotation.
     *
     * @param descriptor the model descriptor for the declaration lookup
     * @param declared the declared annotation, or {@code null} for a free annotation
     * @param where the binding context for the message (field or type name)
     * @return true when the annotation is declared
     */
    private boolean checkAnnotationDeclaration(JsonModelDescriptor descriptor, JsonAnnotation declared, String where) {
        typeAnnotationRows();
        if (declared == null) {
            setEditStatus(EditStatus.WARNING);
            setEditMessage("Annotation is not declared for " + where + " (tolerated free annotation)");
            return false;
        }
        setEditStatus(EditStatus.OKAY);
        setEditMessage(declared.isTransient()
                ? "ok, transient annotation, model=" + descriptor.getModelName()
                : "ok, model=" + descriptor.getModelName());
        return true;
    }

    /**
     * Hands the implicit string element type to the rows of this annotation: an annotation is implicitly an array
     * of strings, so its rows adopt the String type instead of waiting for a field resolution that never comes.
     */
    private void typeAnnotationRows() {
        final EditTree tree = getEditTree();
        if (tree != null) {
            tree.propagateElementTypeToChildren(this);
        }
    }

    /**
     * Moves this orphan composite annotation under the given field property and strips the composite anchor from
     * the name. The move happens before the rename so the name change notification re-queues the node and the
     * parse cascade re-evaluates it as a field level annotation under its new parent.
     *
     * @param fieldProp the target field property
     * @param annName the plain annotation name without prefix and target
     */
    private void rebindToFieldProperty(EditNodeProperty fieldProp, String annName) {
        fieldProp.addChild(this, new EditTimes());
        setName(AnnotationKeys.PREFIX + annName);
    }

    /**
     * Returns the child of the given object node with the specified name.
     *
     * @param parent the object node to search
     * @param name the child name to find
     * @return the child node, or {@code null} if none matches
     */
    private EditNode findChildByName(EditNodeObject parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            final EditNode child = parent.getChildAt(i);
            if (name.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    /**
     * Validates whether the found field is compatible with this property type.
     * Base implementation accepts any field. Subclasses (e.g. EditNodePropertyArr)
     * can override this to enforce type-specific constraints (e.g. array type).
     *
     * @param foundField the field descriptor found in the parent type
     * @param fieldName the field name being resolved
     * @param parentType the parent type descriptor
     * @return true if the field is valid for this property type
     */
    protected boolean validateFieldType(JsonFieldDescriptor foundField, String fieldName, JsonTypeDescriptor parentType) {
        return true;
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
    protected List<String> collectDeclaringTypeNames(JsonModelDescriptor descriptor, String fieldName) {
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
        // Resilience (annotation concept, decision 7): annotations anchored
        // under this field are never silently removed with it. They fall back
        // to the owning object with their composite anchor and are re-bound
        // by the parse cascade as soon as a field of that name appears.
        if (!(parent instanceof EditNodeObject owner)) {
            return;
        }
        final List<EditNodeProperty> annotations = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof EditNodeProperty child
                    && AnnotationKeys.isAnnotationKey(child.getName())) {
                annotations.add(child);
            }
        }
        for (EditNodeProperty annotation : annotations) {
            final String annName = AnnotationKeys.annotationName(annotation.getName());
            annotation.setName(AnnotationKeys.PREFIX + annName + AnnotationKeys.SEPARATOR + getName());
            owner.addChild(annotation, new EditTimes());
        }
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
        if (AnnotationKeys.isAnnotationKey(getName())) {
            return AnnotationKeys.FOREANNOTATION;
        }
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
