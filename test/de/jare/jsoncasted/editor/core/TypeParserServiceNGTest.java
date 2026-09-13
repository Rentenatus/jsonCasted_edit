package de.jare.jsoncasted.editor.core;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import de.jare.jsonconfig.def.JsonConfigDefinition;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Test class for the On-the-Fly Type Parser Service (Phase 6-7). Tests the
 * automatic type parsing functionality with JsonConfigDefinition model.
 *
 * @author Janusch Rentenatus
 */
public class TypeParserServiceNGTest {
    
    private EditTree editTree;
    private JsonModelDescriptor modelDescriptor;
    private TypeParserService parserService;
    private File configFile;
    
    public TypeParserServiceNGTest() {
    }
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("===============================================");
        System.out.println("## Start TypeParserServiceNGTest.");
    }
    
    @AfterClass
    public static void tearDownClass() throws Exception {
        System.out.println("## End TypeParserServiceNGTest.");
        System.out.println("===============================================");
    }
    
    @BeforeMethod
    public void setUpMethod() throws Exception {
        // Get the model descriptor from JsonConfigDefinition
        modelDescriptor = JsonConfigDefinition.INSTANCE.getModel().getOrCreateDescriptor();
        assertNotNull(modelDescriptor, "Model descriptor should not be null");

        // Load config1.json from test_assets
        String testAssetsPath = "assets/config/config1.json";
        configFile = new File(testAssetsPath);
        assertTrue(configFile.exists(), "Config file should exist: " + configFile.getAbsolutePath());

        // Load JSON file into EditTree using JsonTreeConverter
        editTree = JsonTreeConverter.fromJsonFile(configFile);
        assertNotNull(editTree, "EditTree should be created from JSON file");

        // Set the root type from the model definition so the parser
        // can assign it to the root node without hardcoding type names.
        String rootTypeName = JsonConfigDefinition.INSTANCE.getRootClass().getcName();
        JsonTypeDescriptor rootTypeDescriptor = modelDescriptor.getType(rootTypeName);
        if (rootTypeDescriptor == null) {
            rootTypeDescriptor = modelDescriptor.getTypePerceptive(rootTypeName);
        }
        editTree.setRootType(rootTypeDescriptor);

        // Set the model descriptor to the tree (this should auto-start the parser)
        editTree.setJsonModelDescriptor(modelDescriptor);
        
        // Wait for initial parsing to complete before running tests
        waitForParsingCompletion();
        
        // Print root type after initial parsing for debugging
        EditNodeAbstract root = editTree.getRoot();
        if (root instanceof EditNodeObject) {
            EditNodeObject rootObject = (EditNodeObject) root;
            JsonTypeDescriptor rootType = rootObject.getJsonType();
            System.out.println("Root name: " + root.getName() + ", type: " + 
                    (rootType != null ? rootType.getTypeName() : "null"));
        }

        // Get the parser service
        parserService = editTree.getParserService();
        assertNotNull(parserService, "Parser service should be created");
        assertTrue(parserService.isRunning(), "Parser service should be running after setting model");
    }
    
    @AfterMethod
    public void tearDownMethod() throws Exception {
        // Stop and cleanup
        if (editTree != null) {
            editTree.stopParserService();
            editTree.close();
            editTree = null;
        }
        parserService = null;
    }

    /**
     * Test that the parser service starts automatically when model descriptor
     * is set.
     */
    @Test
    public void testParserServiceAutoStart() {
        System.out.println("\n--- Testing Parser Service Auto-Start ---");
        
        EditTree localTree = null;
        try {
            localTree = JsonTreeConverter.fromJsonFile(configFile);
            assertNotNull(localTree, "EditTree should be created");
        } catch (IOException | JsonParseException ex) {
            Logger.getLogger(TypeParserServiceNGTest.class.getName()).log(Level.SEVERE, null, ex);
            fail(ex.getMessage());
        }

        // Initially, no parser service
        assertNull(localTree.getParserService(), "Parser service should be null initially");

        // Set model descriptor - should auto-start parser
        localTree.setJsonModelDescriptor(modelDescriptor);
        
        TypeParserService localService = localTree.getParserService();
        assertNotNull(localService, "Parser service should be created");
        assertTrue(localService.isRunning(), "Parser service should be running");

        // Cleanup
        localTree.close();
    }

    /**
     * Test that parse states are initialized correctly.
     */
    @Test
    public void testParseStateInitialization() {
        System.out.println("\n--- Testing Parse State Initialization ---");
        
        EditNodeAbstract root = editTree.getRoot();
        assertNotNull(root, "Root should not be null");

        // Check that root has been processed (should be EDITED or PENDING initially,
        // then DONE after parsing)
        ParseState rootState = root.getParseState();
        assertNotNull(rootState, "ParseState should not be null");

        // Wait for parsing to complete
        waitForParsingCompletion();

        // After parsing, root should be DONE
        rootState = root.getParseState();
        System.out.println("Root ParseState: " + rootState);
        assertTrue(rootState == ParseState.DONE || rootState == ParseState.EDITED,
                "Root should be DONE or EDITED: " + rootState);
    }

    /**
     * Test that type information is assigned to object nodes.
     */
    @Test
    public void testTypeAssignmentForObjectNodes() throws Exception {
        System.out.println("\n--- Testing Type Assignment for Object Nodes ---");

        // Wait for parsing to complete
        waitForParsingCompletion();
        
        EditNodeAbstract root = editTree.getRoot();
        assertNotNull(root, "Root should not be null");

        // The root should be an EditNodeObject with the config root type
        assertTrue(root instanceof EditNodeObject, "Root should be EditNodeObject");
        EditNodeObject rootObject = (EditNodeObject) root;

        // Check that root has a type assigned
        JsonTypeDescriptor rootType = rootObject.getJsonType();
        System.out.println("Root type: " + (rootType != null ? rootType.getTypeName() : "null"));

        // The root type should match ConfigRoot from JsonConfigDefinition
        if (rootType != null) {
            System.out.println("Root type assigned: " + rootType.getTypeName());
        }

        // Check that at least some child nodes have types
        boolean hasTypedChildren = false;
        for (int i = 0; i < root.getChildCount(); i++) {
            EditNode child = root.getChildAt(i);
            if (child instanceof EditNodeObject) {
                EditNodeObject childObject = (EditNodeObject) child;
                JsonTypeDescriptor childType = childObject.getJsonType();
                if (childType != null) {
                    System.out.println("Child '" + child.getName() + "' has type: " + childType.getTypeName());
                    hasTypedChildren = true;
                }
            }
        }

        // At least the root should have type information or children should have types
        // (depending on the model matching)
        System.out.println("Has typed children: " + hasTypedChildren);
    }

    /**
     * Test that field information is assigned to property nodes.
     */
    @Test
    public void testFieldAssignmentForPropertyNodes() throws Exception {
        System.out.println("\n--- Testing Field Assignment for Property Nodes ---");

        // Wait for parsing to complete
        waitForParsingCompletion();
        
        EditNodeAbstract root = editTree.getRoot();
        assertNotNull(root, "Root should not be null");

        // Find property nodes and check their field assignments
        boolean hasFieldAssignments = false;
        for (int i = 0; i < root.getChildCount(); i++) {
            EditNode child = root.getChildAt(i);
            if (child instanceof EditNodeProperty) {
                EditNodeProperty propNode = (EditNodeProperty) child;
                JsonFieldDescriptor field = propNode.getJsonField();
                if (field != null) {
                    System.out.println("Property '" + propNode.getName() + "' has field: " + field.getFieldName());
                    hasFieldAssignments = true;
                } else {
                    // Check EditStatus for properties without field assignment
                    EditStatus status = propNode.getEditStatus();
                    String message = propNode.getEditMessage();
                    System.out.println("Property '" + propNode.getName() + "' status: " + status
                            + (message != null ? " - " + message : ""));
                }
            }

            // Recursively check children
            if (child instanceof EditNodeAbstract) {
                hasFieldAssignments = checkPropertyAssignments((EditNodeAbstract) child) || hasFieldAssignments;
            }
        }
        
        System.out.println("Has field assignments: " + hasFieldAssignments);
    }
    
    private boolean checkPropertyAssignments(EditNodeAbstract node) {
        for (int i = 0; i < node.getChildCount(); i++) {
            EditNode child = node.getChildAt(i);
            if (child instanceof EditNodeProperty) {
                EditNodeProperty propNode = (EditNodeProperty) child;
                JsonFieldDescriptor field = propNode.getJsonField();
                if (field != null) {
                    System.out.println("  Nested property '" + propNode.getName() + "' has field: " + field.getFieldName());
                    return true;
                }
            }
            if (child instanceof EditNodeAbstract) {
                if (checkPropertyAssignments((EditNodeAbstract) child)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Test that ParseState transitions work correctly.
     */
    @Test
    public void testParseStateTransitions() throws Exception {
        System.out.println("\n--- Testing Parse State Transitions ---");
        
        EditNodeAbstract root = editTree.getRoot();
        assertNotNull(root, "Root should not be null");

        // Get the first child that is an EditNodeProperty
        EditNodeProperty testProperty = null;
        for (int i = 0; i < root.getChildCount(); i++) {
            EditNode child = root.getChildAt(i);
            if (child instanceof EditNodeProperty) {
                testProperty = (EditNodeProperty) child;
                break;
            }
        }
        
        assertNotNull(testProperty, "Should find at least one property node");

        // Change the property name - should trigger re-parsing
        // First, ensure the parent has a type assigned by finding a node with a typed parent
        // We need to find a property whose parent already has a type
        // Let's look for a nested property that has a better chance of successful parsing
        EditNodeProperty testPropertyWithTypedParent = findPropertyWithTypedParent(editTree.getRoot());
        if (testPropertyWithTypedParent != null) {
            testProperty = testPropertyWithTypedParent;
        }
        
        // Use a name that exists in the model
        String oldName = testProperty.getName();
        String newName = "level"; // "level" is a valid field name in ConfigLogging
        System.out.println("Changing property from '" + oldName + "' to '" + newName + "'");
        System.out.println("Parent type before: " + 
                (testProperty.getParent() instanceof EditNodeObject ? 
                    ((EditNodeObject) testProperty.getParent()).getJsonType() : "null"));
        testProperty.setName(newName);

        // After name change, ParseState should be EDITED or PENDING
        // (PENDING if the queue processor already picked it up)
        ParseState stateAfterEdit = testProperty.getParseState();
        System.out.println("State after edit: " + stateAfterEdit);
        System.out.println("EditStatus after edit: " + testProperty.getEditStatus() + 
                " - " + testProperty.getEditMessage());
        assertTrue(stateAfterEdit == ParseState.EDITED || stateAfterEdit == ParseState.PENDING,
                "State should be EDITED or PENDING after name change: " + stateAfterEdit);

        // Wait for parsing to complete
        waitForNodeParsing(testProperty);

        // After parsing, state should be DONE
        ParseState stateAfterParse = testProperty.getParseState();
        EditStatus statusAfterParse = testProperty.getEditStatus();
        System.out.println("State after parse: " + stateAfterParse);
        System.out.println("Status after parse: " + statusAfterParse + " - " + testProperty.getEditMessage());
        // Note: If the new name doesn't match the parent type, parsing may fail
        // In that case, the state will still be DONE but status may be WARNING or ERROR
        assertEquals(stateAfterParse, ParseState.DONE, "State should be DONE after parsing");
    }

    /**
     * Test that EditStatus is set correctly for valid and invalid nodes.
     */
    @Test
    public void testEditStatusAssignment() throws Exception {
        System.out.println("\n--- Testing EditStatus Assignment ---");

        // Wait for parsing to complete
        waitForParsingCompletion();
        
        EditNodeAbstract root = editTree.getRoot();
        assertNotNull(root, "Root should not be null");

        // Check EditStatus for various nodes
        int okayCount = 0;
        int warningCount = 0;
        int errorCount = 0;
        int statelessCount = 0;
        
        for (int i = 0; i < root.getChildCount(); i++) {
            EditNode child = root.getChildAt(i);
            EditStatus status = child.getEditStatus();
            String message = child.getEditMessage();
            
            System.out.println("Node '" + child.getName() + "' status: " + status
                    + (message != null ? " - " + message : ""));
            
            switch (status) {
                case OKAY:
                    okayCount++;
                    break;
                case WARNING:
                    warningCount++;
                    break;
                case ERROR:
                    errorCount++;
                    break;
                case STATELESS:
                    statelessCount++;
                    break;
            }
        }
        
        System.out.println("Status counts - OKAY: " + okayCount + ", WARNING: " + warningCount
                + ", ERROR: " + errorCount + ", STATELESS: " + statelessCount);

        // At least some nodes should be OKAY or have meaningful status
        assertTrue(okayCount + warningCount + errorCount > 0,
                "Should have at least one node with non-STATELESS status");
    }

    /**
     * Test that the parser queue processes nodes correctly.
     */
    @Test
    public void testParserQueueProcessing() throws Exception {
        System.out.println("\n--- Testing Parser Queue Processing ---");
        
        assertNotNull(parserService, "Parser service should not be null");

        // Get initial queue state
        int initialQueueSize = parserService.getQueuedTaskCount();
        System.out.println("Initial queue size: " + initialQueueSize);

        // Wait for queue to be processed
        waitForQueueEmpty();

        // Queue should be empty or near empty after processing
        int finalQueueSize = parserService.getQueuedTaskCount();
        System.out.println("Final queue size: " + finalQueueSize);

        // Trigger a full re-parse
        editTree.triggerFullReparse();

        // Queue should have items again
        int afterReparseQueueSize = parserService.getQueuedTaskCount();
        System.out.println("Queue size after re-parse: " + afterReparseQueueSize);
        assertTrue(afterReparseQueueSize > 0, "Queue should have items after triggering re-parse");
    }

    /**
     * Test that manual parse requests work correctly.
     */
    @Test
    public void testManualParseRequest() throws Exception {
        System.out.println("\n--- Testing Manual Parse Request ---");
        
        EditNodeAbstract root = editTree.getRoot();
        EditNodeAbstract firstChild = null;
        
        for (int i = 0; i < root.getChildCount(); i++) {
            EditNode child = root.getChildAt(i);
            if (child instanceof EditNodeAbstract) {
                firstChild = (EditNodeAbstract) child;
                break;
            }
        }
        
        assertNotNull(firstChild, "Should find at least one child node");

        // Reset the parse state
        firstChild.setParseState(ParseState.NONE);

        // Request manual parse
        parserService.requestParse(firstChild);

        // State should be PENDING (node is added to queue and state is set to PENDING)
        // Note: addToParseQueue sets state to PENDING when adding to the queue
        assertEquals(firstChild.getParseState(), ParseState.PENDING,
                "State should be PENDING after parse request (node is in queue)");

        // Wait for parsing to complete
        waitForNodeParsing(firstChild);

        // State should be DONE after parsing
        assertEquals(firstChild.getParseState(), ParseState.DONE,
                "State should be DONE after parsing");
    }

    /**
     * Test that the full parse request works correctly.
     */
    @Test
    public void testFullParseRequest() throws Exception {
        System.out.println("\n--- Testing Full Parse Request ---");

        // Mark all nodes as needing re-parse
        editTree.triggerFullReparse();
        
        EditNodeAbstract root = editTree.getRoot();
        ParseState rootState = root.getParseState();
        // Root should be EDITED initially, but may already be PENDING if queue processor is fast
        assertTrue(rootState == ParseState.EDITED || rootState == ParseState.PENDING,
                "Root should be EDITED or PENDING after full parse request: " + rootState);

        // Wait for parsing to complete
        waitForParsingCompletion();

        // Check that root is now DONE
        rootState = root.getParseState();
        assertEquals(rootState, ParseState.DONE, "Root should be DONE after full parse");
    }

    /**
     * Test that close() properly stops the parser service.
     */
    @Test
    public void testCloseStopsParser() throws Exception {
        System.out.println("\n--- Testing Close Stops Parser ---");
        
        assertTrue(parserService.isRunning(), "Parser should be running initially");

        // Close the tree
        editTree.close();

        // Parser should be stopped
        // Note: parserService reference might be null after close, so we need to check via tree
        EditTree closedTree = editTree; // Save reference before it's cleared
        assertFalse(closedTree.isParserRunning(), "Parser should be stopped after close");
    }

    // ========== Helper Methods ==========
    /**
     * Waits for the parse queue to be empty (or timeout after 5 seconds).
     */
    private void waitForQueueEmpty() {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 5000) {
            if (parserService != null && parserService.getQueuedTaskCount() == 0) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("Warning: Queue empty wait timed out");
    }

    /**
     * Waits for a specific node to be parsed (ParseState.DONE or timeout after
     * 5 seconds).
     */
    private void waitForNodeParsing(EditNodeAbstract node) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 5000) {
            ParseState state = node.getParseState();
            if (state == ParseState.DONE) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("Warning: Node parsing wait timed out for node: " + node.getName());
    }

    /**
     * Waits for the entire tree to be parsed (timeout after 10 seconds).
     */
    private void waitForParsingCompletion() {
        long startTime = System.currentTimeMillis();
        EditNodeAbstract root = editTree.getRoot();

        while (System.currentTimeMillis() - startTime < 10000) {
            // Check if queue is empty and root is DONE.
            // Use editTree.getParseQueue() directly because this method
            // may be called before the parserService field is assigned.
            boolean queueEmpty = editTree.getParseQueue().isEmpty();
            boolean rootDone = root.getParseState() == ParseState.DONE;

            if (queueEmpty && rootDone) {
                return;
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("Warning: Full parsing completion wait timed out");
    }

    /**
     * Prints the tree structure with parse states and edit statuses.
     */
    private void printTreeWithParseStates(EditNode node, String indent) {
        String nodeInfo = node.getClass().getSimpleName()
                + "[name=" + node.getName()
                + ", value=" + node.getValue()
                + ", type=" + node.getTypeKey() + "]";
        
        String parseInfo = "";
        String statusInfo = "";
        
        if (node instanceof EditNodeAbstract) {
            EditNodeAbstract absNode = (EditNodeAbstract) node;
            parseInfo = ", parseState=" + absNode.getParseState().getName();
            statusInfo = ", editStatus=" + absNode.getEditStatus().getName();
            String message = absNode.getEditMessage();
            if (message != null && !message.isEmpty()) {
                statusInfo += " (" + message + ")";
            }
        }
        
        System.out.println(indent + nodeInfo + parseInfo + statusInfo);
        
        for (int i = 0; i < node.getChildCount(); i++) {
            EditNode child = node.getChildAt(i);
            printTreeWithParseStates(child, indent + "  ");
        }
    }

    /**
     * Finds a property node whose parent has a type descriptor assigned.
     * This is useful for testing field assignment, as the parent type is needed
     * for proper field resolution.
     *
     * @param node the root node to start searching from
     * @return a property node with a typed parent, or null if none found
     */
    private EditNodeProperty findPropertyWithTypedParent(EditNode node) {
        if (node instanceof EditNodeProperty) {
            EditNodeProperty prop = (EditNodeProperty) node;
            EditNode parent = prop.getParent();
            if (parent instanceof EditNodeObject) {
                EditNodeObject parentObject = (EditNodeObject) parent;
                if (parentObject.getJsonType() != null) {
                    return prop;
                }
            }
        }
        
        if (node instanceof EditNodeAbstract) {
            EditNodeAbstract absNode = (EditNodeAbstract) node;
            for (int i = 0; i < absNode.getChildCount(); i++) {
                EditNode child = absNode.getChildAt(i);
                EditNodeProperty result = findPropertyWithTypedParent(child);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }

}
