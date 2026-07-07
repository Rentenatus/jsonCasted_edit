/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Container for a collection of resolved and edited EditTree resources.
 *
 * <p>
 * This class manages multiple {@link EditTree} instances that represent
 * resolved/edited resources. It provides lookup and merging capabilities for
 * linking editors and allowing immediate use of changes (e.g., new links) in
 * dependent instances.</p>
 *
 * @see EditTree
 *
 * @author Janusch Rentenatus
 */
public final class EditProviderBox {

    private final List<EditTree> providers;

    /**
     * Constructs an EditProviderBox with the specified list of providers.
     *
     * @param editTrees the list of EditTree providers (must not be null).
     * @throws NullPointerException if the provider list is null.
     */
    public EditProviderBox(List<EditTree> editTrees) {
        Objects.requireNonNull(editTrees, "editTrees must not be null");
        this.providers = new ArrayList<>(editTrees);
    }

    /**
     * Returns an unmodifiable list of all providers.
     *
     * @return unmodifiable list of EditTree instances.
     */
    public List<EditTree> getProviders() {
        return Collections.unmodifiableList(providers);
    }

    /**
     * Finds a provider by its root node text content.
     *
     * @param rootText the root node text content to search for.
     * @return the matching EditTree, or {@code null} if not found.
     */
    public EditTree findByRootText(String rootText) {
        for (EditTree provider : providers) {
            if (provider.getRoot() != null
                    && provider.getRoot().toString().equals(rootText)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * Checks if this box contains a provider with the specified root node text
     * content.
     *
     * @param rootText the root node text content to check.
     * @return {@code true} if a matching provider exists, {@code false}
     * otherwise.
     */
    public boolean containsRootText(String rootText) {
        return findByRootText(rootText) != null;
    }

    /**
     * Returns the number of providers in this box.
     *
     * @return the provider count.
     */
    public int size() {
        return providers.size();
    }

    /**
     * Checks if this box is empty.
     *
     * @return {@code true} if there are no providers, {@code false} otherwise.
     */
    public boolean isEmpty() {
        return providers.isEmpty();
    }

    /**
     * Merges another provider box into this one.
     *
     * <p>
     * Providers from the other box are added only if their root node text is
     * not already present in this box.</p>
     *
     * @param box the other provider box to merge (ignored if null or empty).
     */
    public void mergeBox(EditProviderBox box) {
        if (box == null || box.isEmpty()) {
            return;
        }
        for (EditTree provider : box.providers) {
            if (provider.getRoot() != null
                    && !containsRootText(provider.getRoot().toString())) {
                providers.add(provider);
            }
        }
    }

    @Override
    public String toString() {
        return "EditProviderBox{"
                + "providers=" + providers
                + '}';
    }
}
