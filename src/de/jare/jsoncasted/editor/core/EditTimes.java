/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors and generates timestamps for EditTree operations.
 * Provides unique time values for tracking tree construction and modification events.
 * Uses atomic counters for thread-safe timestamp generation.
 *
 * @author Janusch Rentenatus
 */
public class EditTimes {

    private final AtomicLong counter = new AtomicLong(Long.MIN_VALUE + 1);

    public long update() {
        return counter.incrementAndGet();
    }

    public long getTimes() {
        return counter.get();
    }

}
