/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;

/**
 * Service class for on-the-fly type parsing of EditTree nodes.
 * 
 * <p>
 * This service runs in a separate thread and processes nodes from the parse queue,
 * performing type assignment based on the {@link JsonModelDescriptor} from the tree.
 * It is designed to be thread-safe and non-blocking for the UI.
 * </p>
 * 
 * <p>
 * The service:
 * <ul>
 * <li>Takes nodes from the parse queue ({@link EditTree#getParseQueue()})</li>
 * <li>Uses the tree's JsonModelDescriptor for type inference</li>
 * <li>Updates ParseState on nodes (EDITED -> PENDING -> DONE)</li>
 * <li>Sets EditStatus (OKAY/WARNING/ERROR) based on parsing results</li>
 * <li>Triggers re-parsing of parent nodes when child types change</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <strong>Thread Safety:</strong> This class is designed to run in its own thread
 * and uses thread-safe collections from EditTree.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public class TypeParserService {

    /**
     * The tree this parser service is working on.
     */
    private final EditTree editTree;

    /**
     * Flag to control the parser thread lifecycle.
     */
    private volatile boolean running;

    /**
     * Thread that runs the parser.
     */
    private Thread parserThread;

    /**
     * Creates a new TypeParserService for the specified EditTree.
     *
     * @param editTree the tree to parse (must not be null)
     * @throws IllegalArgumentException if editTree is null
     */
    public TypeParserService(EditTree editTree) {
        if (editTree == null) {
            throw new IllegalArgumentException("EditTree cannot be null");
        }
        this.editTree = editTree;
        this.running = false;
    }

    /**
     * Starts the parser thread.
     * The thread will continuously process nodes from the parse queue.
     */
    public void start() {
        if (running) {
            return; // Already running
        }
        running = true;
        parserThread = new Thread(this::parseLoop, "TypeParserService-Thread");
        parserThread.setDaemon(true); // Daemon thread, won't prevent JVM exit
        parserThread.start();
    }

    /**
     * Stops the parser thread gracefully.
     * Waits for the current parsing operation to complete.
     */
    public void stop() {
        running = false;
        if (parserThread != null) {
            try {
                parserThread.join(1000); // Wait up to 1 second for graceful shutdown
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Returns whether the parser service is currently running.
     *
     * @return true if the parser thread is running, false otherwise
     */
    public boolean isRunning() {
        return running && parserThread != null && parserThread.isAlive();
    }

    /**
     * Returns the EditTree this service is working on.
     *
     * @return the edit tree
     */
    public EditTree getEditTree() {
        return editTree;
    }

    /**
     * The main parsing loop.
     * Continuously processes nodes from the queue while the service is running.
     */
    private void parseLoop() {
        while (running) {
            try {
                // Get the next node from the queue (blocks briefly if empty)
                EditNodeAbstract node = editTree.getParseQueue().poll();
                
                if (node == null) {
                    // Queue is empty, sleep briefly to avoid busy waiting
                    Thread.sleep(10);
                    continue;
                }

                // Process the node
                parseNode(node);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Log error but continue processing other nodes
                System.err.println("Error in TypeParserService: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Parses a single node and updates its type information.
     * This is a placeholder for the actual parsing logic (to be implemented in Phase 6).
     *
     * @param node the node to parse
     */
    private void parseNode(EditNodeAbstract node) {
        // This is a placeholder - actual implementation will be added in Phase 6
        // 
        // For now, just mark as DONE to prevent re-queueing
        // In the real implementation, this will:
        // 1. Check if node still needs parsing (hash comparison)
        // 2. Use JsonModelDescriptor to assign types
        // 3. Handle parent type inference
        // 4. Set EditStatus based on results
        // 5. Trigger re-parsing of affected nodes
        
        node.setParseState(ParseState.DONE);
        editTree.removeFromPending(node);
    }

    /**
     * Triggers re-parsing for a node and its subtree if needed.
     * This method can be called from the UI thread to request parsing.
     *
     * @param node the node to re-parse
     */
    public void requestParse(EditNodeAbstract node) {
        if (node == null || editTree == null) {
            return;
        }
        
        // Mark as EDITED to trigger re-parsing
        node.setParseState(ParseState.EDITED);
        
        // Add to queue
        editTree.addToParseQueue(node);
    }

    /**
     * Triggers parsing for the entire tree.
     * Marks all nodes as EDITED and adds the root to the queue.
     */
    public void requestFullParse() {
        if (editTree == null) {
            return;
        }
        
        // For now, just add the root node
        // The actual implementation will traverse and mark all nodes
        EditNodeAbstract root = editTree.getRoot();
        if (root != null) {
            root.setParseState(ParseState.EDITED);
            editTree.addToParseQueue(root);
        }
    }

}
