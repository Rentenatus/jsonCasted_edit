/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditNodeAbstract;

/**
 * Interface for validators that validate individual EditNode instances.
 * Node validators are applied to each node in the tree during validation.
 *
 * @author Janusch Rentenatus
 */
public interface EditNodeValidator {
    
    /**
     * Validates the specified node and adds any diagnostics to the context.
     *
     * @param node the node to validate
     * @param context the validation context for collecting diagnostics
     */
    void validate(EditNodeAbstract node, ValidationContext context);
    
    /**
     * Returns a unique identifier for this validator.
     *
     * @return the validator ID
     */
    default String getId() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * Returns a description of what this validator checks.
     *
     * @return the validator description
     */
    default String getDescription() {
        return "Validates " + getClass().getSimpleName();
    }
}
