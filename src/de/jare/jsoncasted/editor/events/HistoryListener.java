/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.jsoncasted.editor.events;

/**
 * Listener interface for history change events.
 * <p>
 * Implementations are notified when the history state changes, such as when
 * commands are executed, undone, redone, skipped, or when the history is cleared.
 * This allows components to react to history changes and update their UI state.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public interface HistoryListener {

    /**
     * Called when the history is cleared.
     *
     * @param event the history event containing details about the clear operation
     */
    void onClear(HistoryEvent event);

    /**
     * Called when a history action occurs (command executed, undone, redone, or skipped).
     *
     * @param event the history event containing details about the action
     */
    void onAction(HistoryEvent event);

}
