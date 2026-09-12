/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service class for on-the-fly type parsing of EditTree nodes.
 * 
 * <p>
 * This service uses a thread pool with 1-2 threads to process nodes from the parse queue,
 * performing type assignment based on the {@link de.jare.jsoncasted.model.descriptor.JsonModelDescriptor}
 * from the tree. It is designed to be thread-safe and non-blocking for the UI.
 * </p>
 * 
 * <p>
 * The service:
 * <ul>
 * <li>Uses a fixed thread pool (2 threads) for concurrent processing</li>
 * <li>Takes nodes from the parse queue ({@link EditTree#getParseQueue()})</li>
 * <li>Uses the tree's JsonModelDescriptor for type inference</li>
 * <li>Updates ParseState on nodes (EDITED -> PENDING -> DONE)</li>
 * <li>Sets EditStatus (OKAY/WARNING/ERROR) based on parsing results</li>
 * <li>Triggers re-parsing of parent nodes when child types change</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <strong>Thread Safety:</strong> This class uses an ExecutorService with a fixed thread pool
 * and coordinates with thread-safe collections from EditTree.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public class TypeParserService {

    /**
     * Default number of threads in the parser thread pool.
     * Using 2 threads allows for concurrent parsing while maintaining order
     * through the queue mechanism.
     */
    public static final int DEFAULT_THREAD_POOL_SIZE = 2;

    /**
     * Timeout in seconds for graceful shutdown.
     */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    /**
     * The tree this parser service is working on.
     */
    private final EditTree editTree;

    /**
     * Thread pool for executing parse tasks.
     */
    private ExecutorService executorService;

    /**
     * Flag indicating if the service is running.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Number of threads in the pool.
     */
    private final int threadPoolSize;

    /**
     * Creates a new TypeParserService for the specified EditTree with default thread pool size.
     *
     * @param editTree the tree to parse (must not be null)
     * @throws IllegalArgumentException if editTree is null
     */
    public TypeParserService(EditTree editTree) {
        this(editTree, DEFAULT_THREAD_POOL_SIZE);
    }

    /**
     * Creates a new TypeParserService for the specified EditTree with custom thread pool size.
     *
     * @param editTree the tree to parse (must not be null)
     * @param threadPoolSize number of threads in the pool (must be at least 1)
     * @throws IllegalArgumentException if editTree is null or threadPoolSize < 1
     */
    public TypeParserService(EditTree editTree, int threadPoolSize) {
        if (editTree == null) {
            throw new IllegalArgumentException("EditTree cannot be null");
        }
        if (threadPoolSize < 1) {
            throw new IllegalArgumentException("Thread pool size must be at least 1");
        }
        this.editTree = editTree;
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * Starts the parser service with a fixed thread pool.
     * Each node from the queue will be processed by a thread from the pool.
     */
    public void start() {
        if (running.getAndSet(true)) {
            return; // Already running
        }
        
        // Create fixed thread pool with custom thread factory for naming
        executorService = Executors.newFixedThreadPool(
            threadPoolSize,
            r -> {
                Thread t = new Thread(r, "TypeParserService-Worker");
                t.setDaemon(true); // Daemon threads won't prevent JVM exit
                return t;
            }
        );
        
        // Start the queue processor thread that submits tasks to the pool
        // This allows us to control the rate at which nodes are processed
        Thread queueProcessor = new Thread(this::processQueue, "TypeParserService-QueueProcessor");
        queueProcessor.setDaemon(true);
        queueProcessor.start();
    }

    /**
     * Stops the parser service gracefully.
     * Waits for all running parse tasks to complete.
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return; // Already stopped
        }
        
        if (executorService != null) {
            // Shutdown the executor service
            executorService.shutdown();
            try {
                // Wait for all tasks to complete
                if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    // Force shutdown if tasks are still running
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executorService.shutdownNow();
            }
        }
    }

    /**
     * Returns whether the parser service is currently running.
     *
     * @return true if the service is running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
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
     * Returns the thread pool size.
     *
     * @return number of threads in the pool
     */
    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * The queue processor loop.
     * Continuously polls the queue and submits nodes to the thread pool for parsing.
     */
    private void processQueue() {
        while (running.get() || !editTree.getParseQueue().isEmpty()) {
            try {
                // Poll a node from the queue
                EditNodeAbstract node = editTree.getParseQueue().poll();
                
                if (node == null) {
                    // Queue is empty, sleep briefly to avoid busy waiting
                    Thread.sleep(10);
                    continue;
                }

                // Submit the node to the thread pool for parsing
                // This allows multiple nodes to be parsed concurrently
                if (running.get()) {
                    executorService.submit(() -> parseNodeSafely(node));
                } else {
                    // If service is stopping, mark as EDITED so it can be re-queued later
                    node.setParseState(ParseState.EDITED);
                    editTree.removeFromPending(node);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Log error but continue processing
                System.err.println("Error in TypeParserService queue processor: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Safely parses a single node with error handling.
     * This method wraps the actual parseNode call to prevent exceptions
     * from propagating to the thread pool.
     *
     * @param node the node to parse
     */
    private void parseNodeSafely(EditNodeAbstract node) {
        try {
            parseNode(node);
        } catch (Exception e) {
            // Log error for this specific node
            System.err.println("Error parsing node [editId=" + node.getEditId() + ", name=" + node.getName() + "]: " + e.getMessage());
            // Mark as EDITED so it can be retried
            node.setParseState(ParseState.EDITED);
            editTree.removeFromPending(node);
        }
    }

    /**
     * Parses a single node and updates its type information.
     * This is a placeholder for the actual parsing logic (to be implemented in Phase 6).
     *
     * <p>
     * Current implementation:
     * 1. Checks if node still needs parsing (hash comparison)
     * 2. Marks as DONE to prevent re-queueing
     * </p>
     *
     * <p>
     * Future implementation (Phase 6) will:
     * 1. Use JsonModelDescriptor to assign types
     * 2. Handle parent type inference
     * 3. Set EditStatus based on results
     * 4. Trigger re-parsing of affected nodes
     * </p>
     *
     * @param node the node to parse
     */
    private void parseNode(EditNodeAbstract node) {
        if (node == null) {
            return;
        }

        // Check if the node still needs parsing by comparing hash
        long currentHash = node.computeHash();
        long lastHash = node.getLastParsedHash();
        
        // If hash hasn't changed and we're already DONE, no need to re-parse
        if (currentHash == lastHash && node.getParseState() == ParseState.DONE) {
            editTree.removeFromPending(node);
            return;
        }

        // Update the last parsed hash
        node.setLastParsedHash(currentHash);

        // TODO: Phase 6 - Actual type parsing logic
        // For now, just mark as DONE
        // In the real implementation, this will:
        // 1. Use JsonModelDescriptor to assign types
        // 2. Handle parent type inference
        // 3. Set EditStatus based on results
        // 4. Trigger re-parsing of affected nodes
        
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
        
        EditNodeAbstract root = editTree.getRoot();
        if (root != null) {
            root.setParseState(ParseState.EDITED);
            editTree.addToParseQueue(root);
        }
    }

    /**
     * Returns the number of threads currently active in the pool.
     *
     * @return number of active threads, or 0 if service is not running
     */
    public int getActiveThreadCount() {
        if (executorService == null) {
            return 0;
        }
        // Note: This is approximate and may not be perfectly accurate
        // but gives a good indication of current load
        if (executorService instanceof java.util.concurrent.ThreadPoolExecutor) {
            java.util.concurrent.ThreadPoolExecutor tpe = 
                (java.util.concurrent.ThreadPoolExecutor) executorService;
            return tpe.getActiveCount();
        }
        return 0;
    }

    /**
     * Returns the approximate number of queued tasks.
     *
     * @return number of queued tasks
     */
    public int getQueuedTaskCount() {
        return editTree.getParseQueue().size();
    }

}
