/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.merge;

import de.jare.jsoncasted.diff.MergePlan;
import java.util.Collections;
import java.util.List;

/**
 * The result of a merge run: the analysis plan plus whether the applicable part was applied. Problems lists the
 * theirs deltas that could not be translated into commands (for example a drifted path after unexpected structural
 * churn) — they are neither applied nor conflicts, and the caller must show them.
 *
 * @author Janusch Rentenatus
 */
public final class MergeOutcome {

    private final MergePlan plan;
    private final boolean applied;
    private final List<String> problems;

    /**
     * Creates the outcome.
     *
     * @param plan the analysis plan, never {@code null}.
     * @param applied whether the applicable theirs deltas were applied.
     * @param problems deltas that could not be applied, never {@code null}.
     */
    public MergeOutcome(MergePlan plan, boolean applied, List<String> problems) {
        this.plan = plan;
        this.applied = applied;
        this.problems = problems == null ? List.of() : List.copyOf(problems);
    }

    /**
     * @return the analysis plan (apply list, no-ops, conflicts).
     */
    public MergePlan getPlan() {
        return plan;
    }

    /**
     * Tells whether the merge transaction ran (one undo step, see CompositeEditCommand).
     *
     * @return {@code true} when the applicable deltas were applied.
     */
    public boolean isApplied() {
        return applied;
    }

    /**
     * @return descriptions of theirs deltas that could neither be applied nor classified as conflicts.
     */
    public List<String> getProblems() {
        return Collections.unmodifiableList(problems);
    }

    @Override
    public String toString() {
        return plan + (applied ? ", applied" : ", not applied")
                + (problems.isEmpty() ? "" : ", problems: " + problems);
    }
}
