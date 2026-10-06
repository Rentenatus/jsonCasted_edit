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

    /**
     * Checks whether the given pattern matches the target. The pattern is a simple * miniregex: every '*' matches
     * an arbitrary sequence of characters (including none), all other characters are literal.
     *
     * @param pattern the pattern with optional '*' wildcards
     * @param target the target to match against
     * @return true if the pattern matches the target
     */
    public static boolean matchesPattern(String pattern, String target) {
        if (pattern == null || target == null) {
            return false;
        }
        if (!pattern.contains("*")) {
            return pattern.equals(target);
        }
        final StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            final char c = pattern.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));
            }
        }
        return target.matches(regex.toString());
    }

    /**
     * Finds the wildcard declaration of the given annotation for the named target among the type's annotations: an
     * entry in composite form ({@code name:pattern}) whose annotation name matches and whose * miniregex pattern
     * matches the target. A declared {@code doc:*} on a type therefore covers every field (pass the field name as
     * the target) and the object level itself (pass {@code "*"} as the target - only the pure wildcard covers the
     * type, prefix patterns stay field specific).
     *
     * @param type the type descriptor with the declared annotations
     * @param annName the plain annotation name without prefix and target
     * @param target the target the pattern must match
     * @return the matching wildcard declaration, or {@code null} if none matches
     */
    public static de.jare.jsoncasted.model.item.JsonAnnotation findWildcardDeclaration(
            de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor type, String annName, String target) {
        if (type == null || annName == null || target == null) {
            return null;
        }
        for (de.jare.jsoncasted.model.item.JsonAnnotation next : type.getAnnotations()) {
            final String name = next.getName();
            final int cut = name.indexOf(SEPARATOR);
            if (cut < PREFIX.length()) {
                continue;
            }
            final String candidate = name.substring(0, cut);
            final String pattern = name.substring(cut + 1);
            if (annName.equals(candidate) && matchesPattern(pattern, target)) {
                return next;
            }
        }
        return null;
    }
}

