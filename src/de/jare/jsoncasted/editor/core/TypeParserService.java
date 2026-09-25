/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.model.JsonCollectionType;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service class for on-the-fly type parsing of EditTree nodes.Implements
 TypeParserListener to receive notifications about node changes.<p>
 * This service uses a single parse worker to process nodes from the parse
 * queue, performing type assignment based on the
 * {@link de.jare.jsoncasted.model.descriptor.JsonModelDescriptor} from the
 * tree. It is designed to be thread-safe and non-blocking for the UI.
 * </p>
 *
 * <p>
 * The service:
 * <ul>
 * <li>Implements {@link TypeParserListener} to receive change
 * notifications</li>
 * <li>Uses a single parse worker: parse runs are serialized, one node at a
 * time. Parallel parsing bought nothing but races on shared state; the
 * single worker acts as the parse permit with an inherent finally
 * guarantee - a second parse can never overlap an in-flight one.</li>
 * <li>Takes nodes from the parse queue ({@link EditTree#getParseQueue()})</li>
 * <li>Uses the tree's JsonModelDescriptor for type inference</li>
 * <li>Updates ParseState on nodes (EDITED -> PENDING -> DONE)</li>
 * <li>Sets EditStatus (OKAY/WARNING/ERROR) based on parsing results</li>
 * <li>Triggers re-parsing of parent nodes when child types change</li>
 * </ul>
 * </p>
 *
 * <p>
 * <strong>Thread Safety:</strong> This class uses an ExecutorService with a
 * fixed thread pool and coordinates with thread-safe collections from EditTree.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public class TypeParserService implements TypeParserListener {

    private static final Logger LOGGER = Logger.getLogger(TypeParserService.class.getName());

    /**
     * Default number of threads in the parser worker pool. One worker
     * serializes the parse runs: while a node is being parsed, no second
     * parse can start, which removes the races on shared state (node states,
     * field map, requeue cascades) by concept instead of by lock.
     */
    public static final int DEFAULT_THREAD_POOL_SIZE = 1;

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
     * The queue processor thread, kept for a clean shutdown.
     */
    private Thread queueProcessor;

    /**
     * Flag indicating if the service is running.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Number of threads in the pool.
     */
    private final int threadPoolSize;

    /**
     * Creates a new TypeParserService for the specified EditTree with default
     * thread pool size.
     *
     * @param editTree the tree to parse (must not be null)
     * @throws IllegalArgumentException if editTree is null
     */
    public TypeParserService(EditTree editTree) {
        this(editTree, DEFAULT_THREAD_POOL_SIZE);
    }

    /**
     * Creates a new TypeParserService for the specified EditTree with custom
     * thread pool size.
     *
     * @param editTree the tree to parse (must not be null)
     * @param threadPoolSize number of threads in the pool (must be at least 1)
     * @throws IllegalArgumentException if editTree is null or threadPoolSize <
     * 1
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
     * Starts the parser service with a fixed thread pool. Also registers this
     * service as the listener with the EditTree. Each node from the queue will
     * be processed by a thread from the pool.
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
        queueProcessor = new Thread(this::processQueue, "TypeParserService-QueueProcessor");
        queueProcessor.setDaemon(true);
        queueProcessor.start();
    }

    /**
     * Stops the parser service gracefully. Waits for all running parse tasks to
     * complete.
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

        // Interrupt and join the queue processor so it does not linger.
        if (queueProcessor != null) {
            queueProcessor.interrupt();
            try {
                queueProcessor.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            queueProcessor = null;
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
     * The queue processor loop. Continuously polls the queue and submits nodes
     * to the thread pool for parsing.
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
                    // Wait for the parse signal of the next enqueue instead of
                    // burning cycles in a sleep poll.
                    editTree.awaitParseSignal();
                    continue;
                }

                // Submit the node to the thread pool for parsing
                // This allows multiple nodes to be parsed concurrently
                if (running.get() && executorService != null && !executorService.isShutdown()) {
                    try {
                        executorService.submit(() -> parseNodeSafely(node));
                    } catch (RejectedExecutionException e) {
                        // Executor is shutting down — mark as WARNING so the
                        // user sees the node was not parsed, store message.
                        LOGGER.log(Level.FINE, "Executor rejected node [editId="
                                + node.getEditId() + "]: " + e.getMessage(), e);
                        node.setEditStatus(EditStatus.WARNING);
                        node.setEditMessage("Parser shutting down, node will be re-parsed on next pass");
                        node.setParseState(ParseState.EDITED);
                        editTree.removeFromPending(node);
                    }
                } else {
                    // If service is stopping, mark as EDITED so it can be re-queued later.
                    node.setEditStatus(EditStatus.WARNING);
                    node.setEditMessage("Parser service not running, node pending re-parse");
                    node.setParseState(ParseState.EDITED);
                    editTree.removeFromPending(node);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Log error but continue processing
                LOGGER.log(Level.WARNING, "Error in TypeParserService queue processor: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Safely parses a single node with error handling. This method wraps the
     * actual parseNode call to prevent exceptions from propagating to the
     * thread pool.
     *
     * @param node the node to parse
     */
    private void parseNodeSafely(EditNodeAbstract node) {
        try {
            parseNode(node);
        } catch (Exception e) {
            // Log error for this specific node
            LOGGER.log(Level.WARNING, "Error parsing node [editId="
                    + node.getEditId() + ", name=" + node.getName() + "]: "
                    + e.getMessage(), e);
            // Store the error on the node so the user can see it in the UI.
            // The node is marked DONE (not EDITED) so it is not retried
            // indefinitely — the error is persistent until the underlying
            // problem is fixed.
            node.setEditStatus(EditStatus.ERROR);
            node.setEditMessage("Parse error: " + e.getMessage());
            node.setParseState(ParseState.DONE);
            node.setLastParsedHash(node.computeHash());
            editTree.removeFromPending(node);
        }
    }

    /**
     * Parses a single node and updates its type information.
     *
     * <p>
     * This method implements Phase 6 of the on-the-fly parser: 1. Checks if
     * node still needs parsing (hash comparison) 2. Uses JsonModelDescriptor to
     * assign types via tryAssignType() 3. Handles parent type inference for
     * EditNodeObject nodes 4. Sets EditStatus based on parsing results 5.
     * Triggers re-parsing of affected nodes (children when parent type changes)
     * </p>
     *
     * @param node the node to parse
     */
    private void parseNode(EditNodeAbstract node) {
        if (node == null) {
            return;
        }

        // Check if the node still needs parsing by comparing hash
        long currentHash;
        try {
            currentHash = node.computeHash();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to compute hash for node [editId="
                    + node.getEditId() + "]: " + e.getMessage(), e);
            node.setEditStatus(EditStatus.ERROR);
            node.setEditMessage("Failed to compute hash: " + e.getMessage());
            node.setParseState(ParseState.DONE);
            editTree.removeFromPending(node);
            return;
        }
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
            if (node.getEditStatus() != EditStatus.ERROR) {
                node.setEditStatus(EditStatus.WARNING);
                node.setEditMessage("No model descriptor available for type parsing");
            }
            editTree.removeFromPending(node);
            return;
        }

        // Capture the old type before assignment so we can detect changes
        JsonTypeDescriptor oldType = null;
        if (node instanceof EditNodeObject) {
            oldType = ((EditNodeObject) node).getJsonType();
        }

        // Element type propagation: object nodes under a collection property
        // (LIST/ARRAY) or under a single-object field take the element type
        // from the property's field descriptor, as long as they carry no
        // explicit cast name of their own. This types the anonymous "Object"
        // nodes the tree converter creates for nested values: array elements
        // and object-valued single fields alike. Single fields only
        // propagate when the element type is not primitive - object children
        // under a primitive field are not the converter's doing.
        if (node instanceof EditNodeObject && node.getParent() instanceof EditNodeProperty) {
            final EditNodeObject elementNode = (EditNodeObject) node;
            final EditNodeProperty containingProperty = (EditNodeProperty) node.getParent();
            final JsonFieldDescriptor containingField = containingProperty.getJsonField();
            if ((elementNode.getCastName() == null || elementNode.getCastName().isEmpty()
                    || elementNode.getCastName().equals(elementNode.getName()))
                    && containingField != null
                    && containingField.getCollectionType() != null) {
                final JsonTypeDescriptor elementType = model.getType(containingField.getTypeName());
                if (elementType != null
                        && (containingField.getCollectionType() != JsonCollectionType.NONE
                        || !elementType.isPrimitive())) {
                    elementNode.setCastName(elementType.getTypeName());
                }
            }
        }

        // Capture the old field of a property to detect field changes for the
        // element cascade below.
        JsonFieldDescriptor oldField = (node instanceof EditNodeProperty)
                ? ((EditNodeProperty) node).getJsonField() : null;

        // Perform type assignment using the existing tryAssignType method.
        // tryAssignType handles its own errors via EditStatus and never throws.
        boolean typeAssigned;
        try {
            typeAssigned = node.tryAssignType(model);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "tryAssignType threw for node [editId="
                    + node.getEditId() + ", name=" + node.getName() + "]: "
                    + e.getMessage(), e);
            node.setEditStatus(EditStatus.ERROR);
            node.setEditMessage("Type assignment error: " + e.getMessage());
            node.setLastParsedHash(currentHash);
            node.setParseState(ParseState.DONE);
            editTree.removeFromPending(node);
            return;
        }

        // Special handling for root node: if it has no type and no parent,
        // try to assign the root type configured on the EditTree.
        if (!typeAssigned && node instanceof EditNodeObject && node.getParent() == null) {
            EditNodeObject rootNode = (EditNodeObject) node;
            JsonTypeDescriptor rootType = editTree.getRootType();
            if (rootType != null) {
                rootNode.setJsonType(rootType);
                rootNode.setCastName(rootType.getTypeName());
                rootNode.markOkay(model);
                typeAssigned = true;
            }
        }

        // Update the last parsed hash after successful parsing
        node.setLastParsedHash(currentHash);

        // Mark as DONE
        node.setParseState(ParseState.DONE);

        // If the node is an EditNodeObject and its type changed, re-queue all
        // children so their fields can be resolved against the new parent type.
        if (node instanceof EditNodeObject) {
            JsonTypeDescriptor newType = ((EditNodeObject) node).getJsonType();
            if (newType != oldType && node.getChildCount() > 0) {
                queueChildrenForReparse(node);
            }
        }

        // If a property received its field anew, re-queue its children (the
        // collection elements) so they can adopt the element type.
        if (node instanceof EditNodeProperty
                && ((EditNodeProperty) node).getJsonField() != oldField
                && node.getChildCount() > 0) {
            queueChildrenForReparse(node);
        }

        // For EditNodeObject without an explicit cast decision: try to infer
        // the node's own type from the field names of its property children
        // (set coverage). This runs even after a failed name-based
        // assignment - a node whose cast still equals its name carries no
        // explicit decision and is exactly what inference is for.
        if (node instanceof EditNodeObject && hasNoExplicitCast((EditNodeObject) node)) {
            try {
                tryInferOwnType((EditNodeObject) node, model);
            } catch (Exception e) {
                // Inference is best-effort; if it fails the node is still
                // parsed, just without inferred type.
                LOGGER.log(Level.FINE, "tryInferOwnType failed for node [editId="
                        + node.getEditId() + "]: " + e.getMessage(), e);
            }
        }

        editTree.removeFromPending(node);
    }

    /**
     * Re-queues all children of the given node for re-parsing. Marks each child
     * as EDITED and adds it to the parse queue.
     *
     * @param parent the parent whose children need re-parsing
     */
    private void queueChildrenForReparse(EditNodeAbstract parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            EditNode child = parent.getChildAt(i);
            if (child instanceof EditNodeAbstract) {
                addNodeToParseQueue((EditNodeAbstract) child);
            }
        }
    }

    /**
     * Checks whether the node carries no explicit cast decision of its own: no type at all, or a cast that is still
     * identical to the node name (the anonymous converter naming). Only such nodes are candidates for type
     * inference - a propagated or manually set cast is an explicit decision.
     *
     * @param node the object node to check
     * @return true if the node has no explicit cast decision
     */
    private boolean hasNoExplicitCast(EditNodeObject node) {
        return node.getJsonType() == null
                || node.getCastName() == null
                || node.getCastName().isEmpty()
                || node.getCastName().equals(node.getName());
    }

    /**
     * Attempts to infer the type of an untyped EditNodeObject from the field names of its property children. The
     * candidates are the types declaring ALL of these fields (set coverage); if exactly one type covers the whole
     * set, it is assigned, the node is re-queued for a clean parse and its children are re-queued so their fields
     * resolve against the inferred type. This replaces the former single-name parent inference: a field set is far
     * more selective than one field name, so combinations like level+path resolve even when each name alone is
     * ambiguous.
     *
     * @param node the untyped EditNodeObject whose type should be inferred
     * @param model the JsonModelDescriptor to use for type lookups
     */
    private void tryInferOwnType(EditNodeObject node, JsonModelDescriptor model) {
        // Collect the field names of the property children.
        final List<String> fieldNames = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            final EditNode child = node.getChildAt(i);
            if (child instanceof EditNodeProperty) {
                final String fieldName = child.getName();
                if (fieldName != null && !fieldName.isEmpty()) {
                    fieldNames.add(fieldName);
                }
            }
        }
        if (fieldNames.isEmpty()) {
            return; // nothing to infer from
        }

        // Candidates: the types declaring ALL of the field names, via the
        // O(1) type index of the model descriptor.
        final Map<String, List<JsonTypeDescriptor>> typesByField = model.getOrCreateTypesByField();
        List<JsonTypeDescriptor> candidates = null;
        for (String fieldName : fieldNames) {
            final List<JsonTypeDescriptor> declaring = typesByField.get(fieldName);
            if (declaring == null || declaring.isEmpty()) {
                return; // unknown field: no guessing
            }
            if (candidates == null) {
                candidates = new ArrayList<>(declaring);
            } else {
                candidates.retainAll(declaring);
            }
            if (candidates.isEmpty()) {
                // Field combination fits no type: the property children
                // report the mismatch themselves - no inference.
                return;
            }
        }

        if (candidates == null || candidates.size() != 1) {
            if (candidates != null && candidates.size() > 1) {
                node.setEditStatus(EditStatus.WARNING);
                node.setEditMessage("Type is ambiguous by fields " + fieldNames
                        + " (" + candidates.size() + " candidate types)");
            }
            return;
        }

        final JsonTypeDescriptor inferred = candidates.get(0);
        node.setJsonType(inferred);
        node.setCastName(inferred.getTypeName());
        node.markOkay(model);

        // Re-queue the node (the hash now includes cast and type) and its
        // children so their fields resolve against the inferred type.
        node.setParseState(ParseState.EDITED);
        editTree.addToParseQueue(node);
        queueChildrenForReparse(node);
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
     * Helper method to add a node to the parse queue with proper state
     * management.
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
     * Triggers re-parsing for a node and its subtree if needed. This method can
     * be called from the UI thread to request parsing.
     *
     * @param node the node to re-parse
     */
    public void requestParse(EditNodeAbstract node) {
        addNodeToParseQueue(node);
    }

    /**
     * Triggers parsing for the entire tree. Marks all nodes as EDITED and adds
     * all nodes to the queue.
     */
    public void requestFullParse() {
        if (editTree == null) {
            return;
        }
        editTree.triggerFullReparse();
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
            java.util.concurrent.ThreadPoolExecutor tpe
                    = (java.util.concurrent.ThreadPoolExecutor) executorService;
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
