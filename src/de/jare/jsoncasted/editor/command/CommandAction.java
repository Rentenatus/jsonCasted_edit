/*
* Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v2.0 which
* accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

/**
 * Enumeration of possible command actions. Represents the type of operation
 * that can be performed on a command.
 *
 * @author Jansuch Rentenatus
 */
public enum CommandAction {
    /**
     * The command was executed normally.
     */
    EXECUTE,
    /**
     * The command's effect was undone.
     */
    UNDO,
    /**
     * The command was redone after being undone.
     */
    REDO,
    /**
     * The command was skipped after being undone.
     */
    SKIPPED,
    /**
     * The command was skipped due to errors.
     */
    DISALLOWED
}
