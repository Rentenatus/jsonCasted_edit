/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import de.jare.jsonconfig.def.JsonConfigDefinition;
import java.io.File;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Round-trip test for model descriptions: the Seed descriptor is saved as a description file and loaded back
 * through the same path the editor uses (JsonTreeConverter.loadDescrAndConvertRessourceToEditTree). Guards the
 * former defects where fields, accessors or constructor parameters were silently lost on the way.
 *
 * @author Janusch Rentenatus
 */
public class DescriptionRoundTripNGTest {

    /**
     * The Seed model descriptor must survive the save/load round trip: the field map must contain the declared
     * fields, and the loaded field descriptors must carry their getter and setter names.
     */
    @Test
    public void testSeedDescriptionRoundTrip() throws Exception {
        // 1. Seed-Descriptor erzeugen und als Beschreibung speichern
        JsonModelDescriptor seed = JsonConfigDefinition.getInstance().getDescriptor();
        assertNotNull(seed, "Seed descriptor must be created");

        File outDir = new File("./out");
        if (!outDir.exists()) {
            assertTrue(outDir.mkdirs(), "Failed to create output directory");
        }
        File descrFile = new File(outDir, "seedDescriptionRoundTrip.json");
        seed.saveAs(descrFile.getCanonicalPath());
        assertTrue(descrFile.exists() && descrFile.length() > 0, "Description file must be written");

        // 2. Beschreibung laden - derselbe Pfad wie JsonTreeConverter/JackMainActions
        EditTree tree = new EditTree(new EditNodeObject("seedConfig"), new EditTimes());
        JsonTreeConverter.loadDescrAndConvertRessourceToEditTree(
                tree, descrFile.getCanonicalPath(), descrFile);

        // 3. Der geladene Descriptor muss die deklarierten Felder kennen
        JsonModelDescriptor loaded = tree.getJsonModelDescriptor();
        assertNotNull(loaded, "Descriptor must be loaded from description file");

        Map<String, List<JsonFieldDescriptor>> fieldMap = loaded.getOrCreateFieldMap();
        List<JsonFieldDescriptor> commentsFields = fieldMap.get("comments");
        assertNotNull(commentsFields, "Field 'comments' must be known in the loaded model");
        assertFalse(commentsFields.isEmpty(), "Field 'comments' must have at least one declaration");

        // 4. Der ConfigRoot-Typ muss das Feld mit Accessoren zurueckgeben
        JsonTypeDescriptor rootType = loaded.getTypePerceptive("de.jare.jsonconfig.item.ConfigRoot");
        assertNotNull(rootType, "ConfigRoot type must be in the loaded model");
        JsonFieldDescriptor comments = rootType.getField("comments");
        assertNotNull(comments, "ConfigRoot must declare field 'comments'");
        assertEquals(comments.getGetter(), "getComments",
                "Field accessors must survive the round trip");
        assertEquals(comments.getSetter(), "setComments",
                "Field accessors must survive the round trip");
    }
}
