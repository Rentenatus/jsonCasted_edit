/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests for the unique-field semantics of EditNodeProperty.tryAssignType: fields that are unique in the model are
 * always adopted by the property node - even without a parent type or under a parent type that does not declare
 * them - while ambiguous fields are only resolved against the parent type. A later type correction of the parent
 * re-resolves the field cleanly (the parser's requeue cascade does this in production).
 *
 * @author Janusch Rentenatus
 */
public class EditNodePropertyUniqueNGTest {

    private JsonModelDescriptor model;
    private JsonTypeDescriptor typeA;
    private JsonTypeDescriptor typeOther;
    private EditNodeObject parent;
    private EditNodeProperty uniqueProp;
    private EditNodeProperty ambiguousProp;

    /**
     * Builds a model with one unique field (declared by TypeA only) and one ambiguous field (declared by TypeA and
     * TypeOther), plus a parent object with a property for each.
     */
    @BeforeMethod
    public void setUp() {
        model = new JsonModelDescriptor("Test");
        typeA = new JsonTypeDescriptor("TypeA");
        typeA.addField(new JsonFieldDescriptor("uniqueField", "java.lang.String"));
        typeA.addField(new JsonFieldDescriptor("sharedField", "java.lang.String"));
        model.addType(typeA);
        typeOther = new JsonTypeDescriptor("TypeOther");
        typeOther.addField(new JsonFieldDescriptor("sharedField", "java.lang.String"));
        model.addType(typeOther);

        parent = new EditNodeObject("parent");
        uniqueProp = new EditNodeProperty("uniqueField");
        parent.addChild(uniqueProp, new EditTimes());
        ambiguousProp = new EditNodeProperty("sharedField");
        parent.addChild(ambiguousProp, new EditTimes());
    }

    /**
     * A unique field is adopted blindly when the parent has no type descriptor - the property node is fertilized
     * with the field and warns about the missing context.
     */
    @Test
    public void testUniqueFieldAdoptedWithoutParentType() {
        assertTrue(uniqueProp.tryAssignType(model), "Unique field must be adopted without a parent type");
        assertNotNull(uniqueProp.getJsonField(), "Unique field must be assigned to the property node");
        assertEquals(uniqueProp.getJsonField().getFieldName(), "uniqueField");
        assertEquals(uniqueProp.getEditStatus(), EditStatus.WARNING, "Missing parent context must be visible");
        assertTrue(uniqueProp.getEditMessage().contains("adopted as unique"),
                "Message must explain the blind adoption: " + uniqueProp.getEditMessage());
    }

    /**
     * A unique field under a parent type that does not declare it is adopted with a warning; when the parent type
     * is later corrected to the declaring type, the same parse call resolves the field cleanly against the parent.
     */
    @Test
    public void testUniqueFieldSurvivesParentTypeCorrection() {
        parent.setJsonType(typeOther);
        assertTrue(uniqueProp.tryAssignType(model), "Unique field must be adopted under a foreign parent type");
        assertNotNull(uniqueProp.getJsonField(), "Unique field must be assigned under a foreign parent type");
        assertTrue(uniqueProp.getEditMessage().contains("not declared in type 'TypeOther'"),
                "Message must name the foreign parent type: " + uniqueProp.getEditMessage());

        // Typkorrektur des Parents: erneutes Parsen (im Produktionssystem via queueChildrenForReparse)
        parent.setJsonType(typeA);
        assertTrue(uniqueProp.tryAssignType(model), "After the parent type correction the field must resolve");
        assertEquals(uniqueProp.getEditStatus(), EditStatus.OKAY, "After the correction the node must be okay");
        assertSame(uniqueProp.getJsonField(), typeA.getField("uniqueField"),
                "After the correction the field must come from the parent type");
    }

    /**
     * An ambiguous field without parent type is not adopted; it stays with a context warning.
     */
    @Test
    public void testAmbiguousFieldNotAdoptedWithoutParentType() {
        assertFalse(ambiguousProp.tryAssignType(model), "Ambiguous field must not be adopted blindly");
        assertNull(ambiguousProp.getJsonField(), "Ambiguous field must not be assigned");
        assertTrue(ambiguousProp.getEditMessage().contains("ambiguous"),
                "Message must explain the ambiguity: " + ambiguousProp.getEditMessage());
    }

    /**
     * An ambiguous field resolves against the parent type as soon as the parent has a type that declares it.
     */
    @Test
    public void testAmbiguousFieldResolvesAgainstParentType() {
        parent.setJsonType(typeA);
        assertTrue(ambiguousProp.tryAssignType(model), "Ambiguous field must resolve against the parent type");
        assertEquals(ambiguousProp.getEditStatus(), EditStatus.OKAY);
        assertSame(ambiguousProp.getJsonField(), typeA.getField("sharedField"),
                "The field must come from the parent type");
    }
}
