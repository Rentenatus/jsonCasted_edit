/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Registry for collecting and managing validators.
 * Allows registration of both node validators and tree validators.
 *
 * @author Janusch Rentenatus
 */
public class ValidatorRegistry {
    
    private final List<EditNodeValidator> nodeValidators = new ArrayList<>();
    private final List<EditTreeValidator> treeValidators = new ArrayList<>();
    
    /**
     * Creates a new empty validator registry.
     */
    public ValidatorRegistry() {
    }
    
    /**
     * Adds a node validator to this registry.
     *
     * @param validator the node validator to add
     * @return this registry for method chaining
     */
    public ValidatorRegistry addNodeValidator(EditNodeValidator validator) {
        Objects.requireNonNull(validator, "validator");
        if (!nodeValidators.contains(validator)) {
            nodeValidators.add(validator);
        }
        return this;
    }
    
    /**
     * Adds a tree validator to this registry.
     *
     * @param validator the tree validator to add
     * @return this registry for method chaining
     */
    public ValidatorRegistry addTreeValidator(EditTreeValidator validator) {
        Objects.requireNonNull(validator, "validator");
        if (!treeValidators.contains(validator)) {
            treeValidators.add(validator);
        }
        return this;
    }
    
    /**
     * Removes a node validator from this registry.
     *
     * @param validator the node validator to remove
     * @return true if the validator was removed
     */
    public boolean removeNodeValidator(EditNodeValidator validator) {
        return nodeValidators.remove(validator);
    }
    
    /**
     * Removes a tree validator from this registry.
     *
     * @param validator the tree validator to remove
     * @return true if the validator was removed
     */
    public boolean removeTreeValidator(EditTreeValidator validator) {
        return treeValidators.remove(validator);
    }
    
    /**
     * Returns all registered node validators.
     *
     * @return unmodifiable list of node validators
     */
    public List<EditNodeValidator> getNodeValidators() {
        return Collections.unmodifiableList(nodeValidators);
    }
    
    /**
     * Returns all registered tree validators.
     *
     * @return unmodifiable list of tree validators
     */
    public List<EditTreeValidator> getTreeValidators() {
        return Collections.unmodifiableList(treeValidators);
    }
    
    /**
     * Returns the number of node validators.
     *
     * @return the count of node validators
     */
    public int getNodeValidatorCount() {
        return nodeValidators.size();
    }
    
    /**
     * Returns the number of tree validators.
     *
     * @return the count of tree validators
     */
    public int getTreeValidatorCount() {
        return treeValidators.size();
    }
    
    /**
     * Checks if any node validators are registered.
     *
     * @return true if at least one node validator is registered
     */
    public boolean hasNodeValidators() {
        return !nodeValidators.isEmpty();
    }
    
    /**
     * Checks if any tree validators are registered.
     *
     * @return true if at least one tree validator is registered
     */
    public boolean hasTreeValidators() {
        return !treeValidators.isEmpty();
    }
    
    /**
     * Clears all validators from this registry.
     */
    public void clear() {
        nodeValidators.clear();
        treeValidators.clear();
    }
    
    @Override
    public String toString() {
        return "ValidatorRegistry["
                + "nodeValidators=" + nodeValidators.size()
                + ", treeValidators=" + treeValidators.size()
                + "]";
    }
}
