/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service class for on-the-fly type parsing of EditTree nodes.
 * Implements TypeParserListener to receive notifications about node changes.
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
 * <li>Implements {@link TypeParserListener} to receive change notifications</li>
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
public class TypeParserService implements TypeParserListener {

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
     * Also registers this service as the listener with the EditTree.
     * Each node from the queue will be processed by a thread from the pool.
     */
    public void start() {
        if (running.getAndSet(true)) {
            return; // Already running
        }
        
        // Register this service as the parser listener with the tree
        editTree.setParserListener(this);
        
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
        
        // Unregister this service as the listener
        editTree.setParserListener(null);
        
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
                    // Queue is empty, check if we should continue
                    if (!running.get() && editTree.getParseQueue().isEmpty()) {
                        break; // Stop if not running and queue is empty
                    }
                    // Sleep briefly to avoid busy waiting
                    Thread.sleep(10);
                    continue;
                }

                // Submit the node to the thread pool for parsing
                // This allows multiple nodes to be parsed concurrently
                if (running.get() && executorService != null && !executorService.isShutdown()) {
                    try {
                        executorService.submit(() -> parseNodeSafely(node));
                    } catch (RejectedExecutionException e) {
                        // Executor is shutting down, mark node as EDITED for retry
                        node.setParseState(ParseState.EDITED);
                        editTree.removeFromPending(node);
                    }
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
     * 
     * <p>
     * This method implements Phase 6 of the on-the-fly parser:
     * 1. Checks if node still needs parsing (hash comparison)
     * 2. Uses JsonModelDescriptor to assign types via tryAssignType()
     * 3. Handles parent type inference for EditNodeObject nodes
     * 4. Sets EditStatus based on parsing results
     * 5. Triggers re-parsing of affected nodes (children when parent type changes)
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

        // Set state to PENDING to indicate parsing is in progress
        node.setParseState(ParseState.PENDING);

        // Get the model descriptor from the tree
        JsonModelDescriptor model = editTree.getJsonModelDescriptor();
        
        if (model == null) {
            // No model descriptor available - set warning status
            node.setParseState(ParseState.DONE);
            node.setLastParsedHash(currentHash);
            node.setEditStatus(EditStatus.WARNING);
            node.setEditMessage("No model descriptor available for type parsing");
            editTree.removeFromPending(node);
            return;
        }

        // Perform type assignment using the existing tryAssignType method
        boolean typeAssigned = node.tryAssignType(model);
        
        // Update the last parsed hash after successful parsing
        node.setLastParsedHash(currentHash);
        
        // Mark as DONE
        node.setParseState(ParseState.DONE);
        
        // For EditNodeObject: try to infer parent type based on field name
        if (typeAssigned && node instanceof EditNodeObject) {
            tryInferParentTypes((EditNodeObject) node, model);
        }
        
        editTree.removeFromPending(node);
    }

    /**
     * Attempts to infer parent types for EditNodeObject nodes.
     * If a child node has a unique field name in the model, and the parent has no type,
     * the parent's type is set to the type that contains this field.
     * 
     * <p>
     * This implements the automatic type derivation feature: when a field name is unique
     * in the model and the parent node has no type, we set the parent's type and trigger
     * re-parsing of the parent.
     * </p>
     *
     * @param node the EditNodeObject that was just parsed
     * @param model the JsonModelDescriptor to use for type lookups
     */
    private void tryInferParentTypes(EditNodeObject node, JsonModelDescriptor model) {
        // Get the parent node
        EditNode parent = node.getParent();
        if (!(parent instanceof EditNodeObject)) {
            return; // Parent is not an object, cannot have type descriptor
        }
        
        EditNodeObject parentObject = (EditNodeObject) parent;
        
        // If parent already has a type, no need to infer
        if (parentObject.getJsonType() != null) {
            return;
        }
        
        // Get the node's name (which is the field name in the parent)
        String fieldName = node.getName();
        if (fieldName == null || fieldName.isEmpty()) {
            return;
        }
        
        // Find all types in the model that have a field with this name
        List<JsonTypeDescriptor> typesWithField = getTypesContainingField(model, fieldName);
        
        if (typesWithField.isEmpty()) {
            // No types found with this field name
            parentObject.setEditStatus(EditStatus.WARNING);
            parentObject.setEditMessage("No type found containing field '" + fieldName + "'");
            return;
        }
        
        if (typesWithField.size() == 1) {
            // Unique match - set the parent's type
            JsonTypeDescriptor parentType = typesWithField.get(0);
            parentObject.setJsonType(parentType);
            parentObject.setEditStatus(EditStatus.OKAY);
            parentObject.setEditMessage(null);
            
            // Mark parent as EDITED to trigger re-parsing
            parentObject.setParseState(ParseState.EDITED);
            editTree.addToParseQueue(parentObject);
            
        } else {
            // Multiple types found - ambiguous field name
            parentObject.setEditStatus(EditStatus.WARNING);
            parentObject.setEditMessage("Field '" + fieldName + "' is ambiguous (found in " + 
                    typesWithField.size() + " types)");
        }
    }

    /**
     * Finds all type descriptors in the model that contain a field with the specified name.
     * This is used for parent type inference when a child node's name matches a field.
     *
     * @param model the JsonModelDescriptor to search
     * @param fieldName the field name to search for
     * @return list of JsonTypeDescriptor that have a field with the given name
     */
    private List<JsonTypeDescriptor> getTypesContainingField(JsonModelDescriptor model, String fieldName) {
        List<JsonTypeDescriptor> result = new ArrayList<>();
        
        if (model == null || fieldName == null || fieldName.isEmpty()) {
            return result;
        }
        
        // Iterate through all types in the model
        for (JsonTypeDescriptor type : model.getTypes()) {
            if (type == null) {
                continue;
            }
            
            // Check if this type has a field with the specified name
            JsonFieldDescriptor field = type.getField(fieldName);
            if (field != null) {
                result.add(type);
            }
        }
        
        return result;
    }

    // ========== TypeParserListener Implementation ==========

    @Override
    public void onNodeNameChanged(EditNodeAbstract node, String oldName, String newName) {
        if (node == null) {
            return;
        }
        // Name changes can affect type inference
        // Add the node to the parse queue
        addNodeToParseQueue(node);
    }

    @Override
    public void onNodeValueChanged(EditNodeAbstract node, String oldValue, String newValue) {
        if (node == null) {
            return;
        }
        // Value changes may affect type validation
        // Add the node to the parse queue
        addNodeToParseQueue(node);
    }

    @Override
    public void onChildAdded(EditNodeAbstract parent, EditNodeAbstract child) {
        if (parent == null || child == null) {
            return;
        }
        // Adding a child may affect parent type inference and child type
        // Add both parent and child to the parse queue
        addNodeToParseQueue(parent);
        addNodeToParseQueue(child);
    }

    @Override
    public void onChildRemoved(EditNodeAbstract parent, EditNodeAbstract child) {
        if (parent == null || child == null) {
            return;
        }
        // Removing a child may affect parent type inference
        // Add parent to the parse queue
        addNodeToParseQueue(parent);
    }

    @Override
    public void onTypeDescriptorChanged(EditNodeAbstract node) {
        if (node == null) {
            return;
        }
        // Type descriptor changed, need to re-parse
        addNodeToParseQueue(node);
    }

    @Override
    public void onRequestReparse(EditNodeAbstract node) {
        if (node == null) {
            return;
        }
        // Direct request to re-parse
        addNodeToParseQueue(node);
    }

    /**
     * Helper method to add a node to the parse queue with proper state management.
     *
     * @param node the node to add to the parse queue
     */
    private void addNodeToParseQueue(EditNodeAbstract node) {
        if (node == null || editTree == null) {
            return;
        }
        
        // Mark as EDITED to trigger re-parsing
        node.setParseState(ParseState.EDITED);
        
        // Add to queue (deduplication handled by EditTree.addToParseQueue)
        editTree.addToParseQueue(node);
    }

    // ========== Public API for manual triggering ==========

    /**
     * Triggers re-parsing for a node and its subtree if needed.
     * This method can be called from the UI thread to request parsing.
     *
     * @param node the node to re-parse
     */
    public void requestParse(EditNodeAbstract node) {
        addNodeToParseQueue(node);
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
