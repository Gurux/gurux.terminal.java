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

import gurux.common.*;
import gurux.io.*;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/** 
 The GXTerminal component determines methods that make 
 * the communication possible using serial port connection. 
*/
public class GXTerminal implements IGXMedia
{   
    String m_PIN;
    //Values are saved if port is not open and user try to set them.
    int m_BaudRate = 9600;
    int m_DataBits = 8;    
    StopBits m_StopBits = StopBits.ONE;
    Parity m_Parity = Parity.NONE;
    
    enum Progress
    {
        NONE,
        CONNECTING,
        CONNECTED
    }

    //How long connection can take.
    int m_ConnectionWaitTime = 30000;
    
    //How long command reply can take.
    int m_CommadWaitTime = 3000;
    
    Progress m_Progress;
    boolean m_Server;
    String[] m_InitializeCommands;
    String m_PhoneNumber;
    long m_Closing = 0;
    int m_WriteTimeout;
    int m_ReadTimeout;
    private static boolean Initialized;
    int m_ReadBufferSize;
    GXReceiveThread Receiver;
    int m_hWnd;
    String m_PortName;    
    GXSynchronousMediaBase m_syncBase;
    public long m_BytesReceived = 0;
    private long m_BytesSend = 0;
    private int m_Synchronous = 0;
    private TraceLevel m_Trace = TraceLevel.OFF;
    private Object privateEop;        
    private int ConfigurableSettings;
    private List<IGXMediaListener> MediaListeners = new ArrayList<IGXMediaListener>();
    
    /** 
     Constructor.
    */
    public GXTerminal()
    {   
        m_PhoneNumber = "";
        initialize();
        m_ReadBufferSize = 256;        
        m_syncBase = new GXSynchronousMediaBase(m_ReadBufferSize);
        setConfigurableSettings(AvailableMediaSettings.All.getValue());
    }
    
    static void initialize()
    {
        if (!Initialized)
        {
            try
            {
                System.loadLibrary("gurux.serial.java");
            }
            catch(java.lang.UnsatisfiedLinkError ex)
            {
                throw new RuntimeException("Failed to locate gurux.serial.java.dll");            
            }
            Initialized = true;        
        }
    }
    
    /** 
    Gets an array of serial port names for the current computer.

    @return 
    */
    public static String[] getPortNames()
    {                
        initialize();
        return NativeCode.getPortNames();
    }

    /*
     * Get baud rates supported by given serial port.
     */
    public static int[] getAvailableBaudRates(String portName)
    {                
        return new int[]{300, 600, 1800, 2400, 4800, 9600, 19200, 38400};
    }
    
    /** 
     Destructor.
    */
    @Override
    @SuppressWarnings("FinalizeDeclaration")    
    protected void finalize() throws Throwable
    {
        super.finalize();
        if (isOpen())
        {
            close();
        }
    }    

    /** 
     What level of tracing is used.
    */
    @Override
    public final TraceLevel getTrace()
    {
        return m_Trace;
    }
    @Override
    public final void setTrace(TraceLevel value)
    {
        m_Trace = m_syncBase.Trace = value;
    }
    
    private void NotifyPropertyChanged(String info)
    {
        for (IGXMediaListener listener : MediaListeners) 
        {
            listener.onPropertyChanged(this, new PropertyChangedEventArgs(info));
        }
    }         

    void notifyError(RuntimeException ex)
    {
        for (IGXMediaListener listener : MediaListeners) 
        {
            listener.onError(this, ex);
            if (m_Trace.ordinal() >= TraceLevel.ERROR.ordinal())
            {
                listener.onTrace(this, new TraceEventArgs(TraceTypes.ERROR, ex));
            }
        }
    }
    
    void notifyReceived(ReceiveEventArgs e)
    {
        for (IGXMediaListener listener : MediaListeners) 
        {
            listener.onReceived(this, e);
        }
    }
    
    void notifyTrace(TraceEventArgs e)
    {
        for (IGXMediaListener listener : MediaListeners) 
        {                
            listener.onTrace(this, e);
        }
    }
       
    /** <inheritdoc cref="IGXMedia.ConfigurableSettings"/>
    */
    @Override
    public final int getConfigurableSettings()
    {            
        return ConfigurableSettings;
    }
    @Override
    public final void setConfigurableSettings(int value)
    {
        this.ConfigurableSettings = value;
    }
    
     /*
     * Show media properties.
     */
    @Override 
    public boolean properties(javax.swing.JFrame parent)
    {
        GXSettings dlg = new GXSettings(parent, true, this);
        dlg.pack();
        dlg.setVisible(true);    
        return dlg.Accepted;
    }

    /**    
     Displays the copyright of the control, user license, and version information, in a dialog box. 
    */
    public final void aboutBox()
    {
        throw new UnsupportedOperationException();
    }

    /** 
     Sends data asynchronously. <br/>
     No reply from the receiver, whether or not the operation was successful, is expected.

     @param data Data to send to the device.
     @param receiver Not used.
     Reply data is received through OnReceived event.<br/>     		
     @see OnReceived OnReceived
     @see Open Open
     @see Close Close 
    */
    @Override
    public final void send(Object data, String receiver)
    {
        if (m_hWnd == 0)
        {
            throw new RuntimeException("Serial port is not open.");
        }
        if (m_Trace == TraceLevel.VERBOSE)
        {
            notifyTrace(new TraceEventArgs(TraceTypes.SENT, data));
        }
        //Reset last position if Eop is used.
        synchronized (m_syncBase.m_ReceivedSync)
        {
            m_syncBase.m_LastPosition = 0;
        }
        byte[] buff = GXSynchronousMediaBase.getAsByteArray(data);
        if (buff == null)
        {
            throw new IllegalArgumentException("Data send failed. Invalid data.");
        }
        NativeCode.write(m_hWnd, buff, m_WriteTimeout);
        this.m_BytesSend += buff.length;
    }

    private void NotifyMediaStateChange(MediaState state)
    {
        for (IGXMediaListener listener : MediaListeners) 
        {                
            if (m_Trace.ordinal() >= TraceLevel.ERROR.ordinal())
            {
                listener.onTrace(this, new TraceEventArgs(TraceTypes.INFO, state));
            }
            listener.onMediaStateChange(this, new MediaStateEventArgs(state));
        }
    }
  
    /*
     * Open serial port and call to phone number.
     */
    @Override    
    public final void open() throws Exception
    {
        close();
        try
        {            
            if (m_PortName == null || m_PortName == "")
            {
                throw new IllegalArgumentException("Serial port is not selected.");
            }
            synchronized (m_syncBase.m_ReceivedSync)
            {
                m_syncBase.m_LastPosition = 0;
            }
            NotifyMediaStateChange(MediaState.OPENING);
            if (m_Trace.ordinal() >= TraceLevel.INFO.ordinal())
            {
                String eop = "None";
                if (getEop() instanceof byte[])
                {
                }
                else if (getEop() != null)
                {
                    eop = getEop().toString();
                }
                notifyTrace(new TraceEventArgs(TraceTypes.INFO, "Settings: Port: " + this.getPortName() + " Baud Rate: " + getBaudRate() + " Data Bits: " + (new Integer(getDataBits())).toString() + " Parity: " + getParity().toString() + " Stop Bits: " + getStopBits().toString() + " Eop:" + eop));
            }             
            long tmp[] = new long[1];
            m_hWnd = NativeCode.openSerialPort(m_PortName, tmp);            
            //If user has change values before open.
            if (m_BaudRate != 9600)
            {
                setBaudRate(m_BaudRate);
            }
            if (m_DataBits != 8)
            {
                setDataBits(m_DataBits);
            }
            if (m_Parity != Parity.NONE)
            {
                setParity(m_Parity);
            }
            if (m_StopBits != StopBits.ONE)
            {
                setStopBits(m_StopBits);
            }
            m_Closing = tmp[0];
            Receiver = new GXReceiveThread(this, m_hWnd);
            setRtsEnable(true);
            setDtrEnable(true);            
            Receiver.start();            
            Thread.sleep(100);
            try
            {
                //Send AT
                synchronized (getSynchronous())
                {
                    if (getInitializeCommands() != null)
                    {
                        for (String it : getInitializeCommands())
                        {
                            sendCommand(it + "\r\n", m_CommadWaitTime, null, true);
                        }
                    }
                    //Send AT few times. This helps for several modems.
                    String reply;
                    sendCommand("AT\r", m_CommadWaitTime, null, false);
                    sendCommand("AT\r", m_CommadWaitTime, null, false);                    
                    if (m_Server)
                    {
                        if (!"OK".equalsIgnoreCase(sendCommand("AT\r", m_CommadWaitTime, null, false)))
                        {
                            reply = sendCommand("AT\r", m_CommadWaitTime, null, true);
                            if (!"OK".equalsIgnoreCase(reply))
                            {
                                throw new Exception("Invalid reply.");
                            }
                        }
                        reply = sendCommand("ATA\r", m_CommadWaitTime, null, true);
                        if (!"ATA".equalsIgnoreCase(reply))
                        {
                            throw new Exception("Invalid reply.");
                        }
                        m_Progress = Progress.CONNECTING;                        
                    }
                    else
                    {
                        //Send AT
                        if ("OK".compareToIgnoreCase(sendCommand("AT\r", m_CommadWaitTime, null, false)) != 0)
                        {
                            reply = sendCommand("AT\r", m_CommadWaitTime, null, true);
                            if ("OK".compareToIgnoreCase(reply) != 0)                                
                            {
                                throw new Exception("Invalid reply.");
                            }
                        }
                        m_Progress = Progress.CONNECTING;
                        if (m_PhoneNumber == null || m_PhoneNumber.length() == 0)
                        {
                            sendCommand("ATD\r\n", m_ConnectionWaitTime, null, true);                            
                        }
                        else
                        {
                            sendCommand("ATD" + m_PhoneNumber + "\r\n", m_ConnectionWaitTime, null, true);
                        }
                        m_Progress = Progress.CONNECTED;
                    }
                }
            }
            catch (Exception ex)
            {
                close();
                throw ex;
            }
            NotifyMediaStateChange(MediaState.OPEN);
        }
        catch (Exception ex)
        {
            close();
            throw ex;
        }        
    }   

    private void sendBytes(byte[] value)
    {
        if (m_Trace == TraceLevel.VERBOSE)
        {
            notifyTrace(new TraceEventArgs(TraceTypes.SENT, value));
        }
        m_BytesSend += value.length;
        //Reset last position if Eop is used.
        synchronized (m_syncBase.m_ReceivedSync)
        {
            m_syncBase.m_LastPosition = 0;
        }
        NativeCode.write(m_hWnd, value, m_WriteTimeout);
    }

    private String sendCommand(String cmd, int wt, String eop, boolean throwError)
    {
        ReceiveParameters<String> p = new ReceiveParameters<String>(String.class);        
        p.setWaitTime(wt);
        p.setEop(eop != null ? eop : "\r\n");
        if (p.getEop().equals(""))
        {
            p.setEop(null);
            p.setCount(cmd.length());
        }
        try 
        {
            sendBytes(cmd.getBytes("ASCII"));
        } 
        catch (UnsupportedEncodingException ex) 
        {
            throw new RuntimeException(ex.getMessage());
        }
        StringBuilder sb = new StringBuilder();
        int index = -1;
        String reply = "";
        while (index == -1)
        {
            if (!receive(p))
            {
                if (throwError)
                {
                    throw new RuntimeException("Failed to receive answer from the modem. Check serial port.");
                }
                return "";
            }
            sb.append(p.getReply());
            reply = sb.toString();
            //Remove echo.                
            if (sb.length() >= cmd.length() && reply.startsWith(cmd))
            {
                sb.delete(0, cmd.length());
                reply = sb.toString();
                //Remove echo and return if we are not expecting reply.
                if (eop != null && eop.equals(""))
                {
                    return "";
                }
            }
            if (eop != null)
            {
                index = reply.lastIndexOf(eop);
            }
            else if (reply.length() > 5)
            {
                index = reply.lastIndexOf("\r\nOK\r\n");
                if (index == -1)
                {
                    index = reply.lastIndexOf("ERROR:");                    
                    if (index == -1)
                    {
                        index = reply.lastIndexOf("CONNECT");
                        if (index == -1)
                        {
                            index = reply.lastIndexOf("NO CARRIER");
                            if (index != -1)
                            {
                                String str = "Connection failed: no carrier (when telephone call was being established). ";                                
                                int start = reply.indexOf("CAUSE:");
                                if (start != -1)
                                {
                                    if (start < index)
                                    {
                                        str += reply.substring(start, index).trim();
                                    }
                                    else
                                    {
                                        str += reply.substring(start).trim();
                                    }
                                }
                                str += "\r\n" + sendCommand("AT+CEER\r", wt, null, false);                                
                                throw new RuntimeException(str);
                            }
                            if (reply.lastIndexOf("ERROR") != -1)
                            {
                                throw new RuntimeException("Connection failed: error (when telephone call was being established).");
                            }
                            if (reply.lastIndexOf("BUSY") != -1)
                            {
                                throw new RuntimeException("Connection failed: busy (when telephone call was being established).");
                            }
                        }
                    }
                }
                //If there is a message before OK show it.
                else if (index != 0)
                {
                    reply = reply.substring(0, index);
                    index = 0;
                }
            }
            p.setReply(null);
        }
        if (index != 0 & eop == null)
        {
            reply = reply.substring(0, 0) + reply.substring(0 + index);
        }
        reply = reply.trim();
        return reply;
    }
    
    /** 
     * <inheritdoc cref="IGXMedia.Close"/>        
    */    
    @Override       
    public final void close()
    {       
        if (m_hWnd != 0)
        {                
            try
            {
                NotifyMediaStateChange(MediaState.CLOSING);
            }
            catch (RuntimeException ex)
            {
                notifyError(ex);
                throw ex;
            }
            finally
            {                 
                if (m_Progress != Progress.NONE)
                {
                    try
                    {
                        //Send AT
                        synchronized (getSynchronous())
                        {
                            if (m_Progress == Progress.CONNECTED)
                            {
                                try 
                                {
                                    Thread.sleep(1000);
                                } 
                                catch (InterruptedException ex) 
                                {
                                    throw new RuntimeException(ex.getMessage());
                                }
                                ReceiveParameters<String> p = new ReceiveParameters<String>(String.class);        
                                p.setWaitTime(m_CommadWaitTime);
                                p.setCount(3);
                                try 
                                {
                                    sendBytes("+++".getBytes("ASCII"));
                                } 
                                catch (UnsupportedEncodingException ex) 
                                {
                                    throw new RuntimeException(ex.getMessage());
                                }
                                //It's OK if this fails.
                                receive(p);
                                sendCommand("ATH0\r", m_ConnectionWaitTime, null, false);
                            }                            
                        }
                    }
                    finally
                    {
                        m_Progress = Progress.NONE;
                        if (Receiver != null)
                        {
                            Receiver.interrupt();                    
                            Receiver = null;
                        }
                    }
                }
                try
                {
                    NativeCode.closeSerialPort(m_hWnd, m_Closing);
                }
                catch (java.lang.Exception e)
                {
                    //Ignore all errors on close.                    
                }
                m_hWnd = 0;    
                NotifyMediaStateChange(MediaState.CLOSED);
                m_BytesSend = m_BytesReceived = 0;
                m_syncBase.m_ReceivedSize = 0;
            }
        }                
    }

    /** 
    Used baud rate for communication.

    Can be changed without disconnecting.
  */ 
    public final int getBaudRate()
    {
        if (m_hWnd == 0)
        {
            return m_BaudRate;        
        }
        return NativeCode.getBaudRate(m_hWnd);
    }
    public final void setBaudRate(int value)
    {       
        boolean change = getBaudRate() != value;            
        if (change)
        {
            if (m_hWnd == 0)
            {
                m_BaudRate = value;        
            }
            else
            {
                NativeCode.setBaudRate(m_hWnd, value);
            }
            NotifyPropertyChanged("BaudRate");
        }
    }
        
     /** 
    Gets or sets the phone number.    
  */ 
    public final String getPhoneNumber()
    {
        return m_PhoneNumber;
    }
    public final void setPhoneNumber(String value)
    {       
        boolean change = m_PhoneNumber == null || m_PhoneNumber.equalsIgnoreCase(value);
        m_PhoneNumber = value;
        if (change)
        {
            NotifyPropertyChanged("BaudRate");
        }
    }

    /** 
     PIN Code.
    */    
    public final String getPINCode()
    {
        return m_PIN;
    }
    public final void setPINCode(String value)
    {
        m_PIN = value;
    }
    
    /*
     * Get or set how long (ms) modem answer is waited when connection is made.
     */
    public final int getConnectionWaitTime()
    {
        return m_ConnectionWaitTime;
    }
    
    public final void setConnectionWaitTime(int value)
    {       
        boolean change = m_ConnectionWaitTime != value;
        m_ConnectionWaitTime = value;
        if (change)
        {
            NotifyPropertyChanged("ConnectionWaitTime");
        }
    }    
    
    /*
     * Get or set how long (ms) modem answer is waited when command is send for the modem.
     */
    public final int getCommandWaitTime()
    {
        return m_CommadWaitTime;
    }
    
    public final void setCommandWaitTime(int value)
    {       
        boolean change = m_CommadWaitTime != value;
        m_CommadWaitTime = value;
        if (change)
        {
            NotifyPropertyChanged("CommadWaitTime");
        }
    }    
    
    
    
    /** 
     True if the port is in a break state; otherwise, false.
    */
    public final boolean getBreakState()
    {
        return NativeCode.getBreakState(m_hWnd);
    }
    public final void setBreakState(boolean value)
    {
        boolean change;
        change = getBreakState() != value;
        if (change)
        {
            NativeCode.setBreakState(m_hWnd, value);
            NotifyPropertyChanged("BreakState");
        }
    }

    /* 
     * Gets the number of bytes in the receive buffer.
    */
    public final int getBytesToRead()
    {
        return NativeCode.getBytesToRead(m_hWnd);
    }

    /* 
     * Gets the number of bytes in the send buffer.
     */ 
    public final int getBytesToWrite()
    {
        return NativeCode.getBytesToWrite(m_hWnd);
    }

    /* 
    * Gets the state of the Carrier Detect line for the port.
    */
    public final boolean getCDHolding()
    {
        return NativeCode.getCDHolding(m_hWnd);
    }

    /* 
     * Gets the state of the Clear-to-Send line.
     */    
    public final boolean getCtsHolding()
    {
        return NativeCode.getCtsHolding(m_hWnd);
    }
   
    /** 
     * Gets or sets the standard length of data bits per byte.   
     */
    public final int getDataBits()
    {
        if (m_hWnd == 0)
        {
            return m_DataBits;
        }
        return NativeCode.getDataBits(m_hWnd);
    }

    public final void setDataBits(int value)
    {
        boolean change;
        change = getDataBits() != value;        
        if (change)
        {
            if (m_hWnd == 0)
            {
                m_DataBits = value;
            }
            else
            {
                NativeCode.setDataBits(m_hWnd, value);
            }
            NotifyPropertyChanged("DataBits");
        }
    }
    
        /** 
         Gets the state of the Data Set Ready (DSR) signal.
        */
        public final boolean getDsrHolding()
        {
            return NativeCode.getDsrHolding(m_hWnd);
        }

        /* 
         * Gets or sets a value that enables the Data Terminal Ready 
         * (DTR) signal during serial communication.        
        */
        public final boolean getDtrEnable()
        {
            return NativeCode.getDtrEnable(m_hWnd);
        }
        public final void setDtrEnable(boolean value)
        {
            boolean change;
            change = getDtrEnable() != value;
            NativeCode.setDtrEnable(m_hWnd, value);
            if (change)
            {                
                NotifyPropertyChanged("DtrEnable");
            }
        }
        
        /* 
         * Gets or sets the handshaking protocol for serial port transmission of data.
        */
        public final Handshake getHandshake()
        {
            return Handshake.values()[NativeCode.getHandshake(m_hWnd)];
        }
        public final void setHandshake(Handshake value)
        {
            boolean change;
            change = getHandshake() != value;
            if (change)
            {
                NativeCode.setHandshake(m_hWnd, value.ordinal());
                NotifyPropertyChanged("Handshake");
            }
        }

    /** <inheritdoc cref="IGXMedia.IsOpen"/>
     <seealso char="Connect">Open
     <seealso char="Close">Close
    */
    @Override
    public final boolean isOpen()
    {
        return m_hWnd != 0;
    }
    
    /** 
     * Gets or sets the parity-checking protocol.
     */
    public final Parity getParity()
    {
        if (m_hWnd == 0)
        {
            return m_Parity;
        }
        return Parity.values()[NativeCode.getParity(m_hWnd)];
    }

    public final void setParity(Parity value)
    {        
        boolean change;
        change = getParity() != value;        
        if (change)
        {
            if (m_hWnd == 0)
            {
                m_Parity = value;
            }
            else
            {
                NativeCode.setParity(m_hWnd, value.ordinal());
            }
            NotifyPropertyChanged("Parity");
        }
    }

    /** 
     Gets or sets the port for communications, including but not limited to all available COM ports.
    */
    public final String getPortName()
    {
        return m_PortName;
    }
                
    public final void setPortName(String value)
    {
        boolean change;
        change = !value.equals(m_PortName);
        m_PortName = value;
        if (change)
        {
            NotifyPropertyChanged("PortName");
        }
    }

    /*
     * Gets or sets the size of the System.IO.Ports.SerialPort input buffer.
     */ 
    public final int getReadBufferSize()
    {
        return m_ReadBufferSize;
    }
    public final void setReadBufferSize(int value)
    {
        boolean change;
        change = getReadBufferSize() != value;
        if (change)
        {
            m_ReadBufferSize = value;
            NotifyPropertyChanged("ReadBufferSize");
        }
    }

    /* 
    * Gets or sets the number of milliseconds before a time-out occurs when a read operation does not finish.
    */ 
    public final int getReadTimeout()
    {
        return m_ReadTimeout;
    }
    public final void setReadTimeout(int value)
    {
        boolean change = m_ReadTimeout != value;
        m_ReadTimeout = value;
        if (change)
        {
            NotifyPropertyChanged("ReadTimeout");
        }
    }
    
    /* 
     * Gets or sets a value indicating whether the 
     * Request to Send (RTS) signal is enabled during serial communication.
    */
    public final boolean getRtsEnable()
    {
        return NativeCode.getRtsEnable(m_hWnd);
    }
    public final void setRtsEnable(boolean value)
    {
        boolean change;
        change = getRtsEnable() != value;
        NativeCode.setRtsEnable(m_hWnd, value);
        if (change)
        {            
            NotifyPropertyChanged("RtsEnable");
        }
    }

    /** 
     Gets or sets the standard number of stopbits per byte.    
    */
     public final StopBits getStopBits()
    {
        if (m_hWnd == 0)
        {
            return m_StopBits;
        }
        return StopBits.values()[NativeCode.getStopBits(m_hWnd)];
    }
    public final void setStopBits(StopBits value)
    {
        boolean change;
        change = getStopBits() != value;
        if (change)
        {
            if (m_hWnd == 0)
            {
                m_StopBits = value;
            }
            else
            {
                NativeCode.setStopBits(m_hWnd, value.ordinal());
            }
            NotifyPropertyChanged("StopBits");
        }
    }

    /* 
     * Gets or sets the number of milliseconds before a time-out 
     * occurs when a write operation does not finish.
     */
    public final int getWriteTimeout()
    {
        return m_WriteTimeout;
    }
    public final void setWriteTimeout(int value)
    {
        boolean change = m_WriteTimeout != value;
        if (change)
        {
            m_WriteTimeout = value;
            NotifyPropertyChanged("WriteTimeout");
        }
    }
    
    @Override
    public final <T> boolean receive(ReceiveParameters<T> args)
    {
        return m_syncBase.receive(args);
    }

    /** 
     Sent byte count.

     @see BytesReceived BytesReceived
     @see ResetByteCounters ResetByteCounters
    */
    @Override
    public final long getBytesSent()
    {
        return m_BytesSend;
    }

    /** 
     Received byte count.

     @see BytesSent BytesSent
     @see ResetByteCounters ResetByteCounters
    */
    @Override
    public final long getBytesReceived()
    {
        return m_BytesReceived;
    }

    /** 
     Resets BytesReceived and BytesSent counters.

     @see BytesSent BytesSent
     @see BytesReceived BytesReceived
    */
    @Override
    public final void resetByteCounters()
    {
        m_BytesSend = m_BytesReceived = 0;
    }
   
    /** 
     Media settings as a XML string.
    */
    @Override
    public final String getSettings()
    {        
        return null;
        //TODO:
    }
    
    @Override
    public final void setSettings(String value)
    {   
        //TODO:
    }
    
    @Override
    public final void copy(Object target)
    {
        GXTerminal tmp = (GXTerminal)target;
        setPortName(tmp.getPortName());
        setBaudRate(tmp.getBaudRate()); 
        setStopBits(tmp.getStopBits());
        setParity(tmp.getParity());
        setDataBits(tmp.getDataBits());
    }

    @Override
    public String getName()
    {
        return getPortName();
    }

    @Override
    public String getMediaType()
    {
        return "Terminal";
    }

    /** <inheritdoc cref="IGXMedia.Synchronous"/>
    */
    @Override
    public final Object getSynchronous()
    {
        synchronized (this)
        {
            int[] tmp = new int[]{m_Synchronous};
            GXSync obj = new GXSync(tmp);
            m_Synchronous = tmp[0];
            return obj;
        }
    }

    /** <inheritdoc cref="IGXMedia.IsSynchronous"/>
    */
    @Override
    public final boolean getIsSynchronous()
    {
        synchronized (this)
        {
            return m_Synchronous != 0;
        }
    }

    /** <inheritdoc cref="IGXMedia.ResetSynchronousBuffer"/>
    */
    @Override
    public final void resetSynchronousBuffer()
    {
        synchronized (m_syncBase.m_ReceivedSync)
        {
            m_syncBase.m_ReceivedSize = 0;
        }
    }

    /** <inheritdoc cref="IGXMedia.Validate"/>
    */
    @Override
    public final void validate()
    {
        if (getPortName() == null || getPortName().length() == 0)
        {
            throw new RuntimeException("Invalid port name.");
        }
    }    

    /*
     * Get or set modem initial settings.
     */
    public String[] getInitializeCommands()
    {
        return m_InitializeCommands;
    }
    
    public final void setInitializeCommands(String[] value)
    {
        m_InitializeCommands = value;
    }
        
    /** <inheritdoc cref="IGXMedia.Eop"/>
    */
    @Override
    public final Object getEop()
    {
        return privateEop;
    }
    @Override
    public final void setEop(Object value)
    {
        privateEop = value;
    }

    @Override
    public void addListener(IGXMediaListener listener) 
    {        
        MediaListeners.add(listener);       
    }

    @Override
    public void removeListener(IGXMediaListener listener) 
    {
        MediaListeners.remove(listener);
    }          
}