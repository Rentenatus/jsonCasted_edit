/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.convertservice.WoodElementResolver;
import de.jare.jsoncasted.lang.JsonProject;
import de.jare.jsoncasted.lang.JsonResource;
import de.jare.jsoncasted.lang.JsonTerms;import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The session clamp of doc/JSON_PROJECT.md (doc/PLAN_JsonProject.md phase 3): the edit layer's registry
 * {@code {canonical path, synonym} -> EditTree}, exactly parallel to the parse layer. One {@link JsonProject}
 * load provides the resource graph; every {@link JsonResource} is converted exactly ONCE into an
 * {@link EditTree} - B then exists as a single EditTree shared by the A and E tabs, the session-side twin of
 * the parse layer's {@code B == B'}.
 *
 * <p>
 * Two clamps, two truths (concept): the {@code JsonProject} owns the file truth (fingerprints, reload,
 * consumption), this class owns the session truth (the editable trees and their state axes). The state lives on
 * the {@link EditTree} itself (CLEAN/EDITED, CURRENT/OUTDATED) - the project only aggregates.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public final class EditProject {

    private final JsonProject parseProject;
    private final EditTree mainTree;
    private final Map<String, EditTree> treesByPath = new LinkedHashMap<>();
    private final Map<String, EditTree> treesBySynonym = new LinkedHashMap<>();
    private boolean closed;

    /**
     * Opens the editor session of a project main file: one parse-layer project load, one conversion per
     * resource. The main tree builds on the same rails as a plain file import (description file, descriptor,
     * root type), the sub trees convert without their own descriptor.
     *
     * @param mainFile the project's main resource file, not null.
     * @return the open edit project.
     * @throws IOException if a file cannot be read.
     * @throws JsonParseException if parsing or an identity rule fails.
     */
    public static EditProject open(File mainFile) throws IOException, JsonParseException {
        Objects.requireNonNull(mainFile, "mainFile must not be null");
        final JsonProject parse = JsonProject.open(mainFile, JsonDebugLevel.SIMPLE);
        try {
            return new EditProject(parse);
        } catch (IOException | JsonParseException | RuntimeException ex) {
            parse.close();
            throw ex;
        }
    }

    private EditProject(JsonProject parseProject) throws IOException, JsonParseException {
        this.parseProject = parseProject;
        this.mainTree = convertMain(parseProject.getMainResource());
        register(resolveMainSynonym(parseProject), mainTree);
        for (String synonym : parseProject.getSortedSynonyms()) {
            final JsonResource resource = parseProject.findBySynonym(synonym);
            if (resource == null || treesBySynonym.containsKey(synonym)) {
                continue;
            }
            final EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, displayNameOf(resource));
            tree.setProviderName(displayNameOf(resource));
            register(synonym, tree);
        }
    }

    /**
     * The main conversion on the plain import rails - the project's main tree behaves exactly like a
     * fromJsonFile import (description file and descriptor included), only that the parse comes from the
     * project's shared instance.
     */
    private static EditTree convertMain(JsonResource mainResource) throws IOException, JsonParseException {
        final String rootName = displayNameOf(mainResource);
        final EditTree tree = JsonTreeConverter.convertRessourceToEditTree(mainResource, rootName);
        tree.setProviderName(rootName);
        final String descriptionFilePath = WoodElementResolver.extractDescriptionFilePath(mainResource);
        File descriptionFile = null;
        if (mainResource.hasResourceFile()) {
            descriptionFile = WoodElementResolver.findDescriptionFile(
                    descriptionFilePath, new File(mainResource.getResourceFile()));
        }
        return JsonTreeConverter.loadDescrAndConvertRessourceToEditTree(
                tree, descriptionFilePath, descriptionFile);
    }

    /**
     * The display name of a resource: its provider synonym, or {@code this} for the main resource (the same
     * normalization the plain file import uses).
     */
    private static String displayNameOf(JsonResource resource) {
        final String providerName = resource.getProviderName();
        if (providerName == null || providerName.isBlank()
                || JsonTerms.SELF_SYNONYM.equals(providerName)) {
            return JsonTerms.THIS_SYNONYM;
        }
        return providerName;
    }

    private String resolveMainSynonym(JsonProject parseProject) {
        final String providerName = parseProject.getMainResource().getProviderName();
        return providerName != null ? providerName : JsonTerms.SELF_SYNONYM;
    }

    private void register(String synonym, EditTree tree) {
        treesBySynonym.put(synonym, tree);
        final JsonResource resource = tree.getBaseSnapshot();
        if (resource != null && resource.hasResourceFile()) {
            final String canonical = canonicalOf(resource.getResourceFile());
            if (canonical != null) {
                treesByPath.put(canonical, tree);
            }
        }
    }

    private static String canonicalOf(String filename) {
        try {
            return new File(filename).getCanonicalPath();
        } catch (IOException ex) {
            return new File(filename).getAbsolutePath();
        }
    }

    /**
     * Returns the session's main tree - the tab identity of the project main file.
     *
     * @return the main tree, not null while open.
     */
    public EditTree getMainTree() {
        return mainTree;
    }

    /**
     * Returns the shared session tree of a provider synonym - the tree every tab of that resource edits.
     *
     * @param synonym the provider synonym, not null.
     * @return the shared EditTree, or null when the project holds no such synonym.
     */
    public EditTree findBySynonym(String synonym) {
        requireOpen();
        return treesBySynonym.get(synonym);
    }

    /**
     * Returns the shared session tree of a canonical file path.
     *
     * @param canonicalPath the file's canonical path, not null.
     * @return the shared EditTree, or null when the project holds no such file.
     */
    public EditTree findByPath(String canonicalPath) {
        requireOpen();
        return treesByPath.get(canonicalPath);
    }

    /**
     * Returns the parse-layer project this session was opened from - the file truth behind the session truth.
     *
     * @return the parse project, not null while open.
     */
    public JsonProject getParseProject() {
        requireOpen();
        return parseProject;
    }

    /**
     * Returns the synonyms of the session in the parse project's topological order (dependencies first).
     *
     * @return unmodifiable sorted synonym list, not null.
     * @throws JsonParseException when the parse project's provider graph contains a cycle.
     */
    public List<String> getSortedSynonyms() throws JsonParseException {
        requireOpen();
        return Collections.unmodifiableList(parseProject.getSortedSynonyms());
    }

    /**
     * Resolves a synonym-qualified wood link across the session trees (concept doc/JSON_PROJECT.md,
     * integration: cross-tab link resolution): the named provider's tree first - {@code dep::5} lives in B's
     * linking set, so the A and E sides resolve into the SHARED B tree - then, as fallback, any tree's set
     * (the local case and names the parse normalization keeps elsewhere). Tree-local resolution
     * ({@link EditLinkingSet#findLinkTarget}) stays untouched; this is the project-wide seam on top.
     *
     * @param qualifiedLink the qualified link ({@code synonym::objectId}), not null.
     * @return the linked node of the shared tree, or null when no session tree knows the link.
     */
    public EditNodeAbstract resolveLink(String qualifiedLink) {
        requireOpen();
        Objects.requireNonNull(qualifiedLink, "qualifiedLink must not be null");
        EditNodeAbstract hit = resolveIn(treesBySynonym.get(providerOf(qualifiedLink)), qualifiedLink);
        if (hit != null) {
            return hit;
        }
        for (final EditTree tree : treesBySynonym.values()) {
            hit = resolveIn(tree, qualifiedLink);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * One tree, both maps: the resolved link map first (the tree-local case), then the identity map - the
     * cross-provider case the tree-local resolution deliberately leaves open (keys pointing at other providers
     * are a case for the project, not the validator).
     */
    private static EditNodeAbstract resolveIn(EditTree tree, String qualifiedLink) {
        if (tree == null || tree.getLinkingSet() == null) {
            return null;
        }
        final EditLinkingSet set = tree.getLinkingSet();
        final EditNodeAbstract resolved = set.findLinkTarget(qualifiedLink);
        return resolved != null ? resolved : set.getUnmodifiableObjectIdMap().get(qualifiedLink);
    }

    private static String providerOf(String qualifiedLink) {
        final int cut = qualifiedLink.indexOf(JsonTerms.COLONCOLON);
        return cut > 0 ? qualifiedLink.substring(0, cut) : null;
    }

    /**
     * Closes the session: releases every held tree (and with them their retained import states).
     */
    public void close() {
        treesByPath.clear();
        treesBySynonym.clear();
        parseProject.close();
        closed = true;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("The edit project is closed");
        }
    }
}
