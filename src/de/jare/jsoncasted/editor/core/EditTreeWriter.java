/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Writes an {@link EditTree} back to wood JSON - the counterpart of {@link JsonTreeConverter}. Field level annotations
 * are flattened into their composite keys ({@code "@doc:profile"} at the object level), object level annotations keep
 * their simple keys. Every annotation is persistent - nothing is filtered on save.
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
        writeWoodDefinitions(tree, sb);
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
     * Writes the {@code _woodModel} entry when the tree carries a loaded descriptor and a description file path, so a
     * saved file finds its description again on reload.
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
     * Writes the wood metadata members of an object node: its identity ({@code _woodObjectId}) and, for a reference
     * stub, its link target ({@code _woodLink}). Identity members come first, like the jsonCasted writer does for
     * shared objects. A reference stub has no field children, so the link is its only member.
     *
     * @param node the object node
     * @param depth the indentation depth
     * @param first whether no member was written before
     * @param sb the target buffer
     * @return whether no member was written after this call
     */
    private static boolean writeWoodMeta(EditNodeObject node, int depth, boolean first, StringBuilder sb) {
        if (node.getObjektId() != null && !node.getObjektId().isEmpty()) {
            if (!first) {
                sb.append(",\n");
            }
            sb.append(indent(depth)).append("\"_woodObjectId\": \"").append(node.getObjektId()).append("\"");
            first = false;
        }
        if (node.isLinkReference()) {
            if (!first) {
                sb.append(",\n");
            }
            sb.append(indent(depth)).append("\"_woodLink\": \"");
            escape(node.getLinkTarget(), sb);
            sb.append("\"");
            first = false;
        }
        return first;
    }

    /**
     * Writes the {@code _woodDefinitions} section with the air objects of the linking set: objects that are still
     * referenced but no longer hang in the tree (deleted at edit level, held by the linking set). Objects without any
     * reference are dropped - otherwise every delete-and-undo cycle would leave a zombie definition behind. Objects
     * that hang in the tree (including a visible _woodDefinitions array from the import) are not duplicated here.
     *
     * @param tree the edit tree
     * @param sb the target buffer
     */
    private static void writeWoodDefinitions(EditTree tree, StringBuilder sb) {
        final EditLinkingSet linkingSet = tree.getLinkingSet();
        if (linkingSet == null) {
            return;
        }
        final java.util.Set<EditNodeAbstract> reachable = new java.util.HashSet<>();
        collectReachable(tree.getRoot(), reachable);

        final List<EditNodeObject> detached = new java.util.ArrayList<>();
        for (Map.Entry<String, EditNodeObject> entry : linkingSet.getObjectIdMap().entrySet()) {
            final EditNodeObject candidate = entry.getValue();
            if (candidate == null || reachable.contains(candidate)) {
                continue;
            }
            if (!isReferencedFromTree(linkingSet, entry.getKey(), reachable)) {
                continue; // not referenced from the exported tree: drop the zombie
            }
            detached.add(candidate);
        }
        if (detached.isEmpty()) {
            return;
        }

        sb.append(INDENT).append("\"_woodDefinitions\": [\n");
        boolean first = true;
        for (EditNodeObject obj : detached) {
            if (!first) {
                sb.append(",\n");
            }
            sb.append(INDENT).append("{ ");
            boolean memberFirst = true;
            if (obj.getCastName() != null && !obj.getCastName().isEmpty()) {
                sb.append("\"_class\": \"");
                escape(obj.getCastName(), sb);
                sb.append("\"");
                memberFirst = false;
            }
            memberFirst = writeWoodMeta(obj, 0, memberFirst, sb);
            // a detached reference stub has no members beyond the link
            if (!obj.isLinkReference()) {
                writeInlineMembers(obj, memberFirst, sb);
            }
            sb.append(" }");
            first = false;
        }
        sb.append("\n").append(INDENT).append("],\n");
    }

    /**
     * Writes the field members of an object inline (single line, wood style) - used for definition entries.
     *
     * @param node the object node
     * @param first whether no member was written before
     * @param sb the target buffer
     */
    private static void writeInlineMembers(EditNodeObject node, boolean first, StringBuilder sb) {
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!(node.getChildAt(i) instanceof EditNodeProperty prop)) {
                continue;
            }
            if (prop instanceof EditNodeAnnotation) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            sb.append(prop.getName()).append(": ");
            writeScalar(prop, sb);
            first = false;
        }
    }

    /**
     * Checks whether at least one reference stub pointing at the given target is itself part of the tree. Only those
     * references exist in the exported document, so only they justify a definition entry for a detached object.
     *
     * @param linkingSet the linking set
     * @param linkTarget the qualified link target key
     * @param reachable the identity set of tree nodes
     * @return true when a reachable stub references the target
     */
    private static boolean isReferencedFromTree(EditLinkingSet linkingSet, String linkTarget,
            java.util.Set<EditNodeAbstract> reachable) {
        for (EditNodeObject reference : linkingSet.getReferencesTo(linkTarget)) {
            if (reachable.contains(reference)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects all nodes reachable from the root, so detached objects in the linking set can be told apart from
     * tree members.
     *
     * @param node the current node
     * @param reachable the identity set to fill
     */
    private static void collectReachable(EditNodeAbstract node, java.util.Set<EditNodeAbstract> reachable) {
        if (node == null || !reachable.add(node)) {
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeAbstract child) {
                collectReachable(child, reachable);
            }
        }
    }

    /**
     * Writes the members of an object node (the root or an element object) in child order: field level annotations are
     * flattened to their composite key right before their field, object level annotations keep their position.
     */
    private static void writeObjectMembers(EditNodeObject node, int depth, StringBuilder sb) {
        boolean first = writeWoodMeta(node, depth, true, sb);
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!(node.getChildAt(i) instanceof EditNodeProperty prop)) {
                continue;
            }
            if (prop instanceof EditNodeAnnotation ann) {
                first = writeObjectLevelAnnotation(node, ann, depth, first, sb);
                continue;
            }
            first = writeFieldAnnotations(prop, depth, first, sb);
            first = writeProperty(prop, prop.getName(), false, depth, first, sb);
        }
    }

    /**
     * Writes an object level annotation (simple key or orphan composite anchor).
     */
    private static boolean writeObjectLevelAnnotation(EditNodeObject owner, EditNodeAnnotation ann, int depth,
            boolean first, StringBuilder sb) {
        return writeProperty(ann, ann.getName(), ann.isComposite(), depth, first, sb);
    }

    /**
     * Writes the annotation children of a field property as composite keys.
     */
    private static boolean writeFieldAnnotations(EditNodeProperty fieldProp, int depth, boolean first, StringBuilder sb) {
        for (int i = 0; i < fieldProp.getChildCount(); i++) {
            if (!(fieldProp.getChildAt(i) instanceof EditNodeAnnotation ann)) {
                continue;
            }
            // The composite target is structural: the display name of the
            // annotation already is the composite key.
            writeProperty(ann, ann.getName(), ann.isComposite(), depth, first, sb);
            first = false;
        }
        return first;
    }

    /**
     * Writes a property (or annotation) with the given key. Composite keys are quoted because of the separator; plain
     * keys stay unquoted in the wood style.
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
                if (child instanceof EditNodeAnnotation) {
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
            if (obj.getChildCount() == 0 && !obj.isLinkReference() && obj.getObjektId() == null) {
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
     * Writes a scalar row of an array. Rows carry no JSON node type in the tree, so the cast decides: a row parsed as
     * String is quoted, everything else is written raw when it looks like a number or a boolean and quoted otherwise.
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
     * Appends the JSON-escaped form of the given string.
     */
    private static void escape(String value, StringBuilder sb) {
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"' ->
                    sb.append("\\\"");
                case '\\' ->
                    sb.append("\\\\");
                case '\n' ->
                    sb.append("\n");
                case '\r' ->
                    sb.append("\r");
                case '\t' ->
                    sb.append("\t");
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
