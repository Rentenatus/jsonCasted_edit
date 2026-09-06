/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core.validation;

import java.util.Objects;

/**
 * Core contributor that registers the standard validators for EditTree validation.
 * This contributor is automatically registered with EditValidationRunner.
 *
 * @author Janusch Rentenatus
 */
public class CoreEditValidatorContributor implements ValidatorContributor {
    
    /**
     * Creates a new core validator contributor.
     */
    public CoreEditValidatorContributor() {
    }
    
    @Override
    public void contribute(ValidatorRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        
        // Register node validators
        registry.addNodeValidator(new EditNodeObjectTypeValidator());
        registry.addNodeValidator(new EditNodePropertyFieldValidator());
        
        // Register tree validators
        registry.addTreeValidator(new EditTreeModelConsistencyValidator());
        registry.addTreeValidator(new EditNodeRequiredFieldsValidator());
        registry.addTreeValidator(new EditNodeTypeHierarchyValidator());
    }
}
