package de.jare.jsoncasted.editor.core;

import java.lang.reflect.Constructor;
import java.util.Map;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 *
 * @author Jansuch Rentenatus
 */
public class EditNodeAbstractNGTest {

    public EditNodeAbstractNGTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("===============================================");
        System.out.println("## Start EditNodeAbstractNGTest.");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        System.out.println("## End EditNodeAbstractNGTest.");
        System.out.println("===============================================");
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
    }

    @Test
    public void testReflectionConstructorAndTreeOperations_NarrowRange() throws Exception {
        runRangeTest(10L, 19L, "NARROW RANGE TEST (10..19)");
    }

    @Test
    public void testReflectionConstructorAndTreeOperations_WideRange() throws Exception {
        runRangeTest(10L, 40000L, "WIDE RANGE TEST (10..40000)");
    }

    private void runRangeTest(long leftRange, long rightRange, String testName) throws Exception {
        // Create root node using reflection for private constructor
        long editId = IdGenerator.EDIT_ID_GENERATOR.nextId();
        Constructor<EditNodeObject> nodeConstructor = EditNodeObject.class.getDeclaredConstructor(
                long.class, long.class, long.class, long.class, String.class);
        nodeConstructor.setAccessible(true);
        EditNodeObject root = nodeConstructor.newInstance(editId, leftRange, rightRange, Long.MIN_VALUE, "rootObject");

        // Create tree with the root using reflection for package-private constructor
        // EditTree has a package-private constructor: EditTree(EditNodeAbstract root, EditTimes weightMonitor)
        Constructor<EditTree> treeConstructor = EditTree.class.getDeclaredConstructor(
                EditNodeAbstract.class, EditTimes.class);
        treeConstructor.setAccessible(true);
        EditTree tree = treeConstructor.newInstance(root, new EditTimes());

        // Add 6 children to root via tree
        for (int i = 1; i <= 6; i++) {
            tree.addNewChild(root, "child" + i, false);
        }

        // Print tree
        System.out.println("\n--- " + testName + " - Tree after adding 6 children ---");
        printTree(root, "");

        EditNodeAbstract childB = root.getChildAt(1);

        // Make deep copy
        EditNodeAbstract copy = root.deepCopy();

        // Add copy to child3
        tree.addChild(childB, copy);

        // Print everything again
        System.out.println("\n--- " + testName + " - Tree after adding deep copy to child3 ---");
        printTree(root, "");

        tree.rangeRelabeling(copy);

        // Print everything again
        System.out.println("\n--- " + testName + " - Tree after range relabeling ---");
        printTree(root, "");
    }

    /**
     * Test that getAttributes returns JackAttribut map correctly.
     */
    @Test
    public void testGetAttributes() throws Exception {
        long editId = IdGenerator.EDIT_ID_GENERATOR.nextId();
        Constructor<EditNodeObject> nodeConstructor = EditNodeObject.class.getDeclaredConstructor(
                long.class, long.class, long.class, long.class, String.class);
        nodeConstructor.setAccessible(true);
        EditNodeObject root = nodeConstructor.newInstance(editId, 10L, 19L, Long.MIN_VALUE, "rootObject");

        Map<String, JackAttribut> attrs = root.getAttributes();
        assertNotNull(attrs, "getAttributes should return non-null map");

        JackAttribut editIdAttr = attrs.get("|edit id");
        assertNotNull(editIdAttr, "editId attribute should be present");
        assertEquals(editIdAttr.getValue(), editId, "editId value should match");

        System.out.println("getAttributes test passed: " + attrs.size() + " attributes found");
    }

    private void printTree(EditNode node, String indent) {
        System.out.println(indent + node.getClass().getSimpleName()
                + "[editId=" + node.getEditId()
                + ", leftRange=" + node.getLeftRange()
                + ", rightRange=" + node.getRightRange()
                + ", timesRange=" + node.getTimesRange()
                + ", name=" + node.getName()
                + ", value=" + node.getValue()
                + ", type=" + node.getTypeKey() + "]");
        for (int i = 0; i < node.getChildCount(); i++) {
            printTree(node.getChildAt(i), indent + "  ");
        }
    }
}
