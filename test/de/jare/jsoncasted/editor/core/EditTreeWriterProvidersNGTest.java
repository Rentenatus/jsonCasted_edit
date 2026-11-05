/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.lang.JsonNode;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * The provider round trip fix (concept CONCEPT_ProjectView.md, phase 8a): the root's
 * {@code _woodProviders} member is file level wiring metadata without editable representation. The
 * import captures it on the tree and the writer writes it back - a save may never strip the bundle
 * wiring of a file (the gap: any editor-save of a providers file dropped the entries silently).
 *
 * @author Janusch Rentenatus
 */
public class EditTreeWriterProvidersNGTest {

    /**
     * A file with providers keeps them through the editor session: the import captures the member,
     * the writer writes it back with synonym and filename - multiple entries survive in order.
     */
    @Test
    public void testProvidersSurviveTheEditorRoundTrip() throws Exception {
        final String json = "{\n"
                + "  \"_woodProviders\": [\n"
                + "    { \"synonym\": \"save\", \"filename\": \"./saveBox.json\" },\n"
                + "    { \"synonym\": \"core\", \"filename\": \"../shared/node.json\" }\n"
                + "  ],\n"
                + "  \"one\": { \"_woodLink\": \"save::123456\" }\n"
                + "}";
        final EditTree tree = JsonTreeConverter.fromJsonString(json, "testbox");
        assertNotNull(tree.getProvidersNode(), "the import captures the providers member");

        final String written = EditTreeWriter.toJsonString(tree);
        assertTrue(written.contains("\"_woodProviders\""), "the writer writes the member: " + written);
        assertTrue(written.contains("\"synonym\": \"save\""), "the first entry keeps its synonym: " + written);
        assertTrue(written.contains("\"filename\": \"./saveBox.json\""),
                "the first entry keeps its filename: " + written);
        assertTrue(written.contains("\"synonym\": \"core\""), "the second entry survives too: " + written);
        assertTrue(written.indexOf("\"synonym\": \"save\"") < written.indexOf("\"synonym\": \"core\""),
                "the entry order stays stable");

        // the written string parses again and the wiring is still there - the full round trip
        final EditTree reloaded = JsonTreeConverter.fromJsonString(written, "testbox");
        assertNotNull(reloaded.getProvidersNode(), "the reloaded file carries the providers again");
        assertTrue(reloaded.getProvidersNode().asArray().size() >= 2, "both entries parse back");
    }

    /**
     * A file without providers writes no member - the writer does not invent wiring that was not there.
     */
    @Test
    public void testNoProvidersMeansNoMember() throws Exception {
        final EditTree tree = JsonTreeConverter.fromJsonString("{\"a\": 1}", "plain");
        assertNull(tree.getProvidersNode());

        final String written = EditTreeWriter.toJsonString(tree);
        assertFalse(written.contains("_woodProviders"), "no providers in, no providers out: " + written);
    }

    /**
     * The first member of the first provider entry of the captured providers node.
     */
    private static JsonNode providerMember(EditTree tree, String memberName) {
        final JsonNode providers = tree.getProvidersNode();
        assertNotNull(providers, "the tree carries providers");
        final JsonNode first = providers.asArray().get(0);
        return first.asObjectValues().get(memberName);
    }
}
