/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Context object for validation operations.
 * Holds references to the tree, model descriptor, result, and current path during validation.
 *
 * @author Janusch Rentenatus
 */
public class ValidationContext {
    
    private final EditNodeAbstract rootNode;
    private final JsonModelDescriptor modelDescriptor;
    private final ValidationResult result;
    private final ArrayDeque<EditNode> path;
    
    /**
     * Creates a new validation context.
     *
     * @param rootNode the root node of the tree to validate
     * @param modelDescriptor the model descriptor to validate against
     * @param result the result object to collect diagnostics
     */
    public ValidationContext(EditNodeAbstract rootNode, 
                           JsonModelDescriptor modelDescriptor, 
                           ValidationResult result) {
        this.rootNode = Objects.requireNonNull(rootNode, "rootNode");
        this.modelDescriptor = modelDescriptor;
        this.result = Objects.requireNonNull(result, "result");
        this.path = new ArrayDeque<>();
    }
    
    /**
     * Returns the root node of the tree being validated.
     *
     * @return the root node
     */
    public EditNodeAbstract getRootNode() {
        return rootNode;
    }
    
    /**
     * Returns the model descriptor to validate against.
     *
     * @return the model descriptor, or null if not set
     */
    public JsonModelDescriptor getModelDescriptor() {
        return modelDescriptor;
    }
    
    /**
     * Returns the validation result to collect diagnostics.
     *
     * @return the validation result
     */
    public ValidationResult getResult() {
        return result;
    }
    
    /**
     * Returns the current path in the tree as a list.
     * The path represents the hierarchy from root to the current node.
     *
     * @return unmodifiable list of nodes representing the current path
     */
    public List<EditNode> getPath() {
        return Collections.unmodifiableList(new ArrayList<>(path));
    }
    
    /**
     * Returns the current path as a string representation.
     *
     * @return string representation of the path
     */
    public String getPathString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                sb.append(" -> ");
            }
            EditNode node = path.get(i);
            sb.append(node.getName());
        }
        return sb.toString();
    }
    
    /**
     * Pushes a node onto the path (when entering a node during traversal).
     *
     * @param node the node to push onto the path
     */
    public void pushPath(EditNode node) {
        Objects.requireNonNull(node, "node");
        path.push(node);
    }
    
    /**
     * Pops a node from the path (when leaving a node during traversal).
     *
     * @return the node that was popped
     */
    public EditNode popPath() {
        return path.poll();
    }
    
    /**
     * Returns the current node at the top of the path.
     *
     * @return the current node, or null if path is empty
     */
    public EditNode peekPath() {
        return path.peek();
    }
    
    /**
     * Clears the current path.
     */
    public void clearPath() {
        path.clear();
    }
    
    /**
     * Adds a diagnostic to the result with the current path information.
     *
     * @param diagnostic the diagnostic to add
     */
    public void addDiagnostic(EditNodeDiagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        result.add(diagnostic);
    }
    
    /**
     * Convenience method to add an error diagnostic.
     *
     * @param code the error code
     * @param message the error message
     * @param sourceNode the source node
     */
    public void addError(String code, String message, EditNode sourceNode) {
        EditNodeDiagnostic diagnostic = EditNodeDiagnostic.error(code, message, sourceNode, modelDescriptor);
        addDiagnostic(diagnostic);
    }
    
    /**
     * Convenience method to add a warning diagnostic.
     *
     * @param code the warning code
     * @param message the warning message
     * @param sourceNode the source node
     */
    public void addWarning(String code, String message, EditNode sourceNode) {
        EditNodeDiagnostic diagnostic = EditNodeDiagnostic.warning(code, message, sourceNode, modelDescriptor);
        addDiagnostic(diagnostic);
    }
    
    /**
     * Convenience method to add an info diagnostic.
     *
     * @param code the info code
     * @param message the info message
     * @param sourceNode the source node
     */
    public void addInfo(String code, String message, EditNode sourceNode) {
        EditNodeDiagnostic diagnostic = EditNodeDiagnostic.info(code, message, sourceNode, modelDescriptor);
        addDiagnostic(diagnostic);
    }
}
