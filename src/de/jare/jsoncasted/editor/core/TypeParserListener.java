/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

/**
 * Listener interface for receiving notifications about node changes that require
 * type re-parsing.
 * 
 * <p>
 * Implementations of this interface can be registered with an EditTree to receive
 * callbacks when nodes are modified in ways that may affect their type assignment.
 * The listener will then typically add the modified node to the parse queue.
 * </p>
 * 
 * <p>
 * This listener is part of the on-the-fly parsing infrastructure and enables
 * automatic re-parsing when nodes are edited during live editing sessions.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public interface TypeParserListener {

    /**
     * Called when a node's name has been changed.
     * A name change may affect type inference, especially if the name corresponds
     * to a field name in the model.
     *
     * @param node the node whose name was changed
     * @param oldName the previous name (may be null)
     * @param newName the new name
     */
    void onNodeNameChanged(EditNodeAbstract node, String oldName, String newName);

    /**
     * Called when a node's value has been changed.
     * A value change may affect type validation.
     *
     * @param node the node whose value was changed
     * @param oldValue the previous value (may be null)
     * @param newValue the new value (may be null)
     */
    void onNodeValueChanged(EditNodeAbstract node, String oldValue, String newValue);

    /**
     * Called when a child node has been added to a parent.
     * Adding a child may affect the parent's type inference and the child's type.
     *
     * @param parent the parent node to which the child was added
     * @param child the child node that was added
     */
    void onChildAdded(EditNodeAbstract parent, EditNodeAbstract child);

    /**
     * Called when a child node has been removed from a parent.
     * Removing a child may affect the parent's type inference.
     *
     * @param parent the parent node from which the child was removed
     * @param child the child node that was removed
     */
    void onChildRemoved(EditNodeAbstract parent, EditNodeAbstract child);

    /**
     * Called when a node's type descriptor has been changed externally.
     * This triggers re-parsing of the node and potentially its children.
     *
     * @param node the node whose type descriptor was changed
     */
    void onTypeDescriptorChanged(EditNodeAbstract node);

    /**
     * Called when a node should be re-parsed for any other reason.
     *
     * @param node the node to re-parse
     */
    void onRequestReparse(EditNodeAbstract node);

}
