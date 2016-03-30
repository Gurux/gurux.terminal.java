//
// --------------------------------------------------------------------------
//  Gurux Ltd
// 
//
//
// Filename:        $HeadURL$
//
// Version:         $Revision$,
//                  $Date$
//                  $Author$
//
// Copyright (c) Gurux Ltd
//
//---------------------------------------------------------------------------
//
//  DESCRIPTION
//
// This file is a part of Gurux Device Framework.
//
// Gurux Device Framework is Open Source software; you can redistribute it
// and/or modify it under the terms of the GNU General Public License 
// as published by the Free Software Foundation; version 2 of the License.
// Gurux Device Framework is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of 
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
// See the GNU General Public License for more details.
//
// More information of Gurux products: http://www.gurux.org
//
// This code is licensed under the GNU General Public License v2. 
// Full text may be retrieved at http://www.gnu.org/licenses/gpl-2.0.txt
//---------------------------------------------------------------------------

package gurux.terminal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import gurux.common.GXCommon;
import gurux.common.GXSync;
import gurux.common.GXSynchronousMediaBase;
import gurux.common.IGXMedia;
import gurux.common.IGXMediaListener;
import gurux.common.MediaStateEventArgs;
import gurux.common.PropertyChangedEventArgs;
import gurux.common.ReceiveEventArgs;
import gurux.common.ReceiveParameters;
import gurux.common.TraceEventArgs;
import gurux.common.enums.MediaState;
import gurux.common.enums.TraceLevel;
import gurux.common.enums.TraceTypes;
import gurux.io.BaudRate;
import gurux.io.Handshake;
import gurux.io.NativeCode;
import gurux.io.Parity;
import gurux.io.StopBits;
import gurux.terminal.enums.AvailableMediaSettings;

/**
 * The GXTerminal component determines methods that make the communication
 * possible using terminal (modem) connection.
 */
public class GXTerminal implements IGXMedia, AutoCloseable {
    /**
     * Minimum reply size in bytes.
     */
    static final int MIN_REPLY_SIZE = 5;

    /**
     * How long is waited before AT command is sent after connection is made.
     */
    static final int INITIALIZE_SLEEP = 100;

    /**
     * Initialized connection wait time.
     */
    static final int INITIALIZE_CONNECTION_WAIT_TIME = 30000;

    /**
     * Initialized command wait time.
     */
    static final int INITIALIZE_COMMAND_WAIT_TIME = 3000;

    /**
     * Read buffer size.
     */
    static final int DEFUALT_READ_BUFFER_SIZE = 256;
    /**
     * Default baud rate.
     */
    static final int DEFAULT_BAUD_RATE = 9600;
    /**
     * Amount of default data bits.
     */
    static final int DEFAULT_DATA_BITS = 8;
    /**
     * Used PIN code.
     */
    private String pin;
    /**
     * Used baud rate.
     */
    private int baudRate = DEFAULT_BAUD_RATE;
    /**
     * Amount of data bits.
     */
    private int dataBits = DEFAULT_DATA_BITS;
    /**
     * Used stop bits.
     */
    private StopBits stopBits = StopBits.ONE;
    /**
     * Used parity.
     */
    private Parity parity = Parity.NONE;

    /**
     * Enumeration of progress.
     *
     */
    enum Progress {
        /**
         * No progress.
         */
        NONE,

        /**
         * Connecting to the terminal.
         */
        CONNECTING,

        /**
         * Connected to the terminal.
         */
        CONNECTED
    }

    /**
     * How long connection can take.
     */
    private int connectionWaitTime = INITIALIZE_CONNECTION_WAIT_TIME;

    /**
     * How long command reply can take.
     */
    private int commadWaitTime = INITIALIZE_COMMAND_WAIT_TIME;

    /**
     * Progress status.
     */
    private Progress progress;
    /**
     * Is in server mode.
     */
    private boolean server;
    /**
     * Initialize commands for the modem..
     */
    private String[] initializeCommands;
    /**
     * Phone number to call.
     */
    private String phoneNumber;

    /**
     * Serial port closing handle.
     */
    private long closing = 0;
    /**
     * Write timeout.
     */
    private int writeTimeout;
    /**
     * Read timeout.
     */
    private int readTimeout;
    /**
     * In modem initialized.
     */
    private static boolean initialized;
    /**
     * Read buffer size.
     */
    private int readBufferSize;
    /**
     * Receiver thread.
     */
    private GXReceiveThread receiver;
    /**
     * Serial port handle.
     */
    private int hWnd;
    /**
     * Serial port name.
     */
    private String portName;
    /**
     * Synchronous class.
     */
    private GXSynchronousMediaBase syncBase;
    /**
     * Bytes send.
     */
    private long bytesSend = 0;
    /**
     * Synchronous counter.
     */
    private int synchronous = 0;
    /**
     * Trace level.
     */
    private TraceLevel trace = TraceLevel.OFF;
    /**
     * End of packet.
     */
    private Object eop;
    /**
     * Configurable settings.
     */
    private int configurableSettings;
    /**
     * Media listeners.
     */
    private List<IGXMediaListener> mediaListeners =
            new ArrayList<IGXMediaListener>();

    /**
     * Constructor.
     */
    public GXTerminal() {
        phoneNumber = "";
        initialize();
        readBufferSize = DEFUALT_READ_BUFFER_SIZE;
        syncBase = new GXSynchronousMediaBase(readBufferSize);
        setConfigurableSettings(AvailableMediaSettings.ALL.getValue());
    }

    /**
     * Returns synchronous class used to communicate synchronously.
     * 
     * @return Synchronous class.
     */
    final GXSynchronousMediaBase getSyncBase() {
        return syncBase;
    }

    /**
     * Get handle for closing.
     * 
     * @return Handle for closing.
     */
    final long getClosing() {
        return closing;
    }

    /**
     * Set handle for closing.
     * 
     * @param value
     *            Handle for closing.
     */
    final void setClosing(final long value) {
        closing = value;
    }

    /**
     * Is Windows operating system.
     * 
     * @param os
     *            Operating system name.
     * @return True if Windows.
     */
    static boolean isWindows(final String os) {
        return (os.indexOf("win") >= 0);
    }

    /**
     * Is Mac operating system.
     * 
     * @param os
     *            Operating system name.
     * @return True if Mac.
     */
    static boolean isMac(final String os) {
        return (os.indexOf("mac") >= 0);
    }

    /**
     * Is Unix operating system.
     * 
     * @param os
     *            Operating system name.
     * @return True if Unix.
     */
    static boolean isUnix(final String os) {
        return (os.indexOf("nix") >= 0 || os.indexOf("nux") >= 0
                || os.indexOf("aix") >= 0);
    }

    /**
     * Is Solaris operating system.
     * 
     * @param os
     *            Operating system name.
     * @return True if Solaris.
     */
    static boolean isSolaris(final String os) {
        return (os.indexOf("sunos") >= 0);
    }

    /**
     * Initialize Gurux serial port library.
     */
    static void initialize() {
        if (!initialized) {
            String path;
            String os = System.getProperty("os.name").toLowerCase();
            boolean is32Bit =
                    System.getProperty("sun.arch.data.model").equals("32");
            if (isWindows(os)) {
                if (is32Bit) {
                    path = "win32";
                } else {
                    path = "win64";
                }
            } else if (isUnix(os)) {
                if (is32Bit) {
                    path = "linux86";
                } else {
                    path = "linux64";
                }
            } else {
                throw new RuntimeException("Invald operating system. " + os);
            }
            File file;
            try {
                file = File.createTempFile("gurux.serial.java", ".dll");
            } catch (IOException e1) {
                throw new RuntimeException(
                        "Failed to load file. " + path + "/gurux.serial.java");
            }
            try (InputStream in =
                    GXTerminal.class.getResourceAsStream("/" + path + "/"
                            + System.mapLibraryName("gurux.serial.java"))) {
                Files.copy(in, file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                System.load(file.getAbsolutePath());
            } catch (Exception e) {
                throw new RuntimeException("Failed to load file. " + path
                        + "/gurux.serial.java" + e.toString());
            }
        }
    }

    /**
     * Gets an array of serial port names for the current computer.
     * 
     * @return Collection of available serial ports.
     */
    public static String[] getPortNames() {
        initialize();
        return NativeCode.getPortNames();
    }

    /**
     * 
     * Get baud rates supported by given serial port.
     * 
     * @param portName
     *            Name of serial port.
     * @return Collection of available baud rates.
     */
    public static int[] getAvailableBaudRates(final String portName) {
        return new int[] { BaudRate.BAUD_RATE_300.getValue(),
                BaudRate.BAUD_RATE_600.getValue(),
                BaudRate.BAUD_RATE_1800.getValue(),
                BaudRate.BAUD_RATE_2400.getValue(),
                BaudRate.BAUD_RATE_4800.getValue(),
                BaudRate.BAUD_RATE_9600.getValue(),
                BaudRate.BAUD_RATE_19200.getValue(),
                BaudRate.BAUD_RATE_38400.getValue() };
    }

    @Override
    protected final void finalize() throws Throwable {
        super.finalize();
        if (isOpen()) {
            close();
        }
    }

    @Override
    public final TraceLevel getTrace() {
        return trace;
    }

    @Override
    public final void setTrace(final TraceLevel value) {
        trace = value;
        syncBase.setTrace(value);
    }

    /**
     * Notify that property has changed.
     * 
     * @param info
     *            Name of changed property.
     */
    private void notifyPropertyChanged(final String info) {
        for (IGXMediaListener listener : mediaListeners) {
            listener.onPropertyChanged(this,
                    new PropertyChangedEventArgs(info));
        }
    }

    /**
     * Notify clients from error occurred.
     * 
     * @param ex
     *            Occurred error.
     */
    final void notifyError(final RuntimeException ex) {
        for (IGXMediaListener listener : mediaListeners) {
            listener.onError(this, ex);
            if (trace.ordinal() >= TraceLevel.ERROR.ordinal()) {
                listener.onTrace(this,
                        new TraceEventArgs(TraceTypes.ERROR, ex));
            }
        }
    }

    /**
     * Notify clients from new data received.
     * 
     * @param e
     *            Received event argument.
     */
    final void notifyReceived(final ReceiveEventArgs e) {
        for (IGXMediaListener listener : mediaListeners) {
            listener.onReceived(this, e);
        }
    }

    /**
     * Notify clients from trace events.
     * 
     * @param e
     *            Trace event argument.
     */
    final void notifyTrace(final TraceEventArgs e) {
        for (IGXMediaListener listener : mediaListeners) {
            listener.onTrace(this, e);
        }
    }

    @Override
    public final int getConfigurableSettings() {
        return configurableSettings;
    }

    @Override
    public final void setConfigurableSettings(final int value) {
        this.configurableSettings = value;
    }

    @Override
    public final boolean properties(final javax.swing.JFrame parent) {
        GXSettings dlg = new GXSettings(parent, true, this);
        dlg.pack();
        dlg.setVisible(true);
        return dlg.isAccepted();
    }

    /**
     * Displays the copyright of the control, user license, and version
     * information, in a dialog box.
     */
    public final void aboutBox() {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void send(final Object data, final String target) {
        if (hWnd == 0) {
            throw new RuntimeException("Serial port is not open.");
        }
        if (trace == TraceLevel.VERBOSE) {
            notifyTrace(new TraceEventArgs(TraceTypes.SENT, data));
        }
        // Reset last position if end of packets is used.
        synchronized (syncBase.getSync()) {
            syncBase.resetLastPosition();
        }
        byte[] buff = GXSynchronousMediaBase.getAsByteArray(data);
        if (buff == null) {
            throw new IllegalArgumentException(
                    "Data send failed. Invalid data.");
        }
        NativeCode.write(hWnd, buff, writeTimeout);
        this.bytesSend += buff.length;
    }

    /**
     * Notify client from media state change.
     * 
     * @param state
     *            New media state.
     */
    private void notifyMediaStateChange(final MediaState state) {
        for (IGXMediaListener listener : mediaListeners) {
            if (trace.ordinal() >= TraceLevel.ERROR.ordinal()) {
                listener.onTrace(this,
                        new TraceEventArgs(TraceTypes.INFO, state));
            }
            listener.onMediaStateChange(this, new MediaStateEventArgs(state));
        }
    }

    /**
     * Open serial port and calls to phone number.
     */
    @Override
    public final void open() throws Exception {
        close();
        try {
            if (portName == null || portName == "") {
                throw new IllegalArgumentException(
                        "Serial port is not selected.");
            }
            synchronized (syncBase.getSync()) {
                syncBase.resetLastPosition();
            }
            notifyMediaStateChange(MediaState.OPENING);
            if (trace.ordinal() >= TraceLevel.INFO.ordinal()) {
                String eopStr = "None";
                if (getEop() instanceof byte[]) {
                    eopStr = GXCommon.bytesToHex((byte[]) getEop());
                } else if (getEop() != null) {
                    eopStr = getEop().toString();
                }
                notifyTrace(new TraceEventArgs(TraceTypes.INFO,
                        "Settings: Port: " + this.getPortName() + " Baud Rate: "
                                + getBaudRate() + " Data Bits: "
                                + (new Integer(getDataBits())).toString()
                                + " Parity: " + getParity().toString()
                                + " Stop Bits: " + getStopBits().toString()
                                + " Eop:" + eopStr));
            }
            long[] tmp = new long[1];
            hWnd = NativeCode.openSerialPort(portName, tmp);
            // If user has change values before open.
            if (baudRate != DEFAULT_BAUD_RATE) {
                setBaudRate(baudRate);
            }
            if (dataBits != DEFAULT_DATA_BITS) {
                setDataBits(dataBits);
            }
            if (parity != Parity.NONE) {
                setParity(parity);
            }
            if (stopBits != StopBits.ONE) {
                setStopBits(stopBits);
            }
            closing = tmp[0];
            receiver = new GXReceiveThread(this, hWnd);
            setRtsEnable(true);
            setDtrEnable(true);
            receiver.start();
            Thread.sleep(INITIALIZE_SLEEP);
            try {
                // Send AT
                synchronized (getSynchronous()) {
                    if (getInitializeCommands() != null) {
                        for (String it : getInitializeCommands()) {
                            sendCommand(it + "\r\n", commadWaitTime, null,
                                    true);
                        }
                    }
                    // Send AT few times. This helps for several modems.
                    String reply;
                    sendCommand("AT\r", commadWaitTime, null, false);
                    sendCommand("AT\r", commadWaitTime, null, false);
                    if (server) {
                        if (!"OK".equalsIgnoreCase(sendCommand("AT\r",
                                commadWaitTime, null, false))) {
                            reply = sendCommand("AT\r", commadWaitTime, null,
                                    true);
                            if (!"OK".equalsIgnoreCase(reply)) {
                                throw new Exception("Invalid reply.");
                            }
                        }
                        reply = sendCommand("ATA\r", commadWaitTime, null,
                                true);
                        if (!"ATA".equalsIgnoreCase(reply)) {
                            throw new Exception("Invalid reply.");
                        }
                        progress = Progress.CONNECTING;
                    } else {
                        // Send AT
                        if ("OK".compareToIgnoreCase(sendCommand("AT\r",
                                commadWaitTime, null, false)) != 0) {
                            reply = sendCommand("AT\r", commadWaitTime, null,
                                    true);
                            if ("OK".compareToIgnoreCase(reply) != 0) {
                                throw new Exception("Invalid reply.");
                            }
                        }
                        progress = Progress.CONNECTING;
                        if (phoneNumber == null || phoneNumber.length() == 0) {
                            sendCommand("ATD\r\n", connectionWaitTime, null,
                                    true);
                        } else {
                            sendCommand("ATD" + phoneNumber + "\r\n",
                                    connectionWaitTime, null, true);
                        }
                        progress = Progress.CONNECTED;
                    }
                }
            } catch (Exception ex) {
                close();
                throw ex;
            }
            notifyMediaStateChange(MediaState.OPEN);
        } catch (Exception ex) {
            close();
            throw ex;
        }
    }

    /**
     * Send bytes to the serial port.
     * 
     * @param value
     *            Bytes to send.
     */
    private void sendBytes(final byte[] value) {
        if (trace == TraceLevel.VERBOSE) {
            notifyTrace(new TraceEventArgs(TraceTypes.SENT, value));
        }
        bytesSend += value.length;
        // Reset last position if end of packet is used.
        synchronized (syncBase.getSync()) {
            syncBase.resetLastPosition();
        }
        NativeCode.write(hWnd, value, writeTimeout);
    }

    /**
     * Send command to the serial port.
     * 
     * @param cmd
     *            Command string to send.
     * @param wt
     *            Wait time.
     * @param commandEop
     *            End of command.
     * @param throwError
     *            Is error thrown is reply message is not received.
     * @return Received reply.
     */
    private String sendCommand(final String cmd, final int wt,
            final String commandEop, final boolean throwError) {
        ReceiveParameters<String> p =
                new ReceiveParameters<String>(String.class);
        p.setWaitTime(wt);
        if (commandEop != null) {
            p.setEop(commandEop);

        } else {
            p.setEop("\r\n");
        }
        if (p.getEop().equals("")) {
            p.setEop(null);
            p.setCount(cmd.length());
        }
        try {
            sendBytes(cmd.getBytes("ASCII"));
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex.getMessage());
        }
        StringBuilder sb = new StringBuilder();
        int index = -1;
        String reply = "";
        while (index == -1) {
            if (!receive(p)) {
                if (throwError) {
                    throw new RuntimeException(
                            "Failed to receive answer from the modem. "
                                    + "Check serial port.");
                }
                return "";
            }
            sb.append(p.getReply());
            reply = sb.toString();
            // Remove echo.
            if (sb.length() >= cmd.length() && reply.startsWith(cmd)) {
                sb.delete(0, cmd.length());
                reply = sb.toString();
                // Remove echo and return if we are not expecting reply.
                if (commandEop != null && commandEop.equals("")) {
                    return "";
                }
            }
            if (commandEop != null) {
                index = reply.lastIndexOf(commandEop);
            } else if (reply.length() > MIN_REPLY_SIZE) {
                index = reply.lastIndexOf("\r\nOK\r\n");
                if (index == -1) {
                    index = reply.lastIndexOf("ERROR:");
                    if (index == -1) {
                        index = reply.lastIndexOf("CONNECT");
                        if (index == -1) {
                            index = reply.lastIndexOf("NO CARRIER");
                            if (index != -1) {
                                String str = "Connection failed: no carrier "
                                        + "(when telephone call was "
                                        + "being established). ";
                                int start = reply.indexOf("CAUSE:");
                                if (start != -1) {
                                    if (start < index) {
                                        str += reply.substring(start, index)
                                                .trim();
                                    } else {
                                        str += reply.substring(start).trim();
                                    }
                                }
                                str += "\r\n" + sendCommand("AT+CEER\r", wt,
                                        null, false);
                                throw new RuntimeException(str);
                            }
                            if (reply.lastIndexOf("ERROR") != -1) {
                                throw new RuntimeException(
                                        "Connection failed: error "
                                                + "(when telephone call "
                                                + "was being established).");
                            }
                            if (reply.lastIndexOf("BUSY") != -1) {
                                throw new RuntimeException(
                                        "Connection failed: busy "
                                                + "(when telephone call "
                                                + "was being established).");
                            }
                        }
                    }
                } else if (index != 0) {
                    // If there is a message before OK show it.
                    reply = reply.substring(0, index);
                    index = 0;
                }
            }
            p.setReply(null);
        }
        if (index != 0 & commandEop == null) {
            reply = reply.substring(0, 0) + reply.substring(0 + index);
        }
        reply = reply.trim();
        return reply;
    }

    @Override
    public final void close() {
        if (hWnd != 0) {
            try {
                notifyMediaStateChange(MediaState.CLOSING);
            } catch (RuntimeException ex) {
                notifyError(ex);
                throw ex;
            } finally {
                if (progress != Progress.NONE) {
                    try {
                        // Send AT
                        synchronized (getSynchronous()) {
                            if (progress == Progress.CONNECTED) {
                                try {
                                    Thread.sleep(INITIALIZE_SLEEP);
                                } catch (InterruptedException ex) {
                                    throw new RuntimeException(ex.getMessage());
                                }
                                ReceiveParameters<String> p =
                                        new ReceiveParameters<String>(
                                                String.class);
                                p.setWaitTime(commadWaitTime);
                                p.setCount("+++".length());
                                try {
                                    sendBytes("+++".getBytes("ASCII"));
                                } catch (UnsupportedEncodingException ex) {
                                    throw new RuntimeException(ex.getMessage());
                                }
                                // It's OK if this fails.
                                receive(p);
                                sendCommand("ATH0\r", connectionWaitTime, null,
                                        false);
                            }
                        }
                    } finally {
                        progress = Progress.NONE;
                        if (receiver != null) {
                            receiver.interrupt();
                            receiver = null;
                        }
                    }
                }
                try {
                    NativeCode.closeSerialPort(hWnd, closing);
                } catch (java.lang.Exception e) {
                    // Ignore all errors on close.
                }
                hWnd = 0;
                notifyMediaStateChange(MediaState.CLOSED);
                bytesSend = 0;
                receiver.resetBytesReceived();
                syncBase.resetReceivedSize();
            }
        }
    }

    /**
     * Used baud rate for communication. Can be changed without disconnecting.
     * 
     * @return Used baud rate.
     */
    public final int getBaudRate() {
        if (hWnd == 0) {
            return baudRate;
        }
        return NativeCode.getBaudRate(hWnd);
    }

    /**
     * Set new baud rate.
     * 
     * @param value
     *            New baud rate.
     */
    public final void setBaudRate(final int value) {
        boolean change = getBaudRate() != value;
        if (change) {
            if (hWnd == 0) {
                baudRate = value;
            } else {
                NativeCode.setBaudRate(hWnd, value);
            }
            notifyPropertyChanged("BaudRate");
        }
    }

    /**
     * Gets the phone number.
     * 
     * @return Phone number.
     */
    public final String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Sets the phone number.
     * 
     * @param value
     *            Phone number.
     */
    public final void setPhoneNumber(final String value) {
        boolean change =
                phoneNumber == null || phoneNumber.equalsIgnoreCase(value);
        phoneNumber = value;
        if (change) {
            notifyPropertyChanged("BaudRate");
        }
    }

    /**
     * Get PIN Code.
     * 
     * @return PIN Code.
     */
    public final String getPINCode() {
        return pin;
    }

    /**
     * Set PIN Code.
     * 
     * @param value
     *            PIN Code.
     */
    public final void setPINCode(final String value) {
        pin = value;
    }

    /**
     * Gets how long (milliseconds) modem answer is waited when connection is
     * made.
     * 
     * @return Wait time in milliseconds.
     */
    public final int getConnectionWaitTime() {
        return connectionWaitTime;
    }

    /**
     * Sets how long (milliseconds) modem answer is waited when connection is
     * made.
     * 
     * @param value
     *            Wait time in milliseconds.
     */
    public final void setConnectionWaitTime(final int value) {
        boolean change = connectionWaitTime != value;
        connectionWaitTime = value;
        if (change) {
            notifyPropertyChanged("ConnectionWaitTime");
        }
    }

    /**
     * Gets how long (milliseconds) modem answer is waited when command is send
     * for the modem.
     * 
     * @return Wait time in milliseconds.
     */
    public final int getCommandWaitTime() {
        return commadWaitTime;
    }

    /**
     * Sets how long (milliseconds) modem answer is waited when command is send
     * for the modem.
     * 
     * @param value
     *            Wait time in milliseconds.
     */
    public final void setCommandWaitTime(final int value) {
        boolean change = commadWaitTime != value;
        commadWaitTime = value;
        if (change) {
            notifyPropertyChanged("CommadWaitTime");
        }
    }

    /**
     * Get break state.
     * 
     * @return True if the port is in a break state; otherwise, false.
     */
    public final boolean getBreakState() {
        return NativeCode.getBreakState(hWnd);
    }

    /**
     * Set break state.
     * 
     * @param value
     *            True if the port is in a break state; otherwise, false.
     */
    public final void setBreakState(final boolean value) {
        boolean change;
        change = getBreakState() != value;
        if (change) {
            NativeCode.setBreakState(hWnd, value);
            notifyPropertyChanged("BreakState");
        }
    }

    /**
     * Gets the number of bytes in the receive buffer.
     * 
     * @return Amount of read bytes.
     */
    public final int getBytesToRead() {
        return NativeCode.getBytesToRead(hWnd);
    }

    /**
     * Gets the number of bytes in the send buffer.
     * 
     * @return Amount of bytes to write in the send buffer.
     */
    public final int getBytesToWrite() {
        return NativeCode.getBytesToWrite(hWnd);
    }

    /**
     * Gets the state of the Carrier Detect line for the port.
     * 
     * @return Is Carrier Detect in holding state.
     */
    public final boolean getCDHolding() {
        return NativeCode.getCDHolding(hWnd);
    }

    /**
     * Gets the state of the Clear-to-Send line.
     * 
     * @return Clear-to-Send state.
     */
    public final boolean getCtsHolding() {
        return NativeCode.getCtsHolding(hWnd);
    }

    /**
     * Gets the standard length of data bits per byte.
     * 
     * @return Amount of data bits.
     */
    public final int getDataBits() {
        if (hWnd == 0) {
            return dataBits;
        }
        return NativeCode.getDataBits(hWnd);
    }

    /**
     * Sets the standard length of data bits per byte.
     * 
     * @param value
     *            Amount of data bits.
     */
    public final void setDataBits(final int value) {
        boolean change;
        change = getDataBits() != value;
        if (change) {
            if (hWnd == 0) {
                dataBits = value;
            } else {
                NativeCode.setDataBits(hWnd, value);
            }
            notifyPropertyChanged("DataBits");
        }
    }

    /**
     * Gets the state of the Data Set Ready (DSR) signal.
     * 
     * @return Is Data Set Ready set.
     */
    public final boolean getDsrHolding() {
        return NativeCode.getDsrHolding(hWnd);
    }

    /**
     * Get is Data Terminal Ready (DTR) signal enabled.
     * 
     * @return Is DTR enabled.
     */
    public final boolean getDtrEnable() {
        return NativeCode.getDtrEnable(hWnd);
    }

    /**
     * Set is Data Terminal Ready (DTR) signal enabled.
     * 
     * @param value
     *            Is DTR enabled.
     */
    public final void setDtrEnable(final boolean value) {
        boolean change;
        change = getDtrEnable() != value;
        NativeCode.setDtrEnable(hWnd, value);
        if (change) {
            notifyPropertyChanged("DtrEnable");
        }
    }

    /**
     * Gets the handshaking protocol for serial port transmission of data.
     * 
     * @return Used handshake protocol.
     */
    public final Handshake getHandshake() {
        return Handshake.values()[NativeCode.getHandshake(hWnd)];
    }

    /**
     * Sets the handshaking protocol for serial port transmission of data.
     * 
     * @param value
     *            Handshake protocol.
     */
    public final void setHandshake(final Handshake value) {
        boolean change;
        change = getHandshake() != value;
        if (change) {
            NativeCode.setHandshake(hWnd, value.ordinal());
            notifyPropertyChanged("Handshake");
        }
    }

    @Override
    public final boolean isOpen() {
        return hWnd != 0;
    }

    /**
     * Gets the parity-checking protocol.
     * 
     * @return Used parity.
     */
    public final Parity getParity() {
        if (hWnd == 0) {
            return parity;
        }
        return Parity.values()[NativeCode.getParity(hWnd)];
    }

    /**
     * Sets the parity-checking protocol.
     * 
     * @param value
     *            Used parity.
     */
    public final void setParity(final Parity value) {
        boolean change;
        change = getParity() != value;
        if (change) {
            if (hWnd == 0) {
                parity = value;
            } else {
                NativeCode.setParity(hWnd, value.ordinal());
            }
            notifyPropertyChanged("Parity");
        }
    }

    /**
     * Gets the port for communications, including but not limited to all
     * available COM ports.
     * 
     * @return Used serial port
     */
    public final String getPortName() {
        return portName;
    }

    /**
     * Sets the port for communications, including but not limited to all
     * available COM ports.
     * 
     * @param value
     *            Used serial port.
     */
    public final void setPortName(final String value) {
        boolean change;
        change = !value.equals(portName);
        portName = value;
        if (change) {
            notifyPropertyChanged("PortName");
        }
    }

    /**
     * Gets the size of the serial port input buffer.
     * 
     * @return Size of input buffer.
     */
    public final int getReadBufferSize() {
        return readBufferSize;
    }

    /**
     * Sets the size of the serial port input buffer.
     * 
     * @param value
     *            Size of input buffer.
     */
    public final void setReadBufferSize(final int value) {
        boolean change;
        change = getReadBufferSize() != value;
        if (change) {
            readBufferSize = value;
            notifyPropertyChanged("ReadBufferSize");
        }
    }

    /**
     * Gets the number of milliseconds before a time-out occurs when a read
     * operation does not finish.
     * 
     * @return Read timeout.
     */
    public final int getReadTimeout() {
        return readTimeout;
    }

    /**
     * Sets the number of milliseconds before a time-out occurs when a read
     * operation does not finish.
     * 
     * @param value
     *            Read timeout.
     */
    public final void setReadTimeout(final int value) {
        boolean change = readTimeout != value;
        readTimeout = value;
        if (change) {
            notifyPropertyChanged("ReadTimeout");
        }
    }

    /**
     * Gets a value indicating whether the Request to Send (RTS) signal is
     * enabled during serial communication.
     * 
     * @return Is RTS enabled.
     */
    public final boolean getRtsEnable() {
        return NativeCode.getRtsEnable(hWnd);
    }

    /**
     * Sets a value indicating whether the Request to Send (RTS) signal is
     * enabled during serial communication.
     * 
     * @param value
     *            Is RTS enabled.
     */
    public final void setRtsEnable(final boolean value) {
        boolean change;
        change = getRtsEnable() != value;
        NativeCode.setRtsEnable(hWnd, value);
        if (change) {
            notifyPropertyChanged("RtsEnable");
        }
    }

    /**
     * Gets the standard number of stop bits per byte.
     * 
     * @return Used stop bits.
     */
    public final StopBits getStopBits() {
        if (hWnd == 0) {
            return stopBits;
        }
        return StopBits.values()[NativeCode.getStopBits(hWnd)];
    }

    /**
     * Sets the standard number of stop bits per byte.
     * 
     * @param value
     *            Used stop bits.
     */
    public final void setStopBits(final StopBits value) {
        boolean change;
        change = getStopBits() != value;
        if (change) {
            if (hWnd == 0) {
                stopBits = value;
            } else {
                NativeCode.setStopBits(hWnd, value.ordinal());
            }
            notifyPropertyChanged("StopBits");
        }
    }

    /**
     * Gets the number of milliseconds before a time-out occurs when a write
     * operation does not finish.
     * 
     * @return Used time out.
     */
    public final int getWriteTimeout() {
        return writeTimeout;
    }

    /**
     * Sets the number of milliseconds before a time-out occurs when a write
     * operation does not finish.
     * 
     * @param value
     *            Used time out.
     */
    public final void setWriteTimeout(final int value) {
        boolean change = writeTimeout != value;
        if (change) {
            writeTimeout = value;
            notifyPropertyChanged("WriteTimeout");
        }
    }

    @Override
    public final <T> boolean receive(final ReceiveParameters<T> args) {
        return syncBase.receive(args);
    }

    /**
     * Sent byte count.
     * 
     * @see getBytesReceived getBytesReceived
     * @see resetByteCounters resetByteCounters
     */
    @Override
    public final long getBytesSent() {
        return bytesSend;
    }

    /**
     * Received byte count.
     * 
     * @see getBytesSent getBytesSent
     * @see resetByteCounters resetByteCounters
     */
    @Override
    public final long getBytesReceived() {
        return receiver.getBytesReceived();
    }

    /**
     * Resets BytesReceived and BytesSent counters.
     * 
     * @see getBytesSent getBytesSent
     * @see getBytesReceived getBytesReceived
     */
    @Override
    public final void resetByteCounters() {
        bytesSend = 0;
        receiver.resetBytesReceived();
    }

    /**
     * Media settings as a XML string.
     */
    @Override
    public final String getSettings() {
        return null;
        // TODO:
    }

    @Override
    public final void setSettings(final String value) {
        // TODO:
    }

    @Override
    public final void copy(final Object target) {
        GXTerminal tmp = (GXTerminal) target;
        setPortName(tmp.getPortName());
        setBaudRate(tmp.getBaudRate());
        setStopBits(tmp.getStopBits());
        setParity(tmp.getParity());
        setDataBits(tmp.getDataBits());
    }

    @Override
    public final String getName() {
        return getPortName();
    }

    @Override
    public final String getMediaType() {
        return "Terminal";
    }

    @Override
    public final Object getSynchronous() {
        synchronized (this) {
            int[] tmp = new int[] { synchronous };
            GXSync obj = new GXSync(tmp);
            synchronous = tmp[0];
            return obj;
        }
    }

    @Override
    public final boolean getIsSynchronous() {
        synchronized (this) {
            return synchronous != 0;
        }
    }

    @Override
    public final void resetSynchronousBuffer() {
        synchronized (syncBase.getSync()) {
            syncBase.resetReceivedSize();
        }
    }

    @Override
    public final void validate() {
        if (getPortName() == null || getPortName().length() == 0) {
            throw new RuntimeException("Invalid port name.");
        }
    }

    /**
     * Get modem initial settings.
     * 
     * @return Initialize commands.
     */
    public final String[] getInitializeCommands() {
        return initializeCommands;
    }

    /**
     * Set modem initial settings.
     * 
     * @param value
     *            Initialize commands.
     */
    public final void setInitializeCommands(final String[] value) {
        initializeCommands = value;
    }

    @Override
    public final Object getEop() {
        return eop;
    }

    @Override
    public final void setEop(final Object value) {
        eop = value;
    }

    @Override
    public final void addListener(final IGXMediaListener listener) {
        mediaListeners.add(listener);
    }

    @Override
    public final void removeListener(final IGXMediaListener listener) {
        mediaListeners.remove(listener);
    }
}