/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core.validation;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAnnotation;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.item.JsonAnnotation;

/**
 * Validator for the editString annotation (annotation concept): the editString value must be a string array with
 * exactly one element, the display template of the described type. Zero or multiple elements are errors - a missing
 * template would silently disable the display formatting, multiple ones have no defined winner.
 *
 * <p>
 * The value forms follow the annotation concept: an array annotation carries its elements as row objects, an inline
 * string counts as its single element.</p>
 *
 * @author Janusch Rentenatus
 */
public class EditStringAnnotationValidator implements EditNodeValidator {

    /**
     * Diagnostic code of the single element rule.
     */
    public static final String CODE = "editstring.singleElement";

    @Override
    public void validate(EditNodeAbstract node, ValidationContext context) {
        if (!(node instanceof EditNodeAnnotation annotation)
                || !JsonAnnotation.EDIT_STRING.equals(annotation.getAnnotationName())) {
            return;
        }
        final int elements = elementCount(annotation);
        if (elements != 1) {
            context.addError(CODE,
                    "The editString annotation must carry exactly one template string, found " + elements,
                    annotation);
        }
    }

    /**
     * Counts the template elements of an editString annotation: the row objects of an array annotation, or the
     * inline primitive value as its single element.
     *
     * @param annotation the editString annotation node
     * @return the number of template elements
     */
    private static int elementCount(EditNodeAnnotation annotation) {
        if (annotation.getType() == JsonNodeType.ARRAY) {
            int count = 0;
            for (int i = 0; i < annotation.getChildCount(); i++) {
                if (annotation.getChildAt(i) instanceof EditNodeObject) {
                    count++;
                }
            }
            return count;
        }
        final String value = annotation.getValue();
        return value == null || value.isBlank() ? 0 : 1;
    }
}
