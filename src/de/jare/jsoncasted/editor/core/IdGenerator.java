/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe generator for unique node identifiers. Based on
 * de.jare.tree.data.IdGenerator.
 *
 * @author Janusch Rentenatus
 */
public class IdGenerator {

    public final static IdGenerator EDIT_ID_GENERATOR = new IdGenerator();

    private final AtomicLong counter = new AtomicLong(0L);

    /**
     * Returns the next ID. Each call increments the internal counter and
     * returns the new value.
     *
     * @return next edit ID (starting at 1)
     */
    public long nextId() {
        return counter.incrementAndGet();
    }
}
