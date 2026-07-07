/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import java.util.List;
import java.util.Map;

/**
 * Represents a node in the editable JSON tree structure. Extends
 * JsonTreeNodeData with tree structure methods and edit state management.
 * Implementations include EditNodeAbstract, EditNodeObject, EditNodeProperty,
 * and EditNodePropertyArr.
 *
 * @author Janusch Rentenatus
 */
public sealed interface EditNode permits EditNodeAbstract, EditNodeObject, EditNodeProperty, EditNodePropertyArr {

    /**
     * Edit state constant indicating the node has no specific edit state.
     */
    public final static String EDIT_STATELESS = "stateless";

    /**
     * Edit state constant indicating the node is in a valid state.
     */
    public final static String EDIT_OKAY = "okay";

    /**
     * Edit state constant indicating the node has a warning.
     */
    public final static String EDIT_WARNING = "warning";

    /**
     * Edit state constant indicating the node has an error.
     */
    public final static String EDIT_ERROR = "error";

    /**
     * Returns the unique edit identifier for this node.
     *
     * @return the edit ID
     */
    long getEditId();

    /**
     * Returns the right-side string representation of this node. Used for
     * displaying node information in a readable format.
     *
     * @return the right string representation
     */
    public String rightString();

    /**
     * Callback that is invoked when an object child has been removed from this
     * node. Allows parent nodes to update their internal state when a child is
     * removed.
     *
     * @param child the EditNodeObject that was removed
     */
    default void onChildObjectDataRemoved(EditNodeObject child) {
    }

    /**
     * Callback that is invoked when a property child has been removed from this
     * node. Allows parent nodes to update their internal state when a child is
     * removed.
     *
     * @param child the EditNodeProperty that was removed
     */
    default void onChildPropertyDataRemoved(EditNodeProperty child) {
    }

    /**
     * Checks if this node can be a child of the specified parent node.
     *
     * @param parent the potential parent node
     * @return true if this node can be a child of the parent, false otherwise
     */
    boolean canBeChildOf(EditNode parent);

    /**
     * Checks if this node can be a parent of object data nodes.
     *
     * @return true if this node can have EditNodeObject children, false
     * otherwise
     */
    default boolean canBeParentOfObjectData() {
        return false;
    }

    /**
     * Checks if this node can be a parent of property data nodes.
     *
     * @return true if this node can have EditNodeProperty children, false
     * otherwise
     */
    default boolean canBeParentOfPropertyData() {
        return false;
    }

    /**
     * Checks if this node can be a parent of array property data nodes.
     *
     * @return true if this node can have EditNodePropertyArr children, false
     * otherwise
     */
    default boolean canBeParentOfPropertyArrData() {
        return false;
    }

    /**
     * Returns the left range value for interval labeling.
     *
     * @return the left range value
     */
    long getLeftRange();

    /**
     * Returns the right range value for interval labeling.
     *
     * @return the right range value
     */
    long getRightRange();

    /**
     * Returns the times range value for tracking node creation/modification
     * order.
     *
     * @return the times range value
     */
    long getTimesRange();

    /**
     * Returns the name of this node.
     *
     * @return the node name
     */
    String getName();

    /**
     * Returns the current edit status of this node.
     *
     * @return the edit status (one of EDIT_STATELESS, EDIT_OKAY, EDIT_WARNING,
     * EDIT_ERROR)
     */
    public Object getEditStatus();

    /**
     * Returns the edit message associated with this node.
     *
     * @return the edit message, or null if none
     */
    public Object getEditMessage();

    /**
     * Sets the name of this node.
     *
     * @param editText the new name to set
     */
    void setName(String editText);

    /**
     * Returns the value of this node.
     *
     * @return the node value, or null by default
     */
    default String getValue() {
        return null;
    }

    /**
     * Sets the value of this node. Default implementation does nothing.
     *
     * @param value the value to set
     */
    default void setValue(String value) {
    }

    // ========== Tree structure methods ==========
    /**
     * Returns the parent node of this node.
     *
     * @return the parent node, or null if this is the root node
     */
    EditNode getParent();

    /**
     * Checks if this node is or has the specified node as a parent.
     *
     * @param maybeParent the node to check as parent
     * @return true if this node is the same as maybeParent or has it as a
     * parent, false otherwise
     */
    default boolean isOrHasParent(EditNode maybeParent) {
        return this == maybeParent || hasParent(maybeParent);

    }

    /**
     * Checks if this node has the specified node as a parent.
     *
     * @param maybeParent the node to check as parent
     * @return true if this node has maybeParent as a parent, false otherwise
     */
    default boolean hasParent(EditNode maybeParent) {
        EditNode parent = getParent();
        if (parent == null) {
            return false;
        }
        return parent == maybeParent || parent.hasParent(maybeParent);
    }

    /**
     * Returns the list of child nodes.
     *
     * @return unmodifiable list of child nodes
     */
    List<EditNode> getChildren();

    /**
     * Returns the number of child nodes.
     *
     * @return the child count
     */
    int getChildCount();

    /**
     * Calculates and returns the weight of this node and its subtree.
     *
     * @param weightMonitor the EditTimes monitor for tracking
     * @return the total weight
     */
    int getWeight(EditTimes weightMonitor);

    /**
     * Returns the cached weight of this node.
     *
     * @return the cached weight value
     */
    int getCachedWeight();

    /**
     * Returns the child node at the specified index.
     *
     * @param index the index of the child to return
     * @return the child node at the index
     */
    EditNode getChildAt(int index);

    /**
     * Returns the index of the specified child node.
     *
     * @param child the child node to find
     * @return the index of the child, or -1 if not found
     */
    int getChildIndex(EditNode child);

    /**
     * Returns the type key for this node.
     *
     * @return the type key string
     */
    public String getTypeKey();

    /**
     * Returns the attributes of this node as a map of JackAttribut objects.
     * This ensures type information is always available.
     *
     * @return the attributes map, or null if not supported
     */
    default Map<String, JackAttribut> getAttributes() {
        return null;
    }

    /**
     * Sets the attributes of this node from a map of JackAttribut objects.
     *
     * @param props the attributes map to set
     */
    default void setAttributes(Map<String, JackAttribut> props) {
    }

}
