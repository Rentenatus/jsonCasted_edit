/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsonconfig.def.JsonConfigDefinition;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Tests for the map semantics of the on-the-fly parser: map keys are dynamic
 * and legitimate (no field resolution, no "unknown in model" error), the map
 * object adopts the mapped instance type, and elements under map keys adopt
 * the mapped value type from {@code mappingAllFields}.
 *
 * @author Janusch Rentenatus
 */
public class MapSemanticsNGTest {

    /**
     * Map keys under a map-typed object parse OKAY, the map object carries the
     * mapped instance type and array elements under map keys adopt the mapped
     * value type (String for the labels map).
     */
    @Test
    public void testMapKeysAreLegitimateAndValuesAreTyped() throws Exception {
        final EditNodeObject root = new EditNodeObject("seedConfig");
        final EditNodeProperty profilesProp = new EditNodeProperty("profiles");
        root.addChild(profilesProp, new EditTimes());
        final EditNodeObject profileObj = new EditNodeObject("Object", "{...}");
        profilesProp.addChild(profileObj, new EditTimes());
        final EditNodeProperty featuresProp = new EditNodeProperty("features");
        profileObj.addChild(featuresProp, new EditTimes());
        final EditNodeObject featureObj = new EditNodeObject("Object", "{...}");
        featuresProp.addChild(featureObj, new EditTimes());

        final EditNodeProperty settingsProp = new EditNodeProperty("settings");
        featureObj.addChild(settingsProp, new EditTimes());
        final EditNodeObject settingsMap = new EditNodeObject("Object", "{...}");
        settingsProp.addChild(settingsMap, new EditTimes());
        final EditNodeProperty host1 = new EditNodeProperty("host1");
        settingsMap.addChild(host1, new EditTimes());
        host1.setValue("http://localhost");

        final EditNodeProperty labelsProp = new EditNodeProperty("labels");
        featureObj.addChild(labelsProp, new EditTimes());
        final EditNodeObject labelsMap = new EditNodeObject("Object", "{...}");
        labelsProp.addChild(labelsMap, new EditTimes());
        final EditNodeProperty system0 = new EditNodeProperty("system0");
        labelsMap.addChild(system0, new EditTimes());
        final EditNodeObject lineA = new EditNodeObject("erste Zeile");
        system0.addChild(lineA, new EditTimes());
        final EditNodeObject lineB = new EditNodeObject("zweite Zeile");
        system0.addChild(lineB, new EditTimes());

        final EditNodeProperty enablementsProp = new EditNodeProperty("enablements");
        featureObj.addChild(enablementsProp, new EditTimes());
        final EditNodeObject enablementsMap = new EditNodeObject("Object", "{...}");
        enablementsProp.addChild(enablementsMap, new EditTimes());
        final EditNodeProperty rewriteStory = new EditNodeProperty("rewriteStory");
        enablementsMap.addChild(rewriteStory, new EditTimes());
        rewriteStory.setValue("true");

        final EditTree tree = new EditTree(root, new EditTimes());
        root.setCastName("de.jare.jsonconfig.item.ConfigRoot");
        tree.setJsonModelDescriptor(JsonConfigDefinition.getInstance().getDescriptor());
        waitForParser(tree);

        assertEquals(settingsMap.getCastName(), "de.jare.jsoncasted.lang.JsonInstance<String>",
                "The settings map object must carry the mapped instance type");
        assertEquals(host1.getEditStatus(), EditStatus.OKAY,
                "A map key must not be reported as an unknown field: " + host1.getEditMessage());

        assertEquals(labelsMap.getCastName(), "de.jare.jsoncasted.lang.JsonInstance<String>[]",
                "The labels map object must carry the mapped instance array type");
        assertEquals(system0.getEditStatus(), EditStatus.OKAY,
                "A map key of an array-valued map must be okay: " + system0.getEditMessage());
        assertEquals(lineA.getCastName(), "String",
                "Elements under a map key must adopt the mapped value type");
        assertEquals(lineA.getEditStatus(), EditStatus.OKAY,
                "The map value element must be okay: " + lineA.getEditMessage());
        assertEquals(lineB.getCastName(), "String",
                "Every element under a map key must adopt the mapped value type");

        assertEquals(enablementsMap.getCastName(), "de.jare.jsoncasted.lang.JsonInstance<Boolean>",
                "The enablements map object must carry the mapped boolean instance type");
        assertEquals(rewriteStory.getEditStatus(), EditStatus.OKAY,
                "A boolean map key must not be reported as an unknown field: " + rewriteStory.getEditMessage());
    }

    /**
     * Waits until the on-the-fly parser has processed the queue and settled.
     *
     * @param tree the tree to wait for
     * @throws InterruptedException if the sleep is interrupted
     */
    private static void waitForParser(EditTree tree) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (tree.getParseQueue().isEmpty() && tree.getPendingNodes().isEmpty()) {
                Thread.sleep(150);
                if (tree.getParseQueue().isEmpty() && tree.getPendingNodes().isEmpty()) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Parser did not settle within the timeout");
    }
}
