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

/** 
 Describes available settings for the media.
*/
public class AvailableMediaSettings
{
    /** 
     All serial port properties are shown.
    */
    public static final AvailableMediaSettings All = new AvailableMediaSettings(-1);
    /** 
     Is port name shown.
    */
    public static final AvailableMediaSettings PortName = new AvailableMediaSettings(0x1);
    /** 
     Is baud rate shown.
    */
    public static final AvailableMediaSettings BaudRate = new AvailableMediaSettings(0x2);
    /** 
     Is data bits shown.
    */
    public static final AvailableMediaSettings DataBits = new AvailableMediaSettings(0x4);
    /** 
     Is Parity shown.
    */
    public static final AvailableMediaSettings Parity = new AvailableMediaSettings(0x8);

    /** 
     Is stop bits shown.
    */
    public static final AvailableMediaSettings StopBits = new AvailableMediaSettings(0x10);
    
            
    private int intValue;
    private static java.util.HashMap<Integer, AvailableMediaSettings> mappings;
    private static java.util.HashMap<Integer, AvailableMediaSettings> getMappings()
    {
        if (mappings == null)
        {
            synchronized (AvailableMediaSettings.class)
            {
                if (mappings == null)
                {
                    mappings = new java.util.HashMap<Integer, AvailableMediaSettings>();
                }
            }
        }
        return mappings;
    }

    private AvailableMediaSettings(int value)
    {
        intValue = value;
        synchronized (AvailableMediaSettings.class)
        {
            getMappings().put(value, this);
        }
    }

    public int getValue()
    {
        return intValue;
    }

    public static AvailableMediaSettings forValue(int value)
    {
        synchronized (AvailableMediaSettings.class)
        {
            AvailableMediaSettings enumObj = getMappings().get(value);
            if (enumObj == null)
            {
                return new AvailableMediaSettings(value);
            }
            else
            {
                return enumObj;
            }
        }
    }
}