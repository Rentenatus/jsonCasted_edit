/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Main entry point for validation operations on EditTree.
 * Coordinates the execution of all registered validators and collects results.
 *
 * @author Janusch Rentenatus
 */
public class EditValidationRunner {
    
    private final List<ValidatorContributor> contributors = new ArrayList<>();
    
    /**
     * Creates a new validation runner with the core validators.
     */
    public EditValidationRunner() {
        contributors.add(new CoreEditValidatorContributor());
    }
    
    /**
     * Adds a validator contributor to this runner.
     *
     * @param contributor the contributor to add
     * @return this runner for method chaining
     */
    public EditValidationRunner addContributor(ValidatorContributor contributor) {
        Objects.requireNonNull(contributor, "contributor");
        if (!contributors.contains(contributor)) {
            contributors.add(contributor);
        }
        return this;
    }
    
    /**
     * Validates the entire tree against the specified model descriptor.
     *
     * @param tree the tree to validate
     * @param descriptor the model descriptor to validate against
     * @return the validation result containing all diagnostics
     */
    public ValidationResult validate(EditTree tree, JsonModelDescriptor descriptor) {
        Objects.requireNonNull(tree, "tree");
        
        // If no descriptor is provided, we can only do limited validation
        if (descriptor == null) {
            return validateWithoutModel(tree);
        }
        
        // Build the validator registry
        ValidatorRegistry registry = buildRegistry();
        
        // Create context
        ValidationResult result = new ValidationResult();
        ValidationContext context = new ValidationContext(tree.getRoot(), descriptor, result);
        
        // Validate all nodes recursively
        validateNodesRecursive(tree.getRoot(), registry, context);
        
        // Apply tree validators
        for (EditTreeValidator validator : registry.getTreeValidators()) {
            validator.validate(tree, context);
        }
        
        return result;
    }
    
    /**
     * Validates a single node against the model descriptor.
     *
     * @param node the node to validate
     * @param descriptor the model descriptor to validate against
     * @return the validation result for this node
     */
    public ValidationResult validateSingleNode(EditNodeAbstract node, JsonModelDescriptor descriptor) {
        Objects.requireNonNull(node, "node");
        
        if (descriptor == null) {
            return new ValidationResult(); // No model, no validation possible
        }
        
        ValidatorRegistry registry = buildRegistry();
        ValidationResult result = new ValidationResult();
        ValidationContext context = new ValidationContext(node, descriptor, result);
        
        // Create a temporary path with just this node
        context.pushPath(node);
        
        // Apply all node validators to this single node
        for (EditNodeValidator validator : registry.getNodeValidators()) {
            validator.validate(node, context);
        }
        
        return result;
    }
    
    /**
     * Validates a subtree starting from the specified root node.
     *
     * @param rootNode the root of the subtree to validate
     * @param descriptor the model descriptor to validate against
     * @return the validation result for the subtree
     */
    public ValidationResult validateSubtree(EditNodeAbstract rootNode, JsonModelDescriptor descriptor) {
        Objects.requireNonNull(rootNode, "rootNode");
        
        if (descriptor == null) {
            return validateSubtreeWithoutModel(rootNode);
        }
        
        ValidatorRegistry registry = buildRegistry();
        ValidationResult result = new ValidationResult();
        ValidationContext context = new ValidationContext(rootNode, descriptor, result);
        
        // Validate the subtree recursively
        validateNodesRecursive(rootNode, registry, context);
        
        return result;
    }
    
    /**
     * Builds the validator registry by collecting contributors from all registered contributors.
     *
     * @return the populated validator registry
     */
    private ValidatorRegistry buildRegistry() {
        ValidatorRegistry registry = new ValidatorRegistry();
        for (ValidatorContributor contributor : contributors) {
            contributor.contribute(registry);
        }
        return registry;
    }
    
    /**
     * Recursively validates all nodes in the tree.
     *
     * @param node the current node to validate
     * @param registry the validator registry
     * @param context the validation context
     */
    private void validateNodesRecursive(EditNodeAbstract node, ValidatorRegistry registry, ValidationContext context) {
        // Track path
        context.pushPath(node);
        
        try {
            // Apply all node validators
            for (EditNodeValidator validator : registry.getNodeValidators()) {
                validator.validate(node, context);
            }
            
            // Recursively validate children
            for (int i = 0; i < node.getChildCount(); i++) {
                EditNode child = node.getChildAt(i);
                if (child instanceof EditNodeAbstract) {
                    validateNodesRecursive((EditNodeAbstract) child, registry, context);
                }
            }
        } finally {
            // Always pop the path when done
            context.popPath();
        }
    }
    
    /**
     * Validates a tree without a model descriptor (limited validation).
     * Only structural validators that don't require a model can run.
     *
     * @param tree the tree to validate
     * @return the validation result
     */
    private ValidationResult validateWithoutModel(EditTree tree) {
        ValidationResult result = new ValidationResult();
        
        // Without a model, we can only do limited validation
        // For now, just return empty result
        // Could be extended to do structural validation
        
        return result;
    }
    
    /**
     * Validates a subtree without a model descriptor.
     *
     * @param rootNode the root of the subtree
     * @return the validation result
     */
    private ValidationResult validateSubtreeWithoutModel(EditNodeAbstract rootNode) {
        // Similar to validateWithoutModel but for a subtree
        return new ValidationResult();
    }
    
    /**
     * Removes a contributor from this runner.
     *
     * @param contributor the contributor to remove
     * @return true if the contributor was removed
     */
    public boolean removeContributor(ValidatorContributor contributor) {
        return contributors.remove(contributor);
    }
    
    /**
     * Clears all contributors from this runner.
     */
    public void clearContributors() {
        contributors.clear();
    }
    
    /**
     * Returns the number of registered contributors.
     *
     * @return the contributor count
     */
    public int getContributorCount() {
        return contributors.size();
    }
    
    @Override
    public String toString() {
        return "EditValidationRunner["
                + "contributors=" + contributors.size()
                + "]";
    }
}
