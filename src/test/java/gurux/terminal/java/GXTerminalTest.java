package gurux.terminal.java;

import gurux.terminal.GXTerminal;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for terminal media.
 */
public class GXTerminalTest extends TestCase {
    /**
     * Create the test case
     *
     * @param testName
     *            name of the test case
     */
    public GXTerminalTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(GXTerminalTest.class);
    }

    /**
     * Test native library load.
     */
    public void testNativeLibrary() {
        GXTerminal.getPortNames();
    }
}
