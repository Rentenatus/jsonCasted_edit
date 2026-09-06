/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import java.util.Objects;

/**
 * Represents a single diagnostic result from validation.
 * Contains all information about a validation issue including severity, code, message,
 * affected node, and additional context data.
 *
 * @author Janusch Rentenatus
 */
public class EditNodeDiagnostic {
    
    private final Severity severity;
    private final String code;
    private final String message;
    private final EditNode sourceNode;
    private final JsonModelDescriptor modelContext;
    private final Object relatedData;
    
    /**
     * Creates a new diagnostic with the specified parameters.
     *
     * @param severity the severity level (ERROR, WARNING, INFO)
     * @param code the unique error code (e.g., "editnode.type.missing")
     * @param message human-readable message describing the issue
     * @param sourceNode the affected node that caused this diagnostic
     * @param modelContext the model descriptor context (optional)
     * @param relatedData additional data related to this diagnostic (optional)
     */
    public EditNodeDiagnostic(Severity severity, String code, String message, 
                              EditNode sourceNode, JsonModelDescriptor modelContext, Object relatedData) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.code = Objects.requireNonNull(code, "code");
        this.message = Objects.requireNonNull(message, "message");
        this.sourceNode = sourceNode;
        this.modelContext = modelContext;
        this.relatedData = relatedData;
    }
    
    /**
     * Creates a diagnostic with ERROR severity.
     */
    public static EditNodeDiagnostic error(String code, String message, EditNode sourceNode, 
                                           JsonModelDescriptor modelContext, Object relatedData) {
        return new EditNodeDiagnostic(Severity.ERROR, code, message, sourceNode, modelContext, relatedData);
    }
    
    /**
     * Creates a diagnostic with ERROR severity and no related data.
     */
    public static EditNodeDiagnostic error(String code, String message, EditNode sourceNode, 
                                           JsonModelDescriptor modelContext) {
        return error(code, message, sourceNode, modelContext, null);
    }
    
    /**
     * Creates a diagnostic with WARNING severity.
     */
    public static EditNodeDiagnostic warning(String code, String message, EditNode sourceNode, 
                                              JsonModelDescriptor modelContext, Object relatedData) {
        return new EditNodeDiagnostic(Severity.WARNING, code, message, sourceNode, modelContext, relatedData);
    }
    
    /**
     * Creates a diagnostic with WARNING severity and no related data.
     */
    public static EditNodeDiagnostic warning(String code, String message, EditNode sourceNode, 
                                              JsonModelDescriptor modelContext) {
        return warning(code, message, sourceNode, modelContext, null);
    }
    
    /**
     * Creates a diagnostic with INFO severity.
     */
    public static EditNodeDiagnostic info(String code, String message, EditNode sourceNode, 
                                          JsonModelDescriptor modelContext, Object relatedData) {
        return new EditNodeDiagnostic(Severity.INFO, code, message, sourceNode, modelContext, relatedData);
    }
    
    /**
     * Creates a diagnostic with INFO severity and no related data.
     */
    public static EditNodeDiagnostic info(String code, String message, EditNode sourceNode, 
                                          JsonModelDescriptor modelContext) {
        return info(code, message, sourceNode, modelContext, null);
    }
    
    /**
     * Returns the severity of this diagnostic.
     *
     * @return the severity level
     */
    public Severity getSeverity() {
        return severity;
    }
    
    /**
     * Returns the unique error code for this diagnostic.
     *
     * @return the error code
     */
    public String getCode() {
        return code;
    }
    
    /**
     * Returns the human-readable message for this diagnostic.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * Returns the source node that caused this diagnostic.
     *
     * @return the affected node, or null if not associated with a specific node
     */
    public EditNode getSourceNode() {
        return sourceNode;
    }
    
    /**
     * Returns the model context for this diagnostic.
     *
     * @return the model descriptor, or null if not set
     */
    public JsonModelDescriptor getModelContext() {
        return modelContext;
    }
    
    /**
     * Returns additional data related to this diagnostic.
     *
     * @return the related data, or null if not set
     */
    public Object getRelatedData() {
        return relatedData;
    }
    
    /**
     * Checks if this diagnostic represents an error.
     *
     * @return true if severity is ERROR
     */
    public boolean isError() {
        return severity == Severity.ERROR;
    }
    
    /**
     * Checks if this diagnostic represents a warning.
     *
     * @return true if severity is WARNING
     */
    public boolean isWarning() {
        return severity == Severity.WARNING;
    }
    
    /**
     * Checks if this diagnostic represents an info message.
     *
     * @return true if severity is INFO
     */
    public boolean isInfo() {
        return severity == Severity.INFO;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity).append("] ");
        sb.append(code);
        if (sourceNode != null) {
            sb.append(" at ").append(sourceNode.getName());
        }
        sb.append(": ").append(message);
        return sb.toString();
    }
}
