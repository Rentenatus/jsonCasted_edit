/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core.validation;

/**
 * Represents the severity level of a validation diagnostic.
 * Used to categorize validation results by their impact.
 *
 * @author Janusch Rentenatus
 */
public enum Severity {
    
    /**
     * Critical errors that prevent operations from executing.
     * Example: Type not found, field missing in parent type.
     */
    ERROR,
    
    /**
     * Warnings that indicate potential problems but don't block operations.
     * Example: Required field missing, type not fully compatible.
     */
    WARNING,
    
    /**
     * Informational messages that provide additional context.
     * Example: Type was matched perceptively, field has default value.
     */
    INFO
    
}
