/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

/**
 * Key helpers for annotations in the editable tree (see the annotation concept: annotations carry an {@code @}
 * prefix in the wood JSON, the model declares them without it). A simple key ({@code @hint}) anchors an object level
 * annotation, a composite key ({@code @doc:profile}) anchors a field level annotation at its target field.
 *
 * @author Janusch Rentenatus
 */
public final class AnnotationKeys {

    /**
     * Prefix of every annotation key.
     */
    public static final String PREFIX = "@";

    /**
     * Separator between the annotation name and the target field of a composite key.
     */
    public static final String SEPARATOR = ":";

    private AnnotationKeys() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Checks whether the given property name is an annotation key.
     *
     * @param key the property name to check
     * @return true if the name carries the annotation prefix
     */
    public static boolean isAnnotationKey(String key) {
        return key != null && key.length() > PREFIX.length() && key.startsWith(PREFIX);
    }

    /**
     * Checks whether the given property name is a composite annotation key ({@code @doc:profile}).
     *
     * @param key the property name to check
     * @return true if the key anchors a field level annotation
     */
    public static boolean isCompositeKey(String key) {
        return isAnnotationKey(key) && key.indexOf(SEPARATOR) > PREFIX.length();
    }

    /**
     * Returns the annotation name without the {@code @} prefix and without the composite target.
     *
     * @param key the property name
     * @return the annotation name, or {@code null} if the key is no annotation key
     */
    public static String annotationName(String key) {
        if (!isAnnotationKey(key)) {
            return null;
        }
        final int cut = key.indexOf(SEPARATOR);
        final int end = cut > PREFIX.length() ? cut : key.length();
        return key.substring(PREFIX.length(), end);
    }

    /**
     * Returns the target field of a composite annotation key.
     *
     * @param key the property name
     * @return the target field name, or {@code null} if the key is no composite key
     */
    public static String targetField(String key) {
        if (!isCompositeKey(key)) {
            return null;
        }
        return key.substring(key.indexOf(SEPARATOR) + 1);
    }
}
