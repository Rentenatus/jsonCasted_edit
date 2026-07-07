/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import java.util.Arrays;
import java.util.Objects;

/**
 *
 * @author Jansuch Rentenatus
 */
public final class CommandAvailability {

    public enum Status {
        ALLOWED,
        USELESS,
        DISALLOWED
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    private static final String[] NO_PARAMS = new String[0];

    private final Status status;
    private final String messageKey;
    private final String[] messageParams;
    private final Severity severity;

    private CommandAvailability(
            Status status,
            String messageKey,
            String[] messageParams,
            Severity severity) {

        this.status = Objects.requireNonNull(status, "status");
        this.messageKey = messageKey;
        this.messageParams = messageParams != null ? messageParams.clone() : NO_PARAMS;
        this.severity = severity != null ? severity : Severity.INFO;
    }

    public static CommandAvailability allowed() {
        return new CommandAvailability(Status.ALLOWED, null, NO_PARAMS, Severity.INFO);
    }

    public static CommandAvailability allowed(String messageKey, String... messageParams) {
        return new CommandAvailability(Status.ALLOWED, messageKey, messageParams, Severity.INFO);
    }

    public static CommandAvailability disallowed(String messageKey, String... messageParams) {
        return new CommandAvailability(Status.DISALLOWED, messageKey, messageParams, Severity.ERROR);
    }

    public static CommandAvailability useless(String messageKey, String... messageParams) {
        return new CommandAvailability(Status.USELESS, messageKey, messageParams, Severity.INFO);
    }

    public static CommandAvailability useless(Severity severity, String messageKey, String... messageParams) {
        return new CommandAvailability(Status.USELESS, messageKey, messageParams, severity);
    }

    public static CommandAvailability disallowed(Severity severity, String messageKey, String... messageParams) {
        return new CommandAvailability(Status.DISALLOWED, messageKey, messageParams, severity);
    }

    public boolean isAllowed() {
        return status == Status.ALLOWED;
    }

    public boolean isDisallowed() {
        return status == Status.DISALLOWED;
    }

    public boolean isUseless() {
        return status == Status.USELESS;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String[] getMessageParams() {
        return messageParams.clone();
    }

    public Severity getSeverity() {
        return severity;
    }

    public CommandAvailability withMessage(String messageKey, String... messageParams) {
        return new CommandAvailability(this.status, messageKey, messageParams, this.severity);
    }

    public CommandAvailability withSeverity(Severity severity) {
        return new CommandAvailability(this.status, this.messageKey, this.messageParams, severity);
    }

    @Override
    public String toString() {
        return "CommandAvailability["
                + "status=" + status
                + ", severity=" + severity
                + ", messageKey='" + messageKey + '\''
                + ", messageParams=" + Arrays.toString(messageParams)
                + ']';
    }
}
