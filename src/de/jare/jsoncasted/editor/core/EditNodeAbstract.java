/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.tools.SimpleStringSplitter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for all editable JSON tree nodes. Implements EditNode
 * interface and provides common tree structure functionality. Uses interval
 * labeling for efficient tree operations and maintains cached weights for
 *
 * @author Janusch Rentenatus
 */
public abstract non-sealed class EditNodeAbstract implements EditNode, SimpleStringSplitter {

    /**
     * Default left range value for interval labeling.
     *
     * range = RIGHT - LEFT my be a Long too.
     */
    public final static long LEFT = 0;

    /**
     * Default right range value for interval labeling (Long.MAX_VALUE - 1).
     */
    public final static long RIGHT = Long.MAX_VALUE - 1;

    /**
     * Default times range value (Long.MIN_VALUE + 1).
     */
    public final static long ONSET = Long.MIN_VALUE + 1;

    private final long editId;
    private long leftRange;
    private long rightRange;
    private long timesRange;
    private EditNodeAbstract parent;
    private final List<EditNodeAbstract> children = new ArrayList<>();
    private final List<EditNodeAbstract> sortedChildren = new ArrayList<>();
    private int cachedWeight;
    private String editStatus;
    private String editMessage;

    /**
     * Creates a new EditNodeAbstract with a generated edit ID. Initializes with
     * default range values and stateless edit status.
     */
    public EditNodeAbstract() {
        this.editId = IdGenerator.EDIT_ID_GENERATOR.nextId();
        this.leftRange = LEFT;
        this.rightRange = RIGHT;
        this.timesRange = ONSET;
        this.cachedWeight = 1;
        this.editStatus = EDIT_STATELESS;
    }

    /**
     * Creates a new EditNodeAbstract with the specified edit ID. Initializes
     * with default range values and stateless edit status.
     *
     * @param editId the edit identifier to use
     */
    public EditNodeAbstract(long editId) {
        this.editId = editId;
        this.leftRange = LEFT;
        this.rightRange = RIGHT;
        this.timesRange = ONSET;
        this.cachedWeight = 1;
        this.editStatus = EDIT_STATELESS;
    }

    /**
     * Creates a new EditNodeAbstract with the specified ID and range values.
     *
     * @param editId the edit identifier to use
     * @param leftRange the left range value for interval labeling
     * @param rightRange the right range value for interval labeling
     * @param timesRange the times range value for tracking
     */
    EditNodeAbstract(long editId, long leftRange, long rightRange, long timesRange) {
        this.editId = editId;
        this.leftRange = leftRange;
        this.rightRange = rightRange;
        this.timesRange = timesRange;
        this.cachedWeight = 1;
        this.editStatus = EDIT_STATELESS;
    }

    @Override
    public long getEditId() {
        return editId;
    }

    @Override
    public String getEditStatus() {
        return editStatus;
    }

    /**
     * Sets the edit status for this node.
     *
     * @param editStatus the new edit status (one of EDIT_STATELESS, EDIT_OKAY,
     * EDIT_WARNING, EDIT_ERROR)
     */
    public void setEditStatus(String editStatus) {
        this.editStatus = editStatus;
    }

    @Override
    public String getEditMessage() {
        return editMessage;
    }

    /**
     * Sets the edit message for this node.
     *
     * @param editMessage the edit message to set
     */
    public void setEditMessage(String editMessage) {
        this.editMessage = editMessage;
    }

    /**
     * Adds edit-related attributes to the provided map of JackAttribut objects.
     *
     * @param attributes the map to add attributes to
     * @return the modified attributes map
     */
    public Map<String, JackAttribut> putEditAttributes(Map<String, JackAttribut> attributes) {
        attributes.put("|edit id", new JackAttribut("edit id", getEditId()));
        attributes.put("|edit status", new JackAttribut("edit status", getEditStatus()));
        attributes.put("|edit message", new JackAttribut("edit message", getEditMessage()));
        attributes.put("|child count", new JackAttribut("child count", children.size()));
        return attributes;
    }

    @Override
    public long getLeftRange() {
        return leftRange;
    }

    /**
     * Sets the left range value for interval labeling.
     *
     * @param leftRange the new left range value
     */
    void setLeftRange(long leftRange) {
        this.leftRange = leftRange;
    }

    @Override
    public long getRightRange() {
        return rightRange;
    }

    /**
     * Sets the right range value for interval labeling.
     *
     * @param rightRange the new right range value
     */
    void setRightRange(long rightRange) {
        this.rightRange = rightRange;
    }

    @Override
    public long getTimesRange() {
        return timesRange;
    }

    void setTimesRange(long timesRange) {
        this.timesRange = timesRange;
    }

    // ========== Tree structure methods ==========
    @Override
    public EditNodeAbstract getParent() {
        return parent;
    }

    void setParent(EditNodeAbstract parent) {
        this.parent = parent;
    }

    @Override
    public List<EditNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    List<EditNodeAbstract> getAbstractChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    @Override
    public int getWeight(EditTimes weightMonitor) {
        int weight = 1;
        synchronized (weightMonitor) {
            for (EditNodeAbstract child : children) {
                weight += child.getWeight(weightMonitor);
            }
            return cachedWeight = weight;
        }
    }

    @Override
    public int getCachedWeight() {
        return cachedWeight;
    }

    @Override
    public EditNodeAbstract getChildAt(int index) {
        return children.get(index);
    }

    @Override
    public int getChildIndex(EditNode child) {
        return children.indexOf(child);
    }

    public boolean isRangeConsistent() {
        synchronized (sortedChildren) {
            // Pruefe gegen sortedChildren (nach Range sortiert)
            for (int i = 0; i < sortedChildren.size(); i++) {
                EditNodeAbstract child = sortedChildren.get(i);
                // Kind muss innerhalb des Eltern-Ranges liegen
                if (child.getLeftRange() < this.leftRange || child.getRightRange() > this.rightRange) {
                    return false;
                }
                // In sortierter Liste: Keine Ueberlappung wenn rightRange <= next.leftRange
                if (i < sortedChildren.size() - 1) {
                    EditNodeAbstract next = sortedChildren.get(i + 1);
                    if (child.getRightRange() > next.getLeftRange()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Adds a child node to this node at the end of the children list.
     *
     * @param child the child node to add
     * @param weightMonitor the EditTimes monitor for tracking
     */
    void addChild(EditNodeAbstract child, final EditTimes weightMonitor) {
        addChild(child, children.size(), weightMonitor);
    }

    /**
     * Adds a child node to this node at the specified index.
     *
     * @param child the child node to add
     * @param index the index at which to add the child
     * @param weightMonitor the EditTimes monitor for tracking
     */
    void addChild(EditNodeAbstract child, int index, final EditTimes weightMonitor) {
        addChildPhase1(child, index);
        addChildPhase2(child, weightMonitor);
    }

    /**
     * Phase 1 of adding a child: validates and prepares the child for addition.
     *
     * @param child the child node to add
     * @param index the index at which to add the child
     * @throws IllegalArgumentException if child is null
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    void addChildPhase1(EditNodeAbstract child, int index) {
        if (child == null) {
            throw new IllegalArgumentException("Child cannot be null");
        }
        if (index < 0 || index > children.size()) {
            throw new IndexOutOfBoundsException("Index: " + index);
        }
        EditNodeAbstract oldParent = child.getParent();
        if (oldParent != null && oldParent != this) {
            oldParent.removeChild(child);
        }
        children.add(index, child);
        child.setParent(this);
    }

    /**
     * Phase 2 of adding a child: assigns range values to the child and adds it
     * to sortedChildren. Finds the largest available interval in sortedChildren
     * and assigns appropriate ranges.
     *
     * @param child the child node to add
     * @param weightMonitor the EditTimes monitor for tracking
     */
    private void addChildPhase2(EditNodeAbstract child, final EditTimes weightMonitor) {
        synchronized (weightMonitor) {
            // Finde den groessten freien Intervall in sortedChildren
            long maxGapStart = this.leftRange;
            long maxGapSize = 0;
            EditNodeAbstract current;

            synchronized (sortedChildren) {
                // Pruefe Intervall vor dem ersten Kind
                if (sortedChildren.isEmpty()) {
                    // Keine Kinder, ganzer Eltern-Range ist frei
                    maxGapSize = this.rightRange - this.leftRange + 1;
                    setNextFreeRangeTo(child, this.leftRange, maxGapSize, weightMonitor);
                    sortedChildren.add(child);
                    return;
                } else {
                    current = sortedChildren.get(0);
                    long gapSize = current.getLeftRange() - this.leftRange;
                    if (gapSize > maxGapSize) {
                        maxGapSize = gapSize;
                        maxGapStart = this.leftRange;
                    }
                }
                int sortedIndex = 0;

                // Pruefe Intervalle zwischen den Kindern
                for (int i = 1; i < sortedChildren.size(); i++) {
                    EditNodeAbstract next = sortedChildren.get(i);
                    long gapSize = next.getLeftRange() - current.getRightRange() - 1;
                    if (gapSize > maxGapSize) {
                        maxGapSize = gapSize;
                        maxGapStart = current.getRightRange() + 1;
                        sortedIndex = i;
                    }
                    current = next;
                }

                // Pruefe Intervall nach dem letzten Kind
                long gapSize = this.rightRange - current.getRightRange();
                if (gapSize > maxGapSize) {
                    maxGapSize = gapSize;
                    maxGapStart = current.getRightRange() + 1;
                    sortedIndex = sortedChildren.size();
                }

                if (maxGapSize > 0) {
                    setNextFreeRangeTo(child, maxGapStart, maxGapSize, weightMonitor);
                } else {
                    sortedIndex = sortedChildren.size();
                    child.setLeftRange(this.rightRange);
                    child.setRightRange(this.rightRange);
                    child.setTimesRange(weightMonitor.update());
                    child.rangeRelabelingFor(1);
                }
                sortedChildren.add(sortedIndex, child);
            }
        }
    }

    /**
     * Fast version of phase 2 that only adds the child to sortedChildren
     * without range calculation.
     *
     * @param child the child node to add
     */
    void addChildPhase2Fast(EditNodeAbstract child) {
        synchronized (sortedChildren) {
            sortedChildren.add(child);
        }
    }

    /**
     * Assigns range values to a child node based on the largest available
     * interval. Uses 25% of the largest available interval on the left side, at
     * least weight*2.
     *
     * @param child the child node to assign ranges to
     * @param gapStart the start of the available gap
     * @param maxGapSize the size of the available gap
     * @param weightMonitor the EditTimes monitor for tracking
     * @return the start of the assigned range
     */
    private long setNextFreeRangeTo(EditNodeAbstract child, long gapStart, long maxGapSize, final EditTimes weightMonitor) {
        int weight = child.getWeight(weightMonitor);
        // Use 25% of the largest available interval on the left side—at least `weight * 2`,
        //  but no more than the available amount.
        long rangeSize = Math.max(0, Math.min(maxGapSize, Math.max(weight + weight, maxGapSize / 4)));
        child.setLeftRange(gapStart);
        child.setRightRange(Math.min(this.rightRange, gapStart + rangeSize));
        child.setTimesRange(weightMonitor.update());
        child.rangeRelabelingFor(weight - 1);
        return gapStart;
    }

    /**
     * Performs range relabeling for this node and its children. Adjusts range
     * values to maintain proper interval labeling.
     *
     * @param weightMonitor the EditTimes monitor for tracking
     */
    public void rangeRelabeling(final EditTimes weightMonitor) {
        int size;
        EditNodeAbstract[] sortetArr;
        synchronized (sortedChildren) {
            sortetArr = sortedChildren.toArray(new EditNodeAbstract[size = sortedChildren.size()]);
        }
        if (size == 0) {
            return;
        }

        int totalWeight = 0;
        synchronized (weightMonitor) {
            for (EditNodeAbstract child : sortetArr) {
                int weight = child.getWeight(weightMonitor);
                totalWeight += weight;
            }
            long range = rightRange - leftRange + 1;
            if (parent == null || totalWeight + totalWeight < range) { // 100% offset
                setTimesRange(weightMonitor.update());
                rangeRelabelingFor(totalWeight);
                return;
            }
        }
        // Fallback:
        parent.rangeRelabeling(weightMonitor);
    }

    /**
     * Recursively performs range relabeling for this node's children based on
     * total weight. Distributes at least 75% of the range proportionally to
     * child weights.
     *
     * @param totalWeight the total weight of all children
     */
    private void rangeRelabelingFor(int totalWeight) {
//        System.out.println(totalWeight + "  &&&&&&&&&&&&&&&&  " + getClass().getSimpleName()
//                + "[editId=" + getEditId()
//                + ", leftRange=" + getLeftRange()
//                + ", rightRange=" + getRightRange()
//                + ", name=" + getName()
//                + ", value=" + getValue()
//                + ", type=" + getTypeKey() + "]");
        int size;
        EditNodeAbstract[] sortetArr;
        synchronized (sortedChildren) {
            sortetArr = sortedChildren.toArray(new EditNodeAbstract[size = sortedChildren.size()]);
        }

        if (size == 0) {
            return;
        }
        // Mindestes 75.0% verteilen
        double lookingRange = this.rightRange - this.leftRange + 1; // inklusiv
        lookingRange = lookingRange * Math.max(0.75d, totalWeight / lookingRange);

        // Verteile anteilig an Gewichten chronologisch
        long currentStart = this.leftRange;
        for (int i = 0; i < size; i++) {
            EditNodeAbstract child = sortetArr[i];
            int weight = child.getCachedWeight();
            double weightRatio = ((double) weight) / totalWeight;
            long rangeSize = Math.max(1, (long) (weightRatio * lookingRange + 0.5d));
            child.setLeftRange(currentStart);
            child.setRightRange(Math.min(this.rightRange, currentStart + rangeSize - 1)); // inklusiv
            child.setTimesRange(getTimesRange());
            currentStart = Math.min(this.rightRange, child.getRightRange() + 1);
            child.rangeRelabelingFor(weight - 1);
        }
    }

    /**
     * Removes a child node from this node.
     *
     * @param child the child node to remove
     * @return true if the child was removed, false otherwise
     */
    public boolean removeChild(EditNodeAbstract child) {
        boolean removed = children.remove(child);
        if (removed) {
            synchronized (sortedChildren) {
                sortedChildren.remove(child);
                child.setParent(null);
                // Notify child that it was removed
                child.sayOnRemoved(this);
            }
        }
        return removed;
    }

    /**
     * Creates a deep copy of this node with the option to regenerate the edit
     * ID.
     *
     * @param regenerateEditId if true, generates a new edit ID for the copy
     * @return a deep copy of this node
     */
    public abstract EditNodeAbstract deepCopy(boolean regenerateEditId);

    /**
     * Creates a deep copy of this node with a new edit ID.
     *
     * @return a deep copy of this node with a new edit ID
     */
    public EditNodeAbstract deepCopy() {
        return deepCopy(true);
    }

    /**
     * Called when this node is removed from its parent node, giving the parent
     * a chance to update its internal state.
     *
     * @param parent the parent node from which this node was removed
     */
    abstract void sayOnRemoved(EditNode parent);

    // ========== Factory methods ==========
    /**
     * Creates and adds a new child node with the specified name.
     *
     * @param aName the name for the new child node
     * @param asArray if true, creates an array child node
     * @param weightMonitor the EditTimes monitor for tracking
     * @return the newly created child node
     */
    public EditNodeAbstract addNewChild(String aName, boolean asArray, final EditTimes weightMonitor) {
        EditNodeAbstract child = asArray ? createArrChild(aName) : createChild(aName);
        addChild(child, weightMonitor);
        return child;
    }

    /**
     * Creates and adds a new child node with the specified name at the
     * specified index.
     *
     * @param aName the name for the new child node
     * @param index the index at which to add the child
     * @param asArray if true, creates an array child node
     * @param weightMonitor the EditTimes monitor for tracking
     * @return the newly created child node
     */
    EditNodeAbstract addNewChild(String aName, int index, boolean asArray, final EditTimes weightMonitor) {
        EditNodeAbstract child = asArray ? createArrChild(aName) : createChild(aName);
        addChild(child, index, weightMonitor);
        return child;
    }

    /**
     * Creates a new child node with the specified name.
     *
     * @param aName the name for the new child node
     * @return a new EditNodeAbstract child node
     */
    public abstract EditNodeAbstract createChild(String aName);

    /**
     * Creates a new array child node with the specified name.
     *
     * @param aName the name for the new array child node
     * @return a new EditNodeAbstract array child node
     */
    public abstract EditNodeAbstract createArrChild(String aName);

    /**
     * Creates a new neighbor node with the specified name.
     *
     * @param aName the name for the new neighbor node
     * @return a new EditNodeAbstract neighbor node
     */
    public abstract EditNodeAbstract createNeighbor(String aName);

}
