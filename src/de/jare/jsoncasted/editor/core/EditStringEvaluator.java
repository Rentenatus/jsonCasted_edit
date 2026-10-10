/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import java.text.DecimalFormat;

/**
 * Evaluator of the editString display templates (annotation concept). A template is a literal string with
 * placeholders: {@code ${param}} resolves against the fields of the object (its child properties), a pipe chain of
 * filters forms the value.
 *
 * <p>
 * Grammar:</p>
 * <ul>
 * <li>{@code ${name}} - the primitive value of the child property {@code name}; an object typed field contributes
 * the display text of its first child object (recursive), an array typed field contributes an empty string</li>
 * <li>{@code ${woodId}} / {@code ${jsonType}} - the wood object id and the type name of the object itself</li>
 * <li>Filters, left to right: {@code |upper}, {@code |lower}, {@code |trim}, {@code |substr:start:end} (negative
 * bounds count from the end, end exclusive and optional), {@code |number:pattern} (java.text.DecimalFormat on
 * numeric values), {@code |default:'fallback'} (for a blank value at that point of the chain)</li>
 * <li>{@code $${} is the literal {@code ${}}</li>
 * <li>A missing parameter renders empty - a {@code default} filter catches it</li>
 * </ul>
 *
 * <p>
 * The templates deliberately stay declarative: no expressions, no arithmetic, nothing that executes code - the
 * evaluator is a pure string function, headless testable.</p>
 *
 * @author Janusch Rentenatus
 */
public final class EditStringEvaluator {

    private static final String PLACEHOLDER_START = "${";
    private static final String PLACEHOLDER_END = "}";
    private static final char ESCAPE = '$';
    private static final char FILTER_SEPARATOR = '|';

    private EditStringEvaluator() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Returns the display text of the object: the formatted editString template of its type when the type carries
     * one, otherwise the fallback chain of the display side - the value of a child property {@code name} (the
     * common parameter), the wood object id, the plain object name.
     *
     * @param node the object node, may be null
     * @return the display text, never null
     */
    public static String displayText(EditNodeObject node) {
        if (node == null) {
            return "";
        }
        final JsonTypeDescriptor type = node.getJsonType();
        final String template = type == null ? null : type.getEditString();
        if (template != null && !template.isBlank()) {
            return format(node, template);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeProperty property
                    && "name".equals(property.getName()) && property.getValue() != null) {
                return property.getValue();
            }
        }
        final String woodId = node.getObjektId();
        if (woodId != null && !woodId.isEmpty()) {
            return woodId;
        }
        return node.getName() == null ? "" : node.getName();
    }

    /**
     * Formats the template against the object: substitutes every placeholder by the filtered value of its
     * parameter.
     *
     * @param node the object node providing the parameters
     * @param template the editString template
     * @return the formatted text, the template itself when it is null or blank
     */
    public static String format(EditNodeObject node, String template) {
        if (template == null || template.isBlank()) {
            return template;
        }
        final StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            final char c = template.charAt(i);
            if (c == ESCAPE && template.startsWith("$${", i)) {
                result.append(PLACEHOLDER_START);
                i += 3;
            } else if (c == ESCAPE && template.startsWith(PLACEHOLDER_START, i)) {
                final int end = template.indexOf(PLACEHOLDER_END, i + 2);
                if (end < 0) {
                    result.append(c);
                    i++;
                } else {
                    result.append(placeholder(node, template.substring(i + 2, end)));
                    i = end + 1;
                }
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }

    /**
     * Resolves one placeholder content: the parameter name, then the filter chain applied left to right.
     *
     * @param node the object node providing the parameters
     * @param content the placeholder content without delimiters
     * @return the filtered value
     */
    private static String placeholder(EditNodeObject node, String content) {
        final String[] parts = content.split("\\" + FILTER_SEPARATOR, -1);
        String value = resolveParam(node, parts[0].trim());
        for (int i = 1; i < parts.length; i++) {
            value = applyFilter(value, parts[i].trim());
        }
        return value;
    }

    /**
     * Resolves a parameter name against the object: the special names woodId and jsonType first, then the child
     * properties by name. Missing parameters render empty.
     *
     * @param node the object node providing the parameters
     * @param paramName the parameter name
     * @return the raw parameter value, never null
     */
    private static String resolveParam(EditNodeObject node, String paramName) {
        if (node == null || paramName.isEmpty()) {
            return "";
        }
        switch (paramName) {
            case "woodId" -> {
                final String woodId = node.getObjektId();
                return woodId == null ? "" : woodId;
            }
            case "jsonType" -> {
                final JsonTypeDescriptor type = node.getJsonType();
                return type == null ? "" : type.getTypeName();
            }
            default -> {
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeProperty property
                    && paramName.equals(property.getName())) {
                return propertyValue(property);
            }
        }
        return "";
    }

    /**
     * Derives the value of a child property: its primitive value, the display text of the first child object of an
     * object typed field (recursive display), or an empty string for array typed fields.
     *
     * @param property the child property
     * @return the value, never null
     */
    private static String propertyValue(EditNodeProperty property) {
        if (property.getValue() != null) {
            return property.getValue();
        }
        for (int i = 0; i < property.getChildCount(); i++) {
            if (property.getChildAt(i) instanceof EditNodeObject childObject) {
                return displayText(childObject);
            }
        }
        return "";
    }

    /**
     * Applies one filter of the chain.
     *
     * @param value the current value
     * @param filterSpec the filter with its optional colon argument
     * @return the filtered value
     */
    private static String applyFilter(String value, String filterSpec) {
        final int cut = filterSpec.indexOf(':');
        final String name = (cut < 0 ? filterSpec : filterSpec.substring(0, cut)).trim();
        final String arg = cut < 0 ? null : filterSpec.substring(cut + 1).trim();
        return switch (name) {
            case "upper" -> value.toUpperCase();
            case "lower" -> value.toLowerCase();
            case "trim" -> value.trim();
            case "substr" -> substr(value, arg);
            case "number" -> number(value, arg);
            case "default" -> value.isBlank() ? unquote(arg) : value;
            default -> value; // unknown filters pass through, the text stays visible
        };
    }

    /**
     * Cuts the value to the given bounds: start inclusive, end exclusive, both negative bounds count from the end,
     * a missing end runs to the end of the value. Everything is clamped.
     *
     * @param value the current value
     * @param arg the bounds argument, {@code start:end} or {@code start}
     * @return the cut value, the value itself without a parsable argument
     */
    private static String substr(String value, String arg) {
        if (arg == null || arg.isBlank()) {
            return value;
        }
        final String[] bounds = arg.split(":", 2);
        final int start = clampBound(bounds[0].trim(), value.length());
        final int end = bounds.length > 1 && !bounds[1].isBlank()
                ? clampBound(bounds[1].trim(), value.length())
                : value.length();
        if (start >= end) {
            return "";
        }
        return value.substring(start, end);
    }

    /**
     * Parses one bound of a substr filter: negative bounds count from the end, the result is clamped to the value.
     *
     * @param bound the bound as text
     * @param length the value length for clamping
     * @return the clamped bound, 0 when the bound does not parse
     */
    private static int clampBound(String bound, int length) {
        final int parsed;
        try {
            parsed = Integer.parseInt(bound);
        } catch (NumberFormatException ex) {
            return 0;
        }
        final int resolved = parsed < 0 ? length + parsed : parsed;
        return Math.max(0, Math.min(length, resolved));
    }

    /**
     * Formats a numeric value with a java.text.DecimalFormat pattern; non-numeric values and missing patterns pass
     * through untouched.
     *
     * @param value the current value
     * @param pattern the DecimalFormat pattern
     * @return the formatted value, or the value itself
     */
    private static String number(String value, String pattern) {
        if (value.isBlank() || pattern == null || pattern.isBlank()) {
            return value;
        }
        try {
            return new DecimalFormat(pattern).format(Double.parseDouble(value));
        } catch (NumberFormatException ex) {
            return value;
        }
    }

    /**
     * Strips the surrounding single quotes of a filter argument; unquoted arguments pass through.
     *
     * @param arg the filter argument, or null
     * @return the argument text, empty for null
     */
    private static String unquote(String arg) {
        if (arg == null) {
            return "";
        }
        if (arg.length() >= 2 && arg.startsWith("'") && arg.endsWith("'")) {
            return arg.substring(1, arg.length() - 1);
        }
        return arg;
    }
}
