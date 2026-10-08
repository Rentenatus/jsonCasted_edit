/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import java.util.HashMap;
import java.util.Map;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import de.jare.jsoncasted.model.item.JsonAnnotation;

/**
 * A property node that is an annotation (annotation concept). The wood JSON carries the {@code @} prefix in its key;
 * the editor model never does - the annotation name is stored plainly and the display name is composed. The kind is
 * bound to the class, so no rename can silently turn an annotation into a field or the other way round: renaming edits
 * the annotation name, never the kind.
 *
 * <p>
 * The composite target is never stored - it is derived from the structural position: anchored under a field property
 * the annotation is composite and the parent field name is its target, anchored under an object it is a simple object
 * level annotation. Copy, cut, paste and move therefore always arrive consistent: the annotation adopts the context it
 * lands in, without any parallel string state.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public class EditNodeAnnotation extends EditNodeProperty {

    private String annotationName;

    /**
     * Creates an annotation with its plain name (without prefix and without target).
     *
     * @param annotationName the annotation name without prefix
     */
    public EditNodeAnnotation(String annotationName) {
        super(annotationName, JsonNodeType.ARRAY);
        this.annotationName = annotationName;
    }

    EditNodeAnnotation(long editId, long leftRange, long rightRange, long timesRange,
            String annotationName, String primValue) {
        super(editId, leftRange, rightRange, timesRange, annotationName, JsonNodeType.ARRAY, primValue);
        this.annotationName = annotationName;
    }

    public String getAnnotationName() {
        return annotationName;
    }

    /**
     * Returns the composite target derived from the structural position: the name of the field property this annotation
     * is anchored under, or null for a simple object level annotation.
     *
     * @return the target field name, or null
     */
    public String getTargetField() {
        return getParent() instanceof EditNodeProperty parentProp
                && !(parentProp instanceof EditNodeAnnotation)
                        ? parentProp.getName()
                        : null;
    }

    public boolean isComposite() {
        return getTargetField() != null;
    }

    // ========== Name / Label ==========
    @Override
    public String getName() {
        final String name = AnnotationKeys.PREFIX + annotationName;
        return isComposite() ? name + AnnotationKeys.SEPARATOR + getTargetField() : name;
    }

    /**
     * Renames the annotation without ever changing its kind: a leading {@code @} is accepted and stripped. The
     * composite target is structural and never part of the name - a composite input syntax is reduced to its annotation
     * name part.
     *
     * @param name the new annotation name, with or without prefix
     */
    @Override
    public void setName(String name) {
        String plain = name != null && name.startsWith(AnnotationKeys.PREFIX)
                ? name.substring(AnnotationKeys.PREFIX.length())
                : name;
        if (plain == null || plain.isBlank()) {
            return; // an annotation always needs a name
        }
        final int separator = plain.indexOf(AnnotationKeys.SEPARATOR);
        if (separator >= 0) {
            plain = plain.substring(0, separator);
        }
        final String oldName = getName();
        this.annotationName = plain;

        // Notify parser listener about name change — this triggers
        // asynchronous re-parsing via the parse queue.
        final EditTree tree = getEditTree();
        if (tree != null) {
            tree.notifyNodeNameChanged(this, oldName, getName());
        }
    }

    @Override
    public String toString() {
        return maskEscapes(getName() + rightString());
    }

    // ========== Type identification ==========
    @Override
    public String getTypeKey() {
        return AnnotationKeys.FOREANNOTATION;
    }

    // ========== Type constraints ==========
    @Override
    public boolean canBeChildOf(EditNode parent) {
        return parent != null && parent.canBeParentOfAnnotation();
    }

    @Override
    public boolean canBeParentOfAnnotation() {
        return false;
    }

    // ========== Type assignment ==========
    @Override
    public boolean tryAssignType(JsonModelDescriptor descriptor) {
        if (descriptor == null) {
            setEditStatus(EditStatus.STATELESS);
            setEditMessage(null);
            return false;
        }
        return tryAssignAnnotationType(descriptor);
    }

    /**
     * Annotation binding path (annotation concept, decisions 6 to 8): declared annotations bind OKAY; undeclared ones
     * are free annotations and are tolerated with a WARNING instead of an error. The binding context is the structural
     * anchor: under a field property the declaration of that field counts (plus the wildcard declarations of the owning
     * type), under an object the declaration of the type.
     *
     * @param descriptor the model descriptor for the declaration lookup
     * @return true when the annotation is declared, false for a tolerated annotation
     */
    private boolean tryAssignAnnotationType(JsonModelDescriptor descriptor) {
        final String annName = getAnnotationName();
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

        setEditStatus(EditStatus.WARNING);
        setEditMessage("Annotation has no binding context");
        typeAnnotationRows();
        return false;
    }

    /**
     * Checks the declaration of the annotation and sets the edit status accordingly: OKAY with a confirmation message
     * for a declared annotation, a WARNING for a tolerated free annotation.
     *
     * @param descriptor the model descriptor for the model name in the message
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
     * Hands the implicit string element type to the rows of this annotation: an annotation is implicitly an array of
     * strings, so its rows adopt the String type instead of waiting for a field resolution that never comes.
     */
    private void typeAnnotationRows() {
        final EditTree tree = getEditTree();
        if (tree != null) {
            tree.propagateElementTypeToChildren(this);
        }
    }

    // ========== Attributes ==========
    @Override
    public Map<String, JackAttribut> getAttributes() {
        // The attribute view shows the pure data of the annotation: the plain
        // annotation name (no prefix, no composite target), the implicit type
        // and the derived target. jsonField and primValue of the property
        // inheritance are meaningless here and stay hidden.
        final Map<String, JackAttribut> attributes = new HashMap<>();
        attributes.put("name", new JackAttribut("name", getAnnotationName()));
        attributes.put("type", new JackAttribut("type", getType()));
        attributes.put("field", new JackAttribut("field", getTargetField()));
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
    }

    // ========== Factory methods ==========
    /**
     * Creates a new string row for this annotation: an annotation is implicitly an array of strings, so its rows are
     * value objects, exactly like the rows of a field. Structural children (fields, nested arrays) do not exist under
     * an annotation.
     *
     * @param aName the name for the new row
     * @return a new EditNodeObject row
     */
    @Override
    public EditNodeObject createChild(String aName) {
        return new EditNodeObject(aName);
    }

    /**
     * Annotations do not create array child nodes - they are implicitly arrays of strings themselves.
     *
     * @param aName ignored
     * @return never
     * @throws UnsupportedOperationException always
     */
    @Override
    public EditNodePropertyArr createArrChild(String aName) {
        throw new UnsupportedOperationException("Annotations do not create array child nodes.");
    }

    @Override
    public EditNodeAnnotation createNeighbor(String aName) {
        return new EditNodeAnnotation(aName);
    }

    // ========== Deep Copy ==========
    @Override
    public EditNodeAbstract deepCopy(boolean regenerateEditId) {
        final EditNodeAnnotation copy = new EditNodeAnnotation(
                regenerateEditId ? IdGenerator.EDIT_ID_GENERATOR.nextId() : getEditId(),
                getLeftRange(), getRightRange(), getTimesRange(),
                annotationName, getValue());

        for (EditNodeAbstract child : getAbstractChildren()) {
            final EditNodeAbstract deepCopy = child.deepCopy(regenerateEditId);
            copy.addChildPhase1(deepCopy, copy.getChildCount());
            copy.addChildPhase2Fast(deepCopy);
        }
        return copy;
    }

}
