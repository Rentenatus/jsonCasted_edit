/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.SimpleEntry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A transaction over several edit commands: one history entry, atomic undo (gap G2 of doc/TREE_DIFF.md in
 * jsonCasted). Executing runs the children in order; undoing runs them in reverse order — replaying N external
 * merge deltas as N single commands would otherwise mean N undo steps, which is what makes {@code SAFE_AUTO}
 * unusable without this composite.
 *
 * @author Janusch Rentenatus
 */
public class CompositeEditCommand extends AbstractEditCommand {

    private final List<EditCommand> commands;

    /**
     * Creates a transaction over the given commands. The list is copied; the description should say what the
     * transaction is about (e.g. "Merge: N external changes").
     *
     * @param description the transaction description
     * @param commands the commands to run, in execution order
     */
    public CompositeEditCommand(String description, List<EditCommand> commands) {
        super(CommandType.OTHER, description);
        this.commands = new ArrayList<>(commands);
    }

    /**
     * Returns the wrapped commands in execution order.
     *
     * @return the children, never {@code null}.
     */
    public List<EditCommand> getCommands() {
        return new ArrayList<>(commands);
    }

    @Override
    public CommandAvailability check(EditTree tree) {
        if (commands.isEmpty()) {
            return CommandAvailability.disallowed("editor.command.composite.empty");
        }
        for (EditCommand command : commands) {
            final CommandAvailability availability = command.check(tree);
            if (!availability.isAllowed()) {
                return availability; // the first blocking child blocks the whole transaction
            }
        }
        return CommandAvailability.allowed();
    }

    @Override
    protected CommandResult doExecute(EditTree tree, boolean redoAction) {
        final List<CommandResult> results = new ArrayList<>(commands.size());
        for (EditCommand command : commands) {
            results.add(command.execute(tree, redoAction));
        }
        return aggregate(results, redoAction ? CommandAction.REDO : CommandAction.EXECUTE);
    }

    @Override
    public void skipped() {
        super.skipped();
        for (EditCommand command : commands) {
            command.skipped();
        }
    }

    @Override
    protected CommandResult doUndo(EditTree tree) {
        final List<CommandResult> results = new ArrayList<>(commands.size());
        for (int i = commands.size() - 1; i >= 0; i--) {
            results.add(commands.get(i).undo(tree));
        }
        return aggregate(results, CommandAction.UNDO);
    }

    private CommandResult aggregate(List<CommandResult> results, CommandAction action) {
        final List<EditNodeAbstract> affected = new ArrayList<>();
        final List<SimpleEntry> templates = new ArrayList<>();
        final List<EditNodeAbstract> added = new ArrayList<>();
        final List<EditNodeAbstract> removed = new ArrayList<>();
        final List<EditNodeAbstract> updated = new ArrayList<>();
        final List<EditNodeAbstract> failed = new ArrayList<>();
        final Set<JackUpdateAction> updates = new LinkedHashSet<>();
        for (CommandResult result : results) {
            if (result == null) {
                continue;
            }
            collect(affected, result.getAffectedNodes());
            collect(templates, result.getTemplateEntries());
            collect(added, result.getAddedNodes());
            collect(removed, result.getRemovedNodes());
            collect(updated, result.getUpdatedNodes());
            collect(failed, result.getFailedNodes());
            for (JackUpdateAction update : result.getUpdateActions()) {
                updates.add(update);
            }
        }
        return new CommandResult(this, action,
                affected.toArray(new EditNodeAbstract[0]),
                templates.toArray(new SimpleEntry[0]),
                added.toArray(new EditNodeAbstract[0]),
                removed.toArray(new EditNodeAbstract[0]),
                updated.toArray(new EditNodeAbstract[0]),
                failed.toArray(new EditNodeAbstract[0]),
                updates.toArray(new JackUpdateAction[0]));
    }

    private static <T> void collect(List<T> target, T[] source) {
        if (source == null) {
            return;
        }
        for (T item : source) {
            if (item != null) {
                target.add(item);
            }
        }
    }
}
