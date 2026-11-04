/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import java.util.Objects;

/**
 * The save guard of doc/JSON_PROJECT.md (edit layer): the quadrant decision before overwriting a file. The
 * two axes of the {@link EditTree} decide - the working-copy axis (CLEAN/EDITED) and the file-currency axis
 * (CURRENT/OUTDATED), compared against the file's fingerprint state:
 *
 * <ul>
 * <li>{@code CLEAN + CURRENT} - nothing to do; nobody touched anything.</li>
 * <li>{@code EDITED + CURRENT} - the normal save: plain overwrite (nobody touched the file behind the
 * editor's back).</li>
 * <li>{@code CLEAN + OUTDATED} - safe to re-import: no user work at risk, the external change wins.</li>
 * <li>{@code EDITED + OUTDATED} - the real 3-way conflict, never resolved automatically: the file changed
 * under unsaved work. The rails exist (base = retained import state, ours = the tree, theirs = the external
 * file, doc/TREE_DIFF.md); the policy hook asks the user.</li>
 * </ul>
 *
 * @author Janusch Rentenatus
 */
public final class SaveGuard {

    /**
     * The decision of one save attempt.
     */
    public enum Decision {

        /**
         * CLEAN + CURRENT: nothing to do (a no-op save may skip silently).
         */
        NOTHING_TO_DO,
        /**
         * EDITED + CURRENT: the plain overwrite is allowed.
         */
        PROCEED_SAVE,
        /**
         * CLEAN + OUTDATED: no user work at risk - re-import the file (the external change wins).
         */
        REIMPORT,
        /**
         * EDITED + OUTDATED: the 3-way conflict - never a blind overwrite; policy hook (ask) on the
         * existing merge rails.
         */
        CONFLICT
    }

    private SaveGuard() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Decides one save attempt from the tree's two axes.
     *
     * @param tree the tree whose file is about to be written, not null.
     * @return the quadrant decision, never null.
     */
    public static Decision decide(EditTree tree) {
        Objects.requireNonNull(tree, "tree must not be null");
        final boolean edited = tree.isEdited();
        final boolean outdated = tree.isOutdated();
        if (edited) {
            return outdated ? Decision.CONFLICT : Decision.PROCEED_SAVE;
        }
        return outdated ? Decision.REIMPORT : Decision.NOTHING_TO_DO;
    }
}
