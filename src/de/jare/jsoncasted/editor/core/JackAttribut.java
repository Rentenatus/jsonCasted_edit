/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import java.util.Objects;

/**
 * Wrapper class for node attributes that combines name, value, and type information.
 * This ensures that type information is always available and never null.
 *
 * @author Janusch Rentenatus
 */
public class JackAttribut {
    
    private final String name;
    private final Object value;
    private final String type;
    
    /**
     * Creates a new JackAttribut with the specified name, value, and type.
     *
     * @param name the attribute name
     * @param value the attribute value
     * @param type the attribute type
     */
    public JackAttribut(String name, Object value, String type) {
        this.name = name;
        this.value = value;
        this.type = type != null ? type : "null";
    }
    
    /**
     * Creates a new JackAttribut with the specified name and value.
     * The type is automatically derived from the value's class.
     *
     * @param name the attribute name
     * @param value the attribute value
     */
    public JackAttribut(String name, Object value) {
        this(name, value, value == null ? "null" : value.getClass().getSimpleName());
    }
    
    /**
     * Returns the name of this attribute.
     *
     * @return the attribute name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Returns the value of this attribute.
     *
     * @return the attribute value
     */
    public Object getValue() {
        return value;
    }
    
    /**
     * Returns the type of this attribute.
     *
     * @return the attribute type (never null)
     */
    public String getType() {
        return type;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JackAttribut that = (JackAttribut) o;
        return Objects.equals(name, that.name) && 
               Objects.equals(value, that.value) && 
               Objects.equals(type, that.type);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, value, type);
    }
    
    @Override
    public String toString() {
        return "JackAttribut{" +
                "name='" + name + '\'' +
                ", value=" + value +
                ", type='" + type + '\'' +
                '}';
    }
}
