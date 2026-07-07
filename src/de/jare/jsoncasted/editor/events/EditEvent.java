/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.events;

/**
 * Base interface for all events in the editor event system. Events are used to
 * notify listeners about changes in the tree structure, selection, commands,
 * and other editor state.
 *
 * @author Jansuch Rentenatus
 */
public interface EditEvent {

    /**
     * Returns the source of this event. The source is typically the object that
     * triggered the event.
     *
     * @return the event source, may be null
     */
    Object getSource();

    /**
     * Returns the timestamp when this event was created.
     *
     * @return the timestamp in milliseconds since epoch
     */
    long getTimestamp();

    /**
     * Returns a string description of this event.
     *
     * @return a descriptive string
     */
    String getDescription();
}
