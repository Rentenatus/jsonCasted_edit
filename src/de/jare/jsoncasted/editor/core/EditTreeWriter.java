/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Writes an {@link EditTree} back to wood JSON - the counterpart of {@link JsonTreeConverter}. Field level
 * annotations are flattened into their composite keys ({@code "@doc:profile"} at the object level), object level
 * annotations keep their simple keys. Transient annotations are skipped, but only when the declaration is loaded
 * with the tree: without a model descriptor nothing is filtered (annotation concept, decisions 8 and 9).
 *
 * @author Janusch Rentenatus
 */
public final class EditTreeWriter {

    private static final String INDENT = "    ";

    private EditTreeWriter() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Serializes the tree to a wood JSON string.
     *
     * @param tree the edit tree to serialize
     * @return the wood JSON representation of the tree
     */
    public static String toJsonString(EditTree tree) {
        final StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        writeWoodModel(tree, sb);
        writeObjectMembers((EditNodeObject) tree.getRoot(), 1, sb);
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Serializes the tree to a wood JSON file (UTF-8).
     *
     * @param target the file to write
     * @param tree the edit tree to serialize
     * @throws IOException if the file cannot be written
     */
    public static void toJsonFile(File target, EditTree tree) throws IOException {
        Files.write(target.toPath(), toJsonString(tree).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes the {@code _woodModel} entry when the tree carries a loaded descriptor and a description file path, so
     * a saved file finds its description again on reload.
     */
    private static void writeWoodModel(EditTree tree, StringBuilder sb) {
        final JsonModelDescriptor descriptor = tree.getJsonModelDescriptor();
        final String descPath = tree.getDescriptionFilePath();
        if (descriptor == null || descPath == null || descPath.isBlank()) {
            return;
        }
        sb.append(INDENT).append("\"_woodModel\": { \"name\": \"");
        escape(descriptor.getModelName(), sb);
        sb.append("\", \"fileName\": \"");
        escape(descPath, sb);
        sb.append("\" },\n");
    }

    /**
     * Writes the members of an object node (the root or an element object) in child order: field level annotations
     * are flattened to their composite key right before their field, object level annotations keep their position.
     * Transient annotations are skipped when their declaration is loaded.
     */
    private static void writeObjectMembers(EditNodeObject node, int depth, StringBuilder sb) {
        boolean first = true;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!(node.getChildAt(i) instanceof EditNodeProperty prop)) {
                continue;
            }
            if (AnnotationKeys.isAnnotationKey(prop.getName())) {
                first = writeObjectLevelAnnotation(node, prop, depth, first, sb);
                continue;
            }
            first = writeFieldAnnotations(prop, depth, first, sb);
            first = writeProperty(prop, prop.getName(), false, depth, first, sb);
        }
    }

    /**
     * Writes an object level annotation (simple key or orphan composite anchor) unless its declaration is transient.
     */
    private static boolean writeObjectLevelAnnotation(EditNodeObject owner, EditNodeProperty prop, int depth,
            boolean first, StringBuilder sb) {
        final String annName = AnnotationKeys.annotationName(prop.getName());
        final String target = AnnotationKeys.targetField(prop.getName());
        final JsonTypeDescriptor ownerType = owner.getJsonType();
        if (ownerType != null) {
            if (target == null) {
                if (isTransient(ownerType.getAnnotation(annName))) {
                    return first;
                }
            } else {
                final JsonFieldDescriptor targetField = ownerType.getField(target);
                if (targetField != null && isTransient(targetField.getAnnotation(annName))) {
                    return first;
                }
            }
        }
        return writeProperty(prop, prop.getName(), AnnotationKeys.isCompositeKey(prop.getName()), depth, first, sb);
    }

    /**
     * Writes the annotation children of a field property as composite keys, unless their declaration for that field
     * is transient.
     */
    private static boolean writeFieldAnnotations(EditNodeProperty fieldProp, int depth, boolean first, StringBuilder sb) {
        for (int i = 0; i < fieldProp.getChildCount(); i++) {
            if (!(fieldProp.getChildAt(i) instanceof EditNodeProperty ann)) {
                continue;
            }
            final String annName = AnnotationKeys.annotationName(ann.getName());
            if (annName == null) {
                continue;
            }
            final JsonFieldDescriptor field = fieldProp.getJsonField();
            if (field != null && isTransient(field.getAnnotation(annName))) {
                continue;
            }
            final String key = AnnotationKeys.PREFIX + annName + AnnotationKeys.SEPARATOR + fieldProp.getName();
            writeProperty(ann, key, true, depth, first, sb);
            first = false;
        }
        return first;
    }

    /**
     * Writes a property (or annotation) with the given key. Composite keys are quoted because of the separator;
     * plain keys stay unquoted in the wood style.
     */
    private static boolean writeProperty(EditNodeProperty prop, String key, boolean quotedKey, int depth,
            boolean first, StringBuilder sb) {
        if (!first) {
            sb.append(",\n");
        }
        sb.append(indent(depth)).append(quotedKey ? "\"" + key + "\"" : key).append(": ");
        writeValue(prop, depth, sb);
        return false;
    }

    /**
     * Writes the value of a property: an array of elements, a nested object or a typed scalar.
     */
    private static void writeValue(EditNodeProperty prop, int depth, StringBuilder sb) {
        final JsonNodeType type = prop.getType();
        if (type == JsonNodeType.ARRAY) {
            sb.append("[\n");
            boolean first = true;
            for (int i = 0; i < prop.getChildCount(); i++) {
                final EditNode child = prop.getChildAt(i);
                if (!(child instanceof EditNodeAbstract elem)) {
                    continue;
                }
                // Annotation children of a field node are no collection
                // elements - they are flattened into composite keys.
                if (child instanceof EditNodeProperty annChild
                        && AnnotationKeys.isAnnotationKey(annChild.getName())) {
                    continue;
                }
                if (!first) {
                    sb.append(",\n");
                }
                writeElement(elem, depth + 1, sb);
                first = false;
            }
            if (first) {
                sb.setLength(sb.length() - 2); // empty array: no line break after the opening bracket
                sb.append(']');
                return;
            }
            sb.append('\n').append(indent(depth)).append(']');
            return;
        }
        if (type == JsonNodeType.OBJECT) {
            writeObjectChild(prop, depth, sb);
            return;
        }
        writeScalar(prop, sb);
    }

    /**
     * Writes the single object child of an object-valued property as a nested object.
     */
    private static void writeObjectChild(EditNodeProperty prop, int depth, StringBuilder sb) {
        for (int i = 0; i < prop.getChildCount(); i++) {
            if (prop.getChildAt(i) instanceof EditNodeObject obj) {
                writeObject(obj, depth, sb);
                return;
            }
        }
        sb.append("{ }");
    }

    /**
     * Writes one array element: a nested array, an object or a scalar row.
     */
    private static void writeElement(EditNodeAbstract elem, int depth, StringBuilder sb) {
        if (elem instanceof EditNodePropertyArr arr) {
            sb.append("[\n");
            boolean first = true;
            for (int i = 0; i < arr.getChildCount(); i++) {
                if (!(arr.getChildAt(i) instanceof EditNodeAbstract inner)) {
                    continue;
                }
                if (!first) {
                    sb.append(",\n");
                }
                writeElement(inner, depth + 1, sb);
                first = false;
            }
            if (first) {
                sb.setLength(sb.length() - 2);
                sb.append(']');
                return;
            }
            sb.append('\n').append(indent(depth)).append(']');
            return;
        }
        if (elem instanceof EditNodeObject obj) {
            if (obj.getChildCount() == 0) {
                writeRowValue(obj, sb);
                return;
            }
            writeObject(obj, depth, sb);
            return;
        }
        if (elem instanceof EditNodeProperty rowProp) {
            writeScalar(rowProp, sb);
        }
    }

    /**
     * Writes an object node with its members.
     */
    private static void writeObject(EditNodeObject obj, int depth, StringBuilder sb) {
        sb.append("{\n");
        writeObjectMembers(obj, depth + 1, sb);
        sb.append('\n').append(indent(depth)).append('}');
    }

    /**
     * Writes the primitive value of a property according to its JSON node type: strings are quoted, numbers and
     * booleans stay raw, a missing value becomes null.
     */
    private static void writeScalar(EditNodeProperty prop, StringBuilder sb) {
        final String value = prop.getValue();
        final JsonNodeType type = prop.getType();
        if (value == null) {
            sb.append("null");
            return;
        }
        if (type == JsonNodeType.STRING || type == JsonNodeType.NULL) {
            sb.append('"');
            escape(value, sb);
            sb.append('"');
            return;
        }
        if (type == JsonNodeType.LONG || type == JsonNodeType.NUMBER || type == JsonNodeType.BOOLEAN) {
            sb.append(value);
            return;
        }
        sb.append('"');
        escape(value, sb);
        sb.append('"');
    }

    /**
     * Writes a scalar row of an array. Rows carry no JSON node type in the tree, so the cast decides: a row parsed
     * as String is quoted, everything else is written raw when it looks like a number or a boolean and quoted
     * otherwise.
     */
    private static void writeRowValue(EditNodeObject row, StringBuilder sb) {
        final String value = row.getValue();
        if (value == null) {
            sb.append("null");
            return;
        }
        if ("String".equals(row.getCastName())) {
            sb.append('"');
            escape(value, sb);
            sb.append('"');
            return;
        }
        if (isNumberOrBoolean(value)) {
            sb.append(value);
            return;
        }
        sb.append('"');
        escape(value, sb);
        sb.append('"');
    }

    /**
     * Checks whether the given value looks like a raw JSON number or boolean.
     */
    private static boolean isNumberOrBoolean(String value) {
        if ("true".equals(value) || "false".equals(value)) {
            return true;
        }
        return value.matches("-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
    }

    /**
     * Checks whether the given declared annotation is transient.
     */
    private static boolean isTransient(de.jare.jsoncasted.model.item.JsonAnnotation declared) {
        return declared != null && declared.isTransient();
    }

    /**
     * Appends the JSON-escaped form of the given string.
     */
    private static void escape(String value, StringBuilder sb) {
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\n");
                case '\r' -> sb.append("\r");
                case '\t' -> sb.append("\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
    }

    private static String indent(int depth) {
        return INDENT.repeat(depth);
    }
}
