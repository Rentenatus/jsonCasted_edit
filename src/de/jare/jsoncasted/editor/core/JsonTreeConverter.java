/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.lang.JsonNode;
import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.lang.JsonResource;
import de.jare.jsoncasted.lang.JsonTerms;
import de.jare.jsoncasted.parserwriter.JsonParseException;
import de.jare.jsoncasted.parserservice.JsonParserService;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Utility class for converting between JSON data structures and EditTree
 * representations. Provides methods to create EditTree structures from JSON
 * files, strings, and JsonResource objects. Uses JsonParserService from the
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
        String rootName = file.getName();
        int dotIndex = rootName.lastIndexOf('.');
        if (dotIndex > 0) {
            rootName = rootName.substring(0, dotIndex);
        }
        JsonResource resource = JsonParserService.parse(file, JsonDebugLevel.SIMPLE);
        if (resource == null) {
            throw new IOException("Failed to parse file: " + file.getAbsolutePath());
        }
        return convertRessourceToEditTree(resource, rootName);
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
        return convertRessourceToEditTree(resource, rootName);
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
     * @param weightMonitor the EditTimes monitor for tracking tree construction
     * metrics
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
     * Converts a JSON node to an EditNode structure and adds it as children to
     * the root node. Handles object values by creating EditProperty nodes for
     * each entry.
     *
     * @param rootNode the root EditNodeObject to which child nodes will be
     * added
     * @param jsonNode the JSON node to convert
     * @param weightMonitor the EditTimes monitor for tracking tree construction
     * metrics
     * @throws JsonParseException if JSON parsing fails during conversion
     */
    private static void convertJsonNodeToEditNode(EditNodeObject rootNode, JsonNode jsonNode, EditTimes weightMonitor) throws JsonParseException {
        Map<String, JsonNode> objectValues = jsonNode.asObjectValues();
        if (objectValues != null) {
            for (Map.Entry<String, JsonNode> entry : objectValues.entrySet()) {
                if (JsonTerms.TERM_WOOD_PROVIDERS.equals(entry.getKey())) {
                    continue;
                }
                buildEditProperty(rootNode, entry, weightMonitor);
            }
        }
    }

    /**
     * Builds an EditProperty node from a JSON object entry and adds it to the
     * parent node. Handles different JSON node types (array, object, primitive
     * values).
     *
     * @param parent the parent EditNodeObject to which the property will be
     * added
     * @param entry the map entry containing the property name and JSON node
     * value
     * @param weightMonitor the EditTimes monitor for tracking tree construction
     * metrics
     * @throws JsonParseException if JSON parsing fails during property
     * construction
     */
    private static void buildEditProperty(EditNodeObject parent, Map.Entry<String, JsonNode> entry,
            EditTimes weightMonitor) throws JsonParseException {
        String propertyName = entry.getKey();
        EditNodeProperty editNode = new EditNodeProperty(propertyName != null ? propertyName : ".");
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
     * Builds an EditNode structure from a JSON node and adds it as a child to
     * the parent property node. Handles different JSON node types (object,
     * array, primitive values) appropriately.
     *
     * @param parent the parent EditNodeProperty to which the node will be added
     * @param jsonNode the JSON node to convert to an EditNode
     * @param weightMonitor the EditTimes monitor for tracking tree construction
     * metrics
     * @throws JsonParseException if JSON parsing fails during construction
     */
    private static void buildEditObject(EditNodeProperty parent, JsonNode jsonNode,
            EditTimes weightMonitor) throws JsonParseException {
        System.out.println("1 +++++++++++++++   " + jsonNode);
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
