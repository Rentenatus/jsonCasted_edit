/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages object identities and links within an EditTree for cross-referencing.
 *
 * <p>
 * An EditLinkingSet tracks:</p>
 * <ul>
 * <li>Object IDs and their corresponding EditNodeObject nodes</li>
 * <li>Link references and their target EditNodeObject nodes</li>
 * </ul>
 *
 * <p>
 * This is used to resolve cross-references within the EditTree system. Only
 * EditNodeObject instances from the current tree are held, no properties. Each
 * EditNodeObject has an ID.</p>
 *
 * @author Janusch Rentenatus
 */
public final class EditLinkingSet {

    private String providerName;
    private Map<String, EditNodeObject> objectIdMap;
    private Map<String, EditNodeObject> linkMap;

    /**
     * Constructs an EditLinkingSet for the specified provider.
     *
     * @param providerName the name of the provider this linking set belongs to.
     */
    public EditLinkingSet(String providerName) {
        this.providerName = providerName;
        this.objectIdMap = new LinkedHashMap<>();
        this.linkMap = new LinkedHashMap<>();
    }

    /**
     * Returns the provider name for this linking set.
     *
     * @return the provider name.
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * Sets the provider name for this linking set.
     *
     * @param providerName the provider name to set.
     */
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    /**
     * Returns the map of object IDs to their corresponding EditNodeObject
     * nodes.
     *
     * @return the object ID map (may be modified).
     */
    public Map<String, EditNodeObject> getObjectIdMap() {
        return objectIdMap;
    }

    /**
     * Sets the object ID map.
     *
     * @param objectIdMap the map to set, or {@code null} to create an empty
     * map.
     */
    public void setObjectIdMap(Map<String, EditNodeObject> objectIdMap) {
        this.objectIdMap = objectIdMap != null ? new LinkedHashMap<>(objectIdMap) : new LinkedHashMap<>();
    }

    /**
     * Returns the map of link references to their corresponding EditNodeObject
     * nodes.
     *
     * @return the link map (may be modified).
     */
    public Map<String, EditNodeObject> getLinkMap() {
        return linkMap;
    }

    /**
     * Sets the link map.
     *
     * @param linkMap the map to set, or {@code null} to create an empty map.
     */
    public void setLinkMap(Map<String, EditNodeObject> linkMap) {
        this.linkMap = linkMap != null ? new LinkedHashMap<>(linkMap) : new LinkedHashMap<>();
    }

    /**
     * Finds an EditNodeObject by its object ID.
     *
     * @param objectId the object ID to search for.
     * @return the corresponding EditNodeObject, or {@code null} if not found.
     * @throws NullPointerException if objectId is null.
     */
    public EditNodeObject findObjectById(String objectId) {
        Objects.requireNonNull(objectId, "objectId must not be null");
        return objectIdMap.get(providerName + "::" + objectId);
    }

    /**
     * Finds an EditNodeObject by its link reference.
     *
     * @param link the link reference to search for.
     * @return the corresponding EditNodeObject, or {@code null} if not found.
     * @throws NullPointerException if link is null.
     */
    public EditNodeObject findLinkTarget(String link) {
        Objects.requireNonNull(link, "link must not be null");
        return linkMap.get(link);
    }

    /**
     * Registers an EditNodeObject with its ID in the object ID map.
     *
     * @param editNode the EditNodeObject to register.
     * @throws NullPointerException if editNode is null.
     */
    public void registerObject(EditNodeObject editNode) {
        Objects.requireNonNull(editNode, "editNode must not be null");
        String objektId = editNode.getObjektId();
        if (objektId != null && !objektId.isEmpty()) {
            objectIdMap.put(providerName + "::" + objektId, editNode);
        }
    }

    /**
     * Registers an EditNodeObject as a link target.
     *
     * @param link the link reference.
     * @param editNode the EditNodeObject to register as link target.
     * @throws NullPointerException if link or editNode is null.
     */
    public void registerLink(String link, EditNodeObject editNode) {
        Objects.requireNonNull(link, "link must not be null");
        Objects.requireNonNull(editNode, "editNode must not be null");
        linkMap.put(link, editNode);
    }

    /**
     * Removes an EditNodeObject from the object ID map.
     *
     * @param editNode the EditNodeObject to remove.
     * @return true if the node was removed, false otherwise.
     */
    public boolean removeObject(EditNodeObject editNode) {
        if (editNode == null) {
            return false;
        }
        String objektId = editNode.getObjektId();
        if (objektId != null && !objektId.isEmpty()) {
            return objectIdMap.remove(providerName + "::" + objektId) != null;
        }
        return false;
    }

    /**
     * Removes a link reference from the link map.
     *
     * @param link the link reference to remove.
     * @return the EditNodeObject that was removed, or null if not found.
     */
    public EditNodeObject removeLink(String link) {
        if (link == null) {
            return null;
        }
        return linkMap.remove(link);
    }

    /**
     * Returns an unmodifiable view of the object ID map.
     *
     * @return unmodifiable map of object IDs to EditNodeObject instances.
     */
    public Map<String, EditNodeObject> getUnmodifiableObjectIdMap() {
        return Collections.unmodifiableMap(objectIdMap);
    }

    /**
     * Returns an unmodifiable view of the link map.
     *
     * @return unmodifiable map of link references to EditNodeObject instances.
     */
    public Map<String, EditNodeObject> getUnmodifiableLinkMap() {
        return Collections.unmodifiableMap(linkMap);
    }

    /**
     * Returns all EditNodeObject instances from the object ID map.
     *
     * @return list of all registered EditNodeObject instances.
     */
    public List<EditNodeObject> getAllObjects() {
        return new ArrayList<>(objectIdMap.values());
    }

    /**
     * Returns all EditNodeObject instances from the link map.
     *
     * @return list of all linked EditNodeObject instances.
     */
    public List<EditNodeObject> getAllLinkTargets() {
        return new ArrayList<>(linkMap.values());
    }

    /**
     * Checks if this set contains an object with the specified ID.
     *
     * @param objectId the object ID to check.
     * @return true if the object exists, false otherwise.
     */
    public boolean containsObjectId(String objectId) {
        if (objectId == null) {
            return false;
        }
        return objectIdMap.containsKey(providerName + "::" + objectId);
    }

    /**
     * Checks if this set contains a link with the specified reference.
     *
     * @param link the link reference to check.
     * @return true if the link exists, false otherwise.
     */
    public boolean containsLink(String link) {
        if (link == null) {
            return false;
        }
        return linkMap.containsKey(link);
    }

    /**
     * Clears all registered objects and links.
     */
    public void clear() {
        objectIdMap.clear();
        linkMap.clear();
    }

    @Override
    public String toString() {
        return "EditLinkingSet{"
                + "providerName='" + providerName + '\''
                + ", objectIdMapSize=" + (objectIdMap != null ? objectIdMap.size() : 0)
                + ", linkMapSize=" + (linkMap != null ? linkMap.size() : 0)
                + '}';
    }
}
