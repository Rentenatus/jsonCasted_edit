/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.JsonCollectionType;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonFieldTypeNote;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import de.jare.jsoncasted.model.item.JsonAnnotation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Permissible edit offers of the hard parse mode (parse mode concept, section 4): the query layer between the
 * editor menus and the model descriptor. For a selected node it computes the children the model allows - already
 * present fields and annotations are excluded - and returns them as prepared, typed nodes, so the menu can offer
 * nothing but entries that keep the tree in a model-valid state.
 *
 * @author Janusch Rentenatus
 */
public final class HardEditAdvisor {

    /**
     * One permissible offer: a display label and the prepared child node for the
     * {@code edit.addPrepared} command.
     */
    public static final class ChildProposal {

        private final String label;
        private final EditNodeAbstract preparedChild;

        /**
         * Creates a proposal.
         *
         * @param label the display label of the offer
         * @param preparedChild the prepared child node
         */
        public ChildProposal(String label, EditNodeAbstract preparedChild) {
            this.label = label;
            this.preparedChild = preparedChild;
        }

        public String getLabel() {
            return label;
        }

        public EditNodeAbstract getPreparedChild() {
            return preparedChild;
        }
    }

    private HardEditAdvisor() {
    }

    /**
     * Computes the permissible children of the selected node in hard mode.
     *
     * <ul>
     * <li>Object with a resolved type: the missing declared fields - scalars typed, 0:1 references with a prepared
     * value object (implementors of an interface instead of the approximate interface cast), collections as array
     * properties, enum values as scalar properties. A map object offers free entries with the mapping element
     * type.</li>
     * <li>Object without a type or property without a resolved field: nothing (hard mode offers only known
     * things).</li>
     * <li>Collection property: rows of the declared element type (implementors on interface element types).</li>
     * <li>0:1 property without a value child: the value object of the declared type.</li>
     * <li>Annotation: string rows (an annotation is implicitly an array of strings).</li>
     * </ul>
     *
     * @param node the selected node
     * @param descriptor the model descriptor
     * @return the permissible offers, never null
     */
    public static List<ChildProposal> permissibleChildren(EditNodeAbstract node, JsonModelDescriptor descriptor) {
        final List<ChildProposal> proposals = new ArrayList<>();
        if (node == null || descriptor == null) {
            return proposals;
        }
        if (node instanceof EditNodeAnnotation) {
            proposals.add(new ChildProposal("row (String)", new EditNodeObject("row")));
            return proposals;
        }
        if (node instanceof EditNodeObject owner) {
            collectObjectChildren(owner, descriptor, proposals);
            return proposals;
        }
        if (node instanceof EditNodeProperty property) {
            collectPropertyChildren(property, descriptor, proposals);
        }
        return proposals;
    }

    /**
     * Computes the permissible annotations of the selected node in hard mode: only declared ones, and only those
     * not already present. Object anchors offer the type declarations (pure wildcards like {@code doc:*} cover the
     * type itself, prefix patterns stay field specific), field anchors offer the explicit field declarations plus
     * the wildcard declarations of the owning type that match the field name.
     *
     * @param node the selected node (owning object or field property)
     * @param descriptor the model descriptor
     * @return the permissible annotation offers, never null
     */
    public static List<ChildProposal> permissibleAnnotations(EditNodeAbstract node, JsonModelDescriptor descriptor) {
        final List<ChildProposal> proposals = new ArrayList<>();
        if (node == null || descriptor == null || node instanceof EditNodeAnnotation) {
            return proposals;
        }
        final Set<String> names = new LinkedHashSet<>();
        if (node instanceof EditNodeProperty property) {
            collectFieldAnnotationNames(property, names);
        } else if (node instanceof EditNodeObject owner) {
            collectObjectAnnotationNames(owner, names);
        }
        names.removeAll(presentAnnotationNames(node));
        for (String name : names) {
            proposals.add(new ChildProposal(AnnotationKeys.PREFIX + name, new EditNodeAnnotation(name)));
        }
        return proposals;
    }

    /**
     * Explains why the hard mode offers no children for the selected node, so the disabled add menu can tell the
     * user the model reason instead of looking broken.
     *
     * @param node the selected node, or null
     * @param descriptor the model descriptor
     * @return the explanation, or null when there is nothing to explain
     */
    public static String explainEmptyChildren(EditNodeAbstract node, JsonModelDescriptor descriptor) {
        if (node == null || descriptor == null) {
            return "No node selected.";
        }
        if (node instanceof EditNodeAnnotation) {
            return null; // rows are always offered
        }
        if (node instanceof EditNodeObject owner) {
            if (owner.getJsonType() == null) {
                return "The object has no resolved type.";
            }
            if (owner.getJsonType().getMappingAllFields() != null) {
                return null; // map entries are always offered
            }
            return "All declared fields of type " + owner.getJsonType().getTypeName() + " are present.";
        }
        if (node instanceof EditNodeProperty property) {
            if (property.getJsonField() == null) {
                return "The property has no resolved field.";
            }
            if (property.getJsonField().getCollectionType() != null
                    && property.getJsonField().getCollectionType() != JsonCollectionType.NONE) {
                return "The element type of the collection is unknown.";
            }
            if (hasObjectChild(property)) {
                return "The 0:1 value is already present.";
            }
            return "Scalar values are edited in place, not added as children.";
        }
        return null;
    }

    /**
     * Explains why the hard mode offers no annotation for the selected node.
     *
     * @param node the selected node, or null
     * @param descriptor the model descriptor
     * @return the explanation, or null when there is nothing to explain
     */
    public static String explainEmptyAnnotations(EditNodeAbstract node, JsonModelDescriptor descriptor) {
        if (node == null || descriptor == null) {
            return "No node selected.";
        }
        if (node instanceof EditNodeAnnotation) {
            return "Annotations never anchor under annotations.";
        }
        return "No declared annotation remains for this anchor.";
    }

    // ========== Object children ==========

    private static void collectObjectChildren(EditNodeObject owner, JsonModelDescriptor descriptor,
            List<ChildProposal> proposals) {
        final JsonTypeDescriptor type = owner.getJsonType();
        if (type == null) {
            return;
        }
        final JsonFieldTypeNote mapping = type.getMappingAllFields();
        if (mapping != null) {
            // Free map entry: the key name is editable afterwards, the value carries the mapping element type.
            proposals.add(mapEntryProposal("key", mapping.getTypeName(), descriptor));
            return;
        }
        for (JsonFieldDescriptor field : type.getAllFields()) {
            if (field == null || hasChildNamed(owner, field.getFieldName())) {
                continue;
            }
            addChildProposalForField(field, descriptor, proposals);
        }
    }

    private static void addChildProposalForField(JsonFieldDescriptor field, JsonModelDescriptor descriptor,
            List<ChildProposal> proposals) {
        final JsonCollectionType collection = field.getCollectionType();
        if (collection != null && collection != JsonCollectionType.NONE) {
            proposals.add(new ChildProposal(
                    field.getFieldName() + ": " + collection.getLiteral() + " of " + field.getTypeName(),
                    new EditNodeProperty(field.getFieldName(), JsonNodeType.ARRAY)));
            return;
        }
        final JsonTypeDescriptor fieldType = descriptor.getType(field.getTypeName());
        if (fieldType == null) {
            return; // hard mode offers only known types
        }
        if (isScalar(fieldType)) {
            proposals.add(new ChildProposal(field.getFieldName() + ": " + field.getTypeName(),
                    new EditNodeProperty(field.getFieldName(), scalarNodeType(fieldType))));
            return;
        }
        // 0:1 reference: the implementors of an interface instead of the approximate interface cast
        for (String castName : castsFor(fieldType)) {
            proposals.add(referenceProposal(field.getFieldName(), castName));
        }
    }

    // ========== Property children ==========

    private static void collectPropertyChildren(EditNodeProperty property, JsonModelDescriptor descriptor,
            List<ChildProposal> proposals) {
        final JsonFieldDescriptor field = property.getJsonField();
        if (field == null) {
            return; // hard mode offers nothing without a resolved field
        }
        final JsonCollectionType collection = field.getCollectionType();
        if (collection != null && collection != JsonCollectionType.NONE) {
            // rows of the declared element type
            final JsonTypeDescriptor elementType = descriptor.getType(field.getTypeName());
            if (elementType == null) {
                return;
            }
            if (isScalar(elementType)) {
                proposals.add(new ChildProposal("row (" + field.getTypeName() + ")", new EditNodeObject("row")));
                return;
            }
            for (String castName : castsFor(elementType)) {
                proposals.add(new ChildProposal("row (" + castName + ")", typedObject("row", castName)));
            }
            return;
        }
        // 0:1 reference: exactly one value child, none when already occupied
        if (hasObjectChild(property)) {
            return;
        }
        final JsonTypeDescriptor fieldType = descriptor.getType(field.getTypeName());
        if (fieldType == null || isScalar(fieldType)) {
            return; // scalar values are edited in place, not added as children
        }
        for (String castName : castsFor(fieldType)) {
            proposals.add(new ChildProposal("value (" + castName + ")", typedObject("value", castName)));
        }
    }

    // ========== Annotations ==========

    private static void collectObjectAnnotationNames(EditNodeObject owner, Set<String> names) {
        final JsonTypeDescriptor type = owner.getJsonType();
        if (type == null || type.getAnnotations() == null) {
            return;
        }
        for (JsonAnnotation annotation : type.getAnnotations()) {
            if (annotation == null) {
                continue;
            }
            final String name = annotation.getName();
            final int cut = name.indexOf(AnnotationKeys.SEPARATOR);
            if (cut < 0) {
                names.add(name);
            } else if ("*".equals(name.substring(cut + 1))) {
                names.add(name.substring(0, cut)); // pure wildcard covers the object level
            }
        }
    }

    private static void collectFieldAnnotationNames(EditNodeProperty property, Set<String> names) {
        final JsonFieldDescriptor field = property.getJsonField();
        if (field != null && field.getAnnotations() != null) {
            for (JsonAnnotation annotation : field.getAnnotations()) {
                if (annotation == null) {
                    continue;
                }
                final String name = annotation.getName();
                if (name.indexOf(AnnotationKeys.SEPARATOR) < 0) {
                    names.add(name);
                }
            }
        }
        if (property.getParent() instanceof EditNodeObject owner) {
            final JsonTypeDescriptor ownerType = owner.getJsonType();
            if (ownerType == null || ownerType.getAnnotations() == null) {
                return;
            }
            final String fieldName = property.getName();
            for (JsonAnnotation annotation : ownerType.getAnnotations()) {
                if (annotation == null) {
                    continue;
                }
                final String name = annotation.getName();
                final int cut = name.indexOf(AnnotationKeys.SEPARATOR);
                if (cut > 0 && AnnotationKeys.matchesPattern(name.substring(cut + 1), fieldName)) {
                    names.add(name.substring(0, cut));
                }
            }
        }
    }

    private static Set<String> presentAnnotationNames(EditNodeAbstract node) {
        final Set<String> present = new LinkedHashSet<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeAnnotation annotation) {
                present.add(annotation.getAnnotationName());
            }
        }
        return present;
    }

    // ========== Helpers ==========

    private static ChildProposal referenceProposal(String fieldName, String castName) {
        final EditNodeProperty refProp = new EditNodeProperty(fieldName, JsonNodeType.OBJECT);
        refProp.addChild(typedObject(fieldName, castName), new EditTimes());
        return new ChildProposal(fieldName + ": " + castName, refProp);
    }

    private static ChildProposal mapEntryProposal(String keyName, String elementTypeName,
            JsonModelDescriptor descriptor) {
        final JsonTypeDescriptor elementType = descriptor.getType(elementTypeName);
        if (elementType != null && isScalar(elementType)) {
            return new ChildProposal("map entry (" + elementTypeName + ")",
                    new EditNodeProperty(keyName, scalarNodeType(elementType)));
        }
        final EditNodeProperty entryProp = new EditNodeProperty(keyName, JsonNodeType.OBJECT);
        if (elementTypeName != null) {
            entryProp.addChild(typedObject(keyName, elementTypeName), new EditTimes());
        }
        return new ChildProposal("map entry (" + elementTypeName + ")", entryProp);
    }

    private static EditNodeObject typedObject(String name, String castName) {
        final EditNodeObject value = new EditNodeObject(name);
        value.setCastName(castName);
        return value;
    }

    private static List<String> castsFor(JsonTypeDescriptor type) {
        final List<String> casts = new ArrayList<>();
        final List<JsonTypeDescriptor> implementors = type.getImplementors();
        if (implementors == null || implementors.isEmpty()) {
            casts.add(type.getTypeName());
            return casts;
        }
        for (JsonTypeDescriptor implementor : implementors) {
            if (implementor != null) {
                casts.add(implementor.getTypeName());
            }
        }
        return casts;
    }

    private static boolean isScalar(JsonTypeDescriptor type) {
        return type.isPrimitive() || (type.getPermittedValues() != null && !type.getPermittedValues().isEmpty());
    }

    private static JsonNodeType scalarNodeType(JsonTypeDescriptor type) {
        final JsonNodeType nodeType = type.getNodeType();
        return nodeType == JsonNodeType.OBJECT || nodeType == JsonNodeType.ARRAY
                ? JsonNodeType.STRING : nodeType;
    }

    private static boolean hasChildNamed(EditNodeObject owner, String name) {
        for (int i = 0; i < owner.getChildCount(); i++) {
            final EditNode child = owner.getChildAt(i);
            if (child instanceof EditNodeProperty && !(child instanceof EditNodeAnnotation)
                    && name.equals(child.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasObjectChild(EditNodeProperty property) {
        for (int i = 0; i < property.getChildCount(); i++) {
            if (property.getChildAt(i) instanceof EditNodeObject) {
                return true;
            }
        }
        return false;
    }
}
