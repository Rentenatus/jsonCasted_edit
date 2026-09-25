/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core.validation;

/**
 * Interface for contributing validators to a registry.
 * Allows modular extension of the validation framework.
 *
 * @author Janusch Rentenatus
 */
public interface ValidatorContributor {
    
    /**
     * Contributes validators to the specified registry.
     * Implementations should call the appropriate add methods on the registry.
     *
     * @param registry the validator registry to contribute to
     */
    void contribute(ValidatorRegistry registry);
}
