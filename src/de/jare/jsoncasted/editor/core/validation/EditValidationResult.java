/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.validation.core.Severity;
import de.jare.jsoncasted.validation.core.ValidationResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents the result of an edit node validation. Extends the generic ValidationResult of the core with
 * node-specific queries; counting, severity checks and pretty printing are inherited.
 *
 * @author Janusch Rentenatus
 */
public class EditValidationResult extends ValidationResult<EditNodeDiagnostic> {

    /**
     * Creates a new empty validation result.
     */
    public EditValidationResult() {
    }

    /**
     * Adds all diagnostics from another result to this one.
     *
     * @param other the other result whose diagnostics to add
     */
    public void addAll(EditValidationResult other) {
        Objects.requireNonNull(other, "other");
        for (EditNodeDiagnostic diagnostic : other.getDiagnostics()) {
            add(diagnostic);
        }
    }

    /**
     * Returns all error diagnostics.
     *
     * @return list of error diagnostics
     */
    public List<EditNodeDiagnostic> getErrors() {
        return getDiagnosticsBySeverity(Severity.ERROR);
    }

    /**
     * Returns all warning diagnostics.
     *
     * @return list of warning diagnostics
     */
    public List<EditNodeDiagnostic> getWarnings() {
        return getDiagnosticsBySeverity(Severity.WARNING);
    }

    /**
     * Returns all info diagnostics.
     *
     * @return list of info diagnostics
     */
    public List<EditNodeDiagnostic> getInfos() {
        return getDiagnosticsBySeverity(Severity.INFO);
    }

    /**
     * Returns diagnostics for a specific node.
     *
     * @param node the node to get diagnostics for
     * @return list of diagnostics for the specified node
     */
    public List<EditNodeDiagnostic> getByNode(EditNode node) {
        Objects.requireNonNull(node, "node");
        return getDiagnostics().stream()
                .filter(d -> node.equals(d.getSourceNode()))
                .collect(Collectors.toList());
    }

    /**
     * Returns diagnostics grouped by node.
     *
     * @return map from node to list of diagnostics for that node
     */
    public Map<EditNode, List<EditNodeDiagnostic>> getDiagnosticsByNode() {
        Map<EditNode, List<EditNodeDiagnostic>> result = new HashMap<>();
        for (EditNodeDiagnostic diagnostic : getDiagnostics()) {
            EditNode node = diagnostic.getSourceNode();
            if (node != null) {
                result.computeIfAbsent(node, k -> new ArrayList<>()).add(diagnostic);
            }
        }
        Map<EditNode, List<EditNodeDiagnostic>> immutable = new HashMap<>();
        for (Map.Entry<EditNode, List<EditNodeDiagnostic>> entry : result.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }
}
