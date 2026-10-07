/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.JsonParser;
import de.jare.jsoncasted.io.convertservice.WoodElementResolver;
import de.jare.jsoncasted.io.convertservice.WoodResolution;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.item.builder.JsonBuilder;
import de.jare.jsoncasted.lang.JsonNode;
import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.lang.JsonResource;
import de.jare.jsoncasted.lang.JsonTerms;
import static de.jare.jsoncasted.lang.JsonTerms.TERM_CLASS;
import static de.jare.jsoncasted.lang.JsonTerms.TERM_WOOD_LINK;
import static de.jare.jsoncasted.lang.JsonTerms.TERM_WOOD_MODEL;
import static de.jare.jsoncasted.lang.JsonTerms.TERM_WOOD_OBJECT_ID;
import static de.jare.jsoncasted.lang.JsonTerms.TERM_WOOD_PROVIDERS;
import de.jare.jsoncasted.model.JsonBuildException;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import de.jare.jsoncasted.model.descriptor.def.JsonModelDescriptorDefinition;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for converting between JSON data structures and EditTree representations. Provides methods to create
 * EditTree structures from JSON files, strings, and JsonResource objects. Uses JsonParserService from the
 * jsoncasted.parserservice package for parsing operations.
 *
 * @author Janusch Rentenatus
 */
public final class JsonTreeConverter {

    private JsonTreeConverter() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Creates an EditTree from a JSON file.
     *
     * @param file the JSON file to load
     * @return a new EditTree containing the JSON content
     * @throws IOException if the file cannot be read
     * @throws JsonParseException if JSON parsing fails
     */
    public static EditTree fromJsonFile(File file) throws IOException, JsonParseException {
        JsonResource resource = JsonParserService.parse(file, JsonDebugLevel.SIMPLE);
        if (resource == null) {
            throw new IOException("Failed to parse file: " + file.getAbsolutePath());
        }
        final String rootName = resolveRootName(resource);
        final EditTree retEditTree = convertRessourceToEditTree(resource, rootName);
        retEditTree.setProviderName(rootName);

        String descriptionFilePath = WoodElementResolver.extractDescriptionFilePath(resource);
        File descriptionFile = WoodElementResolver.findDescriptionFile(descriptionFilePath, file);
        return loadDescrAndConvertRessourceToEditTree(retEditTree, descriptionFilePath, descriptionFile);
    }

    /**
     * Derives the display name for the root node from the provider name of the given resource. The main resource
     * carries no provider alias of its own and therefore defaults to {@code this}.
     *
     * @param resource the parsed JSON resource
     * @return the provider name, or {@code this} when the resource has none
     */
    private static String resolveRootName(JsonResource resource) {
        String providerName = resource.getProviderName();
        if (providerName == null || providerName.isBlank()
                || JsonTerms.SELF_SYNONYM.equals(providerName)) {
            return JsonTerms.THIS_SYNONYM;
        }
        return providerName;
    }

    /**
     * Creates an EditTree from a JSON string.
     *
     * @param jsonString the JSON string to parse
     * @param rootName the name to use for the root node of the EditTree
     * @return a new EditTree containing the JSON content
     * @throws IOException if parsing fails
     * @throws JsonParseException if JSON parsing fails
     */
    public static EditTree fromJsonString(String jsonString, String rootName) throws IOException, JsonParseException {
        JsonResource resource = JsonParserService.parse(jsonString, JsonDebugLevel.SIMPLE);
        if (resource == null) {
            throw new IOException("Failed to parse JSON string");
        }
        final EditTree retEditTree = convertRessourceToEditTree(resource, rootName);

        String descriptionFilePath = WoodElementResolver.extractDescriptionFilePath(resource);
        File descriptionFile = new File(descriptionFilePath);
        return loadDescrAndConvertRessourceToEditTree(retEditTree, descriptionFilePath, descriptionFile);
    }

    /**
     *
     * @param descriptionFilePath
     * @param descriptionFile
     * @param retEditTree
     * @return
     * @throws JsonParseException
     */
    public static EditTree loadDescrAndConvertRessourceToEditTree(EditTree retEditTree, String descriptionFilePath, File descriptionFile) throws JsonParseException {
        JsonModelDescriptor descriptor = null;
        if (descriptionFile != null) {
            try {
                JsonModelDescriptorDefinition definition = JsonModelDescriptorDefinition.getInstance();
                WoodResolution resolution = JsonParser.parse(descriptionFile, definition, definition.getRootClass());
                descriptor = (JsonModelDescriptor) JsonBuilder.buildInstance(definition.getModel(), false, resolution.getAnswer());
            } catch (JsonParseException | JsonBuildException | IOException | NullPointerException | ClassCastException ex) {
                final String failedMsg = "Load JsonModelDescriptor failed. ";
                Logger.getGlobal().log(Level.SEVERE, failedMsg, ex);
                final EditNodeAbstract root = retEditTree.getRoot();
                if (root != null) {
                    root.setEditMessage(failedMsg + ex.getMessage());
                    root.setEditStatus(EditStatus.ERROR);
                }
            }
        }
        retEditTree.setJsonModelDescriptor(descriptor);
        retEditTree.setDescriptionFilePath(descriptionFilePath);

        // Wenn der Root-Knoten noch keinen castName hat (kein _class im JSON),
        // versuchen wir hier, den Typ ueber den Deskriptor aufzuloesen.
        // Der Root-Name ist der Dateiname ohne Extension.
        if (descriptor != null) {
            EditNodeAbstract root = retEditTree.getRoot();
            if (root instanceof EditNodeObject rootObj
                    && (rootObj.getCastName() == null || rootObj.getCastName().isEmpty())) {
                String rootCast = descriptor.getRootNodeCast();
                if (rootCast != null && !rootCast.isEmpty()) {
                    JsonTypeDescriptor rootType = descriptor.getType(rootCast);
                    if (rootType == null) {
                        rootType = descriptor.getTypePerceptive(rootCast);
                    }
                    if (rootType != null) {
                        // Wire the root type so the parser's root fallback can
                        // assign it when the root carries no cast name.
                        retEditTree.setRootType(rootType);
                        rootObj.setCastName(rootType.getTypeName());
                    }
                }
            }
        }

        return retEditTree;
    }

    /**
     * Converts a JsonResource to an EditTree structure.
     *
     * @param resource the JsonResource containing the parsed JSON data
     * @param rootName the name to use for the root node of the EditTree
     * @return a new EditTree containing the JSON content from the resource
     * @throws JsonParseException if JSON parsing fails during conversion
     */
    public static EditTree convertRessourceToEditTree(JsonResource resource, String rootName) throws JsonParseException {
        EditTimes weightMonitor = new EditTimes();
        EditNodeAbstract root = importFromJsonNode(resource.getRoot(), rootName, weightMonitor);
        return new EditTree(root, weightMonitor);
    }

    /**
     * Imports a JSON node into an EditNode structure.
     *
     * @param jsonNode the JSON node to import
     * @param rootName the name to use for the root node
     * @param weightMonitor the EditTimes monitor for tracking tree construction metrics
     * @return the root EditNode containing the imported JSON structure
     * @throws JsonParseException if JSON parsing fails during import
     */
    public static EditNodeAbstract importFromJsonNode(JsonNode jsonNode, String rootName, EditTimes weightMonitor) throws JsonParseException {
        if (jsonNode == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        EditNodeObject rootNode = new EditNodeObject(rootName);
        convertJsonNodeToEditNode(rootNode, jsonNode, weightMonitor);
        return rootNode;
    }

    /**
     * Converts a JSON node to an EditNode structure and adds it as children to the root node. Handles object values by
     * creating EditProperty nodes for each entry.
     * <p>
     * Metadata keys defined in {@link JsonTerms} (e.g. {@code _class}, {@code _woodObjectId}, {@code _woodLink},
     * {@code _woodModel}, {@code _woodProviders}) are filtered out and not added as child nodes. If a {@code _class}
     * entry is present, its value is stored as the {@link EditNodeObject#setCastName(String) castName} on the root node
     * so the parser can resolve the type via O(1) cache lookup on the first parse pass.
     * </p>
     *
     * @param rootNode the root EditNodeObject to which child nodes will be added
     * @param jsonNode the JSON node to convert
     * @param weightMonitor the EditTimes monitor for tracking tree construction metrics
     * @throws JsonParseException if JSON parsing fails during conversion
     */
    private static void convertJsonNodeToEditNode(EditNodeObject rootNode, JsonNode jsonNode, EditTimes weightMonitor) throws JsonParseException {
        Map<String, JsonNode> objectValues = jsonNode.asObjectValues();
        List<Map.Entry<String, JsonNode>> deferredAnnotations = null;
        if (objectValues != null) {
            for (Map.Entry<String, JsonNode> entry : objectValues.entrySet()) {
                String key = entry.getKey();
                // Filter metadata keys — these are structural and should not
                // appear as editable child nodes in the tree.
                if (isMetadataKey(key)) {
                    // If the key is _class, store the value as castName on
                    // the parent EditNodeObject so the parser can use it as
                    // a cache for O(1) type resolution on the first parse.
                    if (TERM_CLASS.equals(key)) {
                        JsonNode classNode = entry.getValue();
                        if (classNode != null) {
                            String castName = classNode.asText();
                            if (castName != null && !castName.isEmpty()) {
                                rootNode.setCastName(castName);
                            }
                        }
                    }
                    continue;
                }
                if (AnnotationKeys.isCompositeKey(key)) {
                    // Composite annotation keys are resolved after the object's
                    // fields are built: the annotation moves under its target
                    // field node, so iteration order does not matter.
                    if (deferredAnnotations == null) {
                        deferredAnnotations = new ArrayList<>();
                    }
                    deferredAnnotations.add(entry);
                    continue;
                }
                buildEditProperty(rootNode, entry, weightMonitor);
            }
        }
        if (deferredAnnotations != null) {
            for (Map.Entry<String, JsonNode> entry : deferredAnnotations) {
                resolveCompositeAnnotation(rootNode, entry, weightMonitor);
            }
        }
    }

    /**
     * Checks whether a JSON property name is a metadata key that should be filtered out during tree construction, not
     * added as an editable child.
     *
     * @param key the property name to check
     * @return true if the key is a reserved metadata term
     */
    private static boolean isMetadataKey(String key) {
        return TERM_WOOD_PROVIDERS.equals(key)
                || TERM_CLASS.equals(key)
                || TERM_WOOD_OBJECT_ID.equals(key)
                || TERM_WOOD_LINK.equals(key)
                || TERM_WOOD_MODEL.equals(key);
    }

    /**
     * Builds an EditProperty node from a JSON object entry and adds it to the parent node. Handles different JSON node
     * types (array, object, primitive values).
     *
     * @param parent the parent EditNodeObject to which the property will be added
     * @param entry the map entry containing the property name and JSON node value
     * @param weightMonitor the EditTimes monitor for tracking tree construction metrics
     * @throws JsonParseException if JSON parsing fails during property construction
     */
    private static void buildEditProperty(EditNodeObject parent, Map.Entry<String, JsonNode> entry,
            EditTimes weightMonitor) throws JsonParseException {
        buildEditProperty(parent, entry.getKey(), entry, weightMonitor);
    }

    /**
     * Builds an EditProperty node under the given parent, using the given property name instead of the entry key (used
     * for composite annotations, which land under their target field node with their plain annotation name).
     *
     * @param parent the parent node (object or property) to which the property will be added
     * @param propertyName the property name to use for the node
     * @param entry the map entry containing the property name and JSON node value
     * @param weightMonitor the EditTimes monitor for tracking tree construction metrics
     * @throws JsonParseException if JSON parsing fails during property construction
     */
    private static void buildEditProperty(EditNodeAbstract parent, String propertyName, Map.Entry<String, JsonNode> entry,
            EditTimes weightMonitor) throws JsonParseException {
        // An @ key is an annotation: the node class carries the kind, the
        // name stays plain and the composite target is kept as its own field.
        final EditNodeProperty editNode = AnnotationKeys.isAnnotationKey(propertyName)
                ? new EditNodeAnnotation(AnnotationKeys.annotationName(propertyName))
                : new EditNodeProperty(propertyName != null ? propertyName : ".");
        parent.addChild(editNode, weightMonitor);
        JsonNode jsonNode = entry.getValue();
        JsonNodeType type = jsonNode.getType();
        editNode.setType(type);
        if (type == JsonNodeType.ARRAY) {
            List<JsonNode> arrayValues = jsonNode.asArray();
            if (arrayValues != null) {
                for (JsonNode value : arrayValues) {
                    buildEditObject(editNode, value, weightMonitor);
                }
            }
            return;
        }
        if (type == JsonNodeType.OBJECT) {
            buildEditObject(editNode, jsonNode, weightMonitor);
            return;
        }
        editNode.setValue(convertJsonValueToString(jsonNode));
    }

    /**
     * Resolves a composite annotation key ({@code @doc:profile}): when the target field property exists under the
     * object, the annotation is built as a child of that field node. Without the target field the annotation falls back
     * to a simple object level annotation - the composite target is structural and derived from the anchor position.
     *
     * @param parent the object node holding the annotation entry
     * @param entry the deferred annotation entry
     * @param weightMonitor the EditTimes monitor for tracking tree construction metrics
     * @throws JsonParseException if JSON parsing fails during property construction
     */
    private static void resolveCompositeAnnotation(EditNodeObject parent, Map.Entry<String, JsonNode> entry,
            EditTimes weightMonitor) throws JsonParseException {
        final String target = AnnotationKeys.targetField(entry.getKey());
        for (int i = 0; i < parent.getChildCount(); i++) {
            final EditNode child = parent.getChildAt(i);
            if (child instanceof EditNodeProperty fieldProp && target.equals(child.getName())) {
                buildEditProperty(fieldProp, entry.getKey(), entry, weightMonitor);
                return;
            }
        }
        buildEditProperty(parent, entry, weightMonitor);
    }

    /**
     * Builds an EditNode structure from a JSON node and adds it as a child to the parent property node. Handles
     * different JSON node types (object, array, primitive values) appropriately.
     *
     * @param parent the parent EditNodeProperty to which the node will be added
     * @param jsonNode the JSON node to convert to an EditNode
     * @param weightMonitor the EditTimes monitor for tracking tree construction metrics
     * @throws JsonParseException if JSON parsing fails during construction
     */
    private static void buildEditObject(EditNodeProperty parent, JsonNode jsonNode,
            EditTimes weightMonitor) throws JsonParseException {
        JsonNodeType type = jsonNode.getType();

        if (type == JsonNodeType.OBJECT) {
            EditNodeObject ndoeObject = new EditNodeObject("Object", "{...}");
            convertJsonNodeToEditNode(ndoeObject, jsonNode, weightMonitor);
            parent.addChild(ndoeObject, weightMonitor);
            return;
        }
        if (type == JsonNodeType.ARRAY) {
            EditNodePropertyArr nodeArray = new EditNodePropertyArr();
            parent.addChild(nodeArray, weightMonitor);
            List<JsonNode> arrayValues = jsonNode.asArray();
            if (arrayValues != null) {
                for (JsonNode value : arrayValues) {
                    buildEditObject(nodeArray, value, weightMonitor);
                }
            }
            return;
        }
        EditNodeObject nodePrimitiv = new EditNodeObject(jsonNode.toText());
        parent.addChild(nodePrimitiv, weightMonitor);
    }

    /**
     * Converts a primitive JSON node value to its string representation.
     *
     * @param jsonNode the primitive JSON node
     * @return the string value, or null for JSON null
     */
    private static String convertJsonValueToString(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        JsonNodeType type = jsonNode.getType();
        if (type == JsonNodeType.STRING) {
            return jsonNode.asText();
        }
        if (type == JsonNodeType.LONG) {
            return String.valueOf(jsonNode.asLong());
        }
        if (type == JsonNodeType.NUMBER) {
            return String.valueOf(jsonNode.asNumber());
        }
        if (type == JsonNodeType.BOOLEAN) {
            return String.valueOf(jsonNode.asBoolean());
        }
        if (type == JsonNodeType.NULL) {
            return null;
        }
        return jsonNode.asText();
    }
}
