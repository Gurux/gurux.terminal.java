package gurux.terminal.java;

import gurux.io.BaudRate;
import gurux.io.Parity;
import gurux.io.StopBits;
import gurux.terminal.GXTerminal;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for terminal media.
 */
public class GXTerminalTest extends TestCase {
    /**
     * Create the test case.
     *
     * @param testName
     *            Name of the test case.
     */
    public GXTerminalTest(final String testName) {
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
    public final void testNativeLibrary() {
        GXTerminal.getPortNames();
    }

    /**
     * Settings test.
     */
    public final void testSettings() {
        String nl = System.getProperty("line.separator");
        try (GXTerminal serial = new GXTerminal("COM1", BaudRate.BAUD_RATE_300,
                7, Parity.EVEN, StopBits.ONE)) {
            serial.setPhoneNumber("+358 3 265 1244");
            String expected = "<Number>+358 3 265 1244</Number>" + nl
                    + "<Port>COM1</Port>" + nl + "<BaudRate>300</BaudRate>" + nl
                    + "<Parity>2</Parity>" + nl + "<DataBits>7</DataBits>" + nl;
            String actual = serial.getSettings();
            assertEquals(expected, actual);
            try (GXTerminal serial1 = new GXTerminal()) {
                serial1.setSettings(actual);
            }
        }
    }
}
