/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.merge;

import de.jare.jsoncasted.diff.DeltaOp;
import de.jare.jsoncasted.diff.DeltaProperty;
import de.jare.jsoncasted.diff.DeltaType;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.lang.JsonNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pick boundary translation of a cross-world pair (concept WoodJsonJack
 * CONCEPT_ProjectView.md, question 10 - phase 10): ours is the live session, theirs is a stand of
 * a foreign identity world, typically an isolated source (an old version to look at). A cherry-pick
 * from the ghost must not inherit ghost identities - they collide with the live world by the
 * ghost's very reason of existence - and it must not partially roll a reroll back. The translation
 * is causal, per pick, nothing is persisted:
 *
 * <ol>
 * <li><b>Synonym check:</b> the picked references carry ghost synonyms. The only stable anchor
 * between two versions of a bundle is the FILE - a ghost provider entry names the same file as a
 * live provider entry under a (new) synonym. The translation derives old&#8594;current from that
 * correspondence.</li>
 * <li><b>Conflict check:</b> every translated reference must resolve. References into the pick's
 * own content rewire to the fresh ids; references to live objects must exist under the translated
 * synonym in the live world - a reference to a rerolled-away id dangles and REFUSES the whole
 * pick (nothing is half-applied), the user decides.</li>
 * <li><b>Fresh ids for every picked element:</b> added subtrees get newly generated, disjoint ids
 * ({@code r}-prefixed like the reroll); identity deltas as such never apply - the diff engine
 * reports identity changes as REMOVE + ADD anyway.</li>
 * </ol>
 *
 * @author Janusch Rentenatus
 */
public final class PickIdentityTranslation {

    /**
     * The prefix of freshly generated pick identities: {@code r1}, {@code r2}, ... - the same
     * scheme the reroll uses, born new in the live world.
     */
    public static final String FRESH_ID_PREFIX = "r";

    private static final String SYNONYM_KEY = "synonym";
    private static final String FILENAME_KEY = "filename";

    private final Map<String, String> liveSynonymOfGhostSynonym;
    private final Set<String> oursQualifiedIds;
    private final Set<String> oursRawIds;

    private PickIdentityTranslation(Map<String, String> liveSynonymOfGhostSynonym,
            Set<String> oursQualifiedIds, Set<String> oursRawIds) {
        this.liveSynonymOfGhostSynonym = liveSynonymOfGhostSynonym;
        this.oursQualifiedIds = oursQualifiedIds;
        this.oursRawIds = oursRawIds;
    }

    /**
     * One translated pick run: the rewired deltas to apply, or the refusals - a refused pick
     * applies NOTHING (the whole pick is the decision unit).
     */
    public static final class Result {

        private final boolean ok;
        private final List<DeltaOp> deltas;
        private final List<String> problems;

        private Result(boolean ok, List<DeltaOp> deltas, List<String> problems) {
            this.ok = ok;
            this.deltas = deltas;
            this.problems = problems;
        }

        public boolean isOk() {
            return ok;
        }

        public List<DeltaOp> getDeltas() {
            return deltas;
        }

        public List<String> getProblems() {
            return problems;
        }
    }

    /**
     * Prepares the translation of one cross-world pair: the synonym correspondence via the
     * provider file anchor and the live world's identity inventory (for validation and
     * disjointness).
     *
     * <p>
     * The anchor between two versions of a bundle is the provider entry's RELATIVE filename - the
     * ghost mount is a copy/checkout of the same structure, so {@code ./rBox.json} names the same
     * member of the bundle in both worlds even though the absolute folders differ. A live entry
     * under a new synonym tells the ghost's old synonym its current name.
     * </p>
     *
     * @param theirsTree the ghost stand's edit tree (its providers member is the ghost side of the
     * correspondence)
     * @param oursBySessionSynonym every live tree of the ours session, keyed by its session
     * synonym, not null
     * @return the prepared translation - unresolvable synonyms surface at pick time (they may
     * simply not be referenced by the picked content)
     */
    public static PickIdentityTranslation of(EditTree theirsTree,
            Map<String, EditTree> oursBySessionSynonym) {
        // the live side of the anchor: relative provider filename -> the synonym the live world calls it by
        final Map<String, String> liveSynonymOfProviderName = new LinkedHashMap<>();
        for (EditTree oursTree : oursBySessionSynonym.values()) {
            final List<JsonNode> providers = providersOf(oursTree);
            if (providers == null) {
                continue;
            }
            for (JsonNode provider : providers) {
                final String synonym = textOf(provider, SYNONYM_KEY);
                final String filename = textOf(provider, FILENAME_KEY);
                if (synonym != null && filename != null) {
                    liveSynonymOfProviderName.putIfAbsent(relativeKeyOf(filename), synonym);
                }
            }
        }
        // the ghost side: ghost synonym -> live synonym via the same relative member name
        final Map<String, String> synonymMap = new LinkedHashMap<>();
        final List<JsonNode> theirsProviders = providersOf(theirsTree);
        if (theirsProviders != null) {
            for (JsonNode provider : theirsProviders) {
                final String ghostSynonym = textOf(provider, SYNONYM_KEY);
                final String filename = textOf(provider, FILENAME_KEY);
                if (ghostSynonym == null || filename == null) {
                    continue;
                }
                final String liveSynonym = liveSynonymOfProviderName.get(relativeKeyOf(filename));
                if (liveSynonym != null) {
                    synonymMap.put(ghostSynonym, liveSynonym);
                }
            }
        }
        // the live world's identity inventory: raw ids for disjointness, qualified ids for the
        // reference validation
        final Set<String> oursQualifiedIds = new LinkedHashSet<>();
        final Set<String> oursRawIds = new LinkedHashSet<>();
        for (Map.Entry<String, EditTree> entry : oursBySessionSynonym.entrySet()) {
            final String sessionSynonym = entry.getKey();
            final EditNodeAbstract root = entry.getValue().getRoot();
            final Deque<EditNodeAbstract> queue = new ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty()) {
                final EditNodeAbstract node = queue.poll();
                if (node instanceof EditNodeObject obj && obj.getObjektId() != null
                        && !obj.getObjektId().isEmpty()) {
                    oursRawIds.add(obj.getObjektId());
                    oursQualifiedIds.add(sessionSynonym + "::" + obj.getObjektId());
                }
                for (int i = 0; i < node.getChildCount(); i++) {
                    if (node.getChildAt(i) instanceof EditNodeAbstract child) {
                        queue.add(child);
                    }
                }
            }
        }
        return new PickIdentityTranslation(synonymMap, oursQualifiedIds, oursRawIds);
    }

    /**
     * The anchor form of a provider filename: the bundle-relative member name (the working
     * directory never mattered - the entries are file-relative per the binding of question 7).
     */
    private static String relativeKeyOf(String filename) {
        String cleaned = filename.replace('\\', '/');
        while (cleaned.startsWith("./")) {
            cleaned = cleaned.substring(2);
        }
        return cleaned;
    }

    /**
     * Translates the picked deltas of the cross-world pair (concept question 10). Added subtrees
     * mutate in place (they are the frozen copy's nodes - the pick inserts their content), link
     * target deltas are copied with the rewired value; a single unresolvable reference refuses
     * the whole pick.
     *
     * @param deltas the picked theirs deltas, in pick order
     * @return the run - check {@link Result#isOk()}; a refused run applies nothing
     */
    public Result translate(List<DeltaOp> deltas) {
        final List<DeltaOp> out = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        final Map<String, String> freshByGhostId = new LinkedHashMap<>();
        for (DeltaOp op : deltas) {
            if (op.getType() == DeltaType.ADD) {
                final EditNodeAbstract subtree = (EditNodeAbstract) op.getSubtree();
                allocateFreshIds(subtree, freshByGhostId);
                rewire(subtree, freshByGhostId, problems);
                if (!problems.isEmpty()) {
                    break; // a dangling reference refuses the whole pick - stop early, nothing applied
                }
                out.add(op);
                continue;
            }
            if (op.getType() == DeltaType.MODIFY
                    && op.getProperty() == DeltaProperty.LINK_TARGET) {
                final String rewired = translateExternal(op.getNewValue(), problems);
                if (rewired == null) {
                    break; // refused
                }
                out.add(rewired.equals(op.getNewValue()) ? op : op.withNewValue(rewired));
                continue;
            }
            if (op.getType() == DeltaType.MODIFY
                    && op.getProperty() == DeltaProperty.OBJECT_ID) {
                // defensive: the engine reports identity changes as REMOVE + ADD, never MODIFY -
                // an identity delta from a ghost would partially roll a reroll back and never applies
                continue;
            }
            out.add(op);
        }
        return problems.isEmpty()
                ? new Result(true, out, problems)
                : new Result(false, new ArrayList<>(), problems);
    }

    /**
     * Pass 1 of an ADD subtree: every ghost id of the picked content becomes a fresh one,
     * disjoint from the live world and from earlier picks of this translation.
     */
    private void allocateFreshIds(EditNodeAbstract subtree, Map<String, String> freshByGhostId) {
        final List<EditNodeObject> nodes = new ArrayList<>();
        collectObjects(subtree, nodes);
        for (EditNodeObject obj : nodes) {
            if (obj.getObjektId() != null && !obj.getObjektId().isEmpty()
                    && !freshByGhostId.containsKey(obj.getObjektId())) {
                freshByGhostId.put(obj.getObjektId(), nextFreshId(freshByGhostId));
            }
        }
    }

    /**
     * Pass 2 of an ADD subtree: validate every identity and reference first, apply the rewiring
     * only when everything resolves - a refusal leaves the picked content completely untouched
     * (the theirs copy is disposable, but a refused pick should not smear it either).
     */
    private void rewire(EditNodeAbstract subtree, Map<String, String> freshByGhostId,
            List<String> problems) {
        final List<EditNodeObject> nodes = new ArrayList<>();
        collectObjects(subtree, nodes);
        // validate first: one dangling reference refuses the whole pick before any mutation
        for (EditNodeObject obj : nodes) {
            if (obj.isLinkReference() && obj.getLinkTarget() != null) {
                if (translateInternalOrExternal(obj.getLinkTarget(), freshByGhostId, problems) == null) {
                    return;
                }
            }
        }
        if (!problems.isEmpty()) {
            return;
        }
        // apply: fresh ids, rewired references
        for (EditNodeObject obj : nodes) {
            if (obj.getObjektId() != null && freshByGhostId.containsKey(obj.getObjektId())) {
                obj.setObjektId(freshByGhostId.get(obj.getObjektId()));
            }
            if (obj.isLinkReference() && obj.getLinkTarget() != null) {
                final String rewired = translateInternalOrExternal(
                        obj.getLinkTarget(), freshByGhostId, problems);
                if (rewired != null && !rewired.equals(obj.getLinkTarget())) {
                    obj.setLinkTarget(rewired);
                }
            }
        }
    }

    private static void collectObjects(EditNodeAbstract node, List<EditNodeObject> sink) {
        if (node instanceof EditNodeObject obj) {
            sink.add(obj);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeAbstract child) {
                collectObjects(child, sink);
            }
        }
    }

    /**
     * One reference of picked content: internal (the id belongs to the pick) or external (the id
     * must exist in the live world under the translated synonym).
     */
    private String translateInternalOrExternal(String link, Map<String, String> freshByGhostId,
            List<String> problems) {
        final int split = link.indexOf("::");
        if (split < 0) {
            problems.add("Malformed reference '" + link + "' - a qualified syn::id reference is expected.");
            return null;
        }
        final String synonym = link.substring(0, split);
        final String id = link.substring(split + 2);
        // self references keep their point of view; only the id part rewires
        if ("this".equals(synonym) || "self".equals(synonym)) {
            if (!freshByGhostId.containsKey(id)) {
                problems.add("The reference '" + link + "' points outside the pick, but the pick's "
                        + "own file does not know the id in the live world - dangling, the pick refuses.");
                return null;
            }
            return synonym + "::" + freshByGhostId.get(id);
        }
        if (freshByGhostId.containsKey(id)) {
            // internal: the target travels with the pick; the file's live synonym replaces the ghost one
            final String liveSynonym = liveSynonymOfGhostSynonym.get(synonym);
            if (liveSynonym == null) {
                // the target is inside the pick: its file equals the destination file, whose live
                // synonym is the ghost synonym's correspondent - unknown means the ghost names a
                // file the live world does not carry
                problems.add("The reference '" + link + "' names the synonym '" + synonym
                        + "' that no live tree answers to - the pick refuses.");
                return null;
            }
            return liveSynonym + "::" + freshByGhostId.get(id);
        }
        return translateExternal(link, problems);
    }

    /**
     * One external reference (into the live world): synonym check first, conflict check second -
     * the id must exist under the translated synonym, otherwise the pick refuses whole.
     */
    private String translateExternal(String link, List<String> problems) {
        final int split = link.indexOf("::");
        if (split < 0) {
            problems.add("Malformed reference '" + link + "' - a qualified syn::id reference is expected.");
            return null;
        }
        final String synonym = link.substring(0, split);
        final String id = link.substring(split + 2);
        final String liveSynonym = liveSynonymOfGhostSynonym.get(synonym);
        if (liveSynonym == null) {
            problems.add("The reference '" + link + "' names the synonym '" + synonym
                    + "' that no live tree answers to (the live world renamed or dropped it)"
                    + " - the pick refuses.");
            return null;
        }
        final String qualified = liveSynonym + "::" + id;
        if (!oursQualifiedIds.contains(qualified)) {
            problems.add("The reference '" + link + "' does not resolve in the live world as '"
                    + qualified + "' (the id was rerolled away or removed)"
                    + " - set the reference manually after the pick, or include the target.");
            return null;
        }
        return qualified;
    }

    /**
     * The next fresh identity: the {@code r}-prefixed running number free in the live world.
     */
    private String nextFreshId(Map<String, String> freshByGhostId) {
        Set<String> taken = new LinkedHashSet<>(oursRawIds);
        taken.addAll(freshByGhostId.values());
        int count = 1;
        while (taken.contains(FRESH_ID_PREFIX + count)) {
            count++;
        }
        return FRESH_ID_PREFIX + count;
    }

    //
    // session accessors (both sides feed the anchor)
    //

    private static List<JsonNode> providersOf(EditTree tree) {
        final JsonNode providers = tree.getProvidersNode();
        return providers != null ? providers.asArray() : null;
    }

    private static String textOf(JsonNode entry, String key) {
        final Map<String, JsonNode> members = entry != null ? entry.asObjectValues() : null;
        final JsonNode node = members != null ? members.get(key) : null;
        return node != null ? node.asText() : null;
    }

}
