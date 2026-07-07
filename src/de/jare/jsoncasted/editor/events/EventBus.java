/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.events;

import de.jare.tree.control.Orator;

/**
 * Thread-safe event bus for distributing events to registered listeners. This
 * is a type-safe event dispatching mechanism that replaces the Orator class.
 *
 *
 * @author Jansuch Rentenatus
 */
public class EventBus {

    private final Orator<HistoryListener> historyOrator;

    /**
     * Creates a new EventBus instance.
     */
    public EventBus() {
        historyOrator = new Orator<>();
    }

    /**
     * Adds a listener for a specific event type. The listener will be notified
     * whenever an event of the specified type is fired.
     *
     * @param listener the consumer to be called when an event is fired
     * @throws IllegalArgumentException if listener is null
     */
    public void addListener(HistoryListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        historyOrator.addListener(listener);
    }

    /**
     * Adds a listener for a specific event type at a specified priority level.
     * The listener will be notified whenever an event of the specified type is fired.
     *
     * @param level the priority level of this listener (higher values are notified first)
     * @param listener the consumer to be called when an event is fired
     * @throws IllegalArgumentException if listener is null
     */
    public void addListener(int level, HistoryListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        historyOrator.addListener(level, listener);
    }

    /**
     * Removes a listener for a specific event type.
     *
     * @param listener the consumer to remove
     * @return true if the listener was removed
     */
    public boolean removeListener(HistoryListener listener) {
        if (listener == null) {
            return false;
        }
        return historyOrator.removeListener(listener);

    }

    /**
     * Fires an event to all registered listeners for its type. Also fires to
     * listeners of supertypes and interfaces.
     *
     * @param event the event to fire
     */
    public void fireEvent(HistoryEvent event) {
        if (event == null) {
            return;
        }

        HistoryEvent.ChangeType changeType = event.getChangeType();
        if (HistoryEvent.ChangeType.HIST_CLEARED == changeType) {
            historyOrator.say((level, l) -> l.onClear(event));
        } else {
            historyOrator.say((level, l) -> l.onAction(event));
        }

    }

    /**
     * Removes all listeners from this event bus.
     */
    public void clear() {
        historyOrator.clear();
    }

    /**
     * Returns the number of listener registrations.
     *
     * @return the total number of listeners
     */
    public int getListenerCount() {
        return historyOrator.getListenerCount();
    }

    @Override
    public String toString() {
        return "EventBus[listeners=" + getListenerCount() + "]";

    }
}
