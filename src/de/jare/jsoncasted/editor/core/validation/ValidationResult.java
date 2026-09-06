/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents the result of a validation operation.
 * Collects all diagnostics and provides convenient methods to query the validation state.
 *
 * @author Janusch Rentenatus
 */
public class ValidationResult {
    
    private final List<EditNodeDiagnostic> diagnostics = new ArrayList<>();
    
    /**
     * Creates a new empty validation result.
     */
    public ValidationResult() {
    }
    
    /**
     * Adds a diagnostic to this result.
     *
     * @param diagnostic the diagnostic to add
     */
    public void add(EditNodeDiagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        diagnostics.add(diagnostic);
    }
    
    /**
     * Adds all diagnostics from another result to this one.
     *
     * @param other the other result whose diagnostics to add
     */
    public void addAll(ValidationResult other) {
        Objects.requireNonNull(other, "other");
        this.diagnostics.addAll(other.diagnostics);
    }
    
    /**
     * Checks if this result contains any errors.
     *
     * @return true if there are any diagnostics with ERROR severity
     */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(EditNodeDiagnostic::isError);
    }
    
    /**
     * Checks if this result contains any warnings.
     *
     * @return true if there are any diagnostics with WARNING severity
     */
    public boolean hasWarnings() {
        return diagnostics.stream().anyMatch(EditNodeDiagnostic::isWarning);
    }
    
    /**
     * Checks if this result contains any info messages.
     *
     * @return true if there are any diagnostics with INFO severity
     */
    public boolean hasInfo() {
        return diagnostics.stream().anyMatch(EditNodeDiagnostic::isInfo);
    }
    
    /**
     * Checks if the validation was successful (no errors).
     *
     * @return true if there are no errors
     */
    public boolean isValid() {
        return !hasErrors();
    }
    
    /**
     * Returns all diagnostics in this result.
     *
     * @return unmodifiable list of all diagnostics
     */
    public List<EditNodeDiagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }
    
    /**
     * Returns all error diagnostics.
     *
     * @return list of error diagnostics
     */
    public List<EditNodeDiagnostic> getErrors() {
        return diagnostics.stream()
                .filter(EditNodeDiagnostic::isError)
                .collect(Collectors.toList());
    }
    
    /**
     * Returns all warning diagnostics.
     *
     * @return list of warning diagnostics
     */
    public List<EditNodeDiagnostic> getWarnings() {
        return diagnostics.stream()
                .filter(EditNodeDiagnostic::isWarning)
                .collect(Collectors.toList());
    }
    
    /**
     * Returns all info diagnostics.
     *
     * @return list of info diagnostics
     */
    public List<EditNodeDiagnostic> getInfos() {
        return diagnostics.stream()
                .filter(EditNodeDiagnostic::isInfo)
                .collect(Collectors.toList());
    }
    
    /**
     * Returns diagnostics for a specific node.
     *
     * @param node the node to get diagnostics for
     * @return list of diagnostics for the specified node
     */
    public List<EditNodeDiagnostic> getByNode(EditNode node) {
        Objects.requireNonNull(node, "node");
        return diagnostics.stream()
                .filter(d -> node.equals(d.getSourceNode()))
                .collect(Collectors.toList());
    }
    
    /**
     * Returns diagnostics filtered by severity.
     *
     * @param severity the severity to filter by
     * @return list of diagnostics with the specified severity
     */
    public List<EditNodeDiagnostic> getBySeverity(Severity severity) {
        Objects.requireNonNull(severity, "severity");
        return diagnostics.stream()
                .filter(d -> d.getSeverity() == severity)
                .collect(Collectors.toList());
    }
    
    /**
     * Returns diagnostics grouped by node.
     *
     * @return map from node to list of diagnostics for that node
     */
    public Map<EditNode, List<EditNodeDiagnostic>> getDiagnosticsByNode() {
        Map<EditNode, List<EditNodeDiagnostic>> result = new HashMap<>();
        for (EditNodeDiagnostic diagnostic : diagnostics) {
            EditNode node = diagnostic.getSourceNode();
            if (node != null) {
                result.computeIfAbsent(node, k -> new ArrayList<>()).add(diagnostic);
            }
        }
        return result;
    }
    
    /**
     * Returns the total count of diagnostics.
     *
     * @return the number of diagnostics
     */
    public int getTotalCount() {
        return diagnostics.size();
    }
    
    /**
     * Returns the count of error diagnostics.
     *
     * @return the number of errors
     */
    public int getErrorCount() {
        return (int) diagnostics.stream().filter(EditNodeDiagnostic::isError).count();
    }
    
    /**
     * Returns the count of warning diagnostics.
     *
     * @return the number of warnings
     */
    public int getWarningCount() {
        return (int) diagnostics.stream().filter(EditNodeDiagnostic::isWarning).count();
    }
    
    /**
     * Clears all diagnostics from this result.
     */
    public void clear() {
        diagnostics.clear();
    }
    
    /**
     * Checks if this result has no diagnostics.
     *
     * @return true if there are no diagnostics
     */
    public boolean isEmpty() {
        return diagnostics.isEmpty();
    }
    
    @Override
    public String toString() {
        return "ValidationResult["
                + "total=" + getTotalCount()
                + ", errors=" + getErrorCount()
                + ", warnings=" + getWarningCount()
                + ", valid=" + isValid()
                + "]";
    }
}
