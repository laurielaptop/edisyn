/**
   Copyright 2024 by Sean Luke
   Licensed under the Apache License version 2.0
*/

package edisyn.synth.korgm1;
import edisyn.*;

public class KorgM1CombiRec extends Recognize
    {
    /** Recognizes a single Combination Parameter Dump (49H), 148 bytes. */
    public static boolean recognize(byte[] data)
        {
        return (data.length == 148 &&
                data[0] == (byte)0xF0 &&
                data[1] == (byte)0x42 &&
                (data[2] & 0xF0) == 0x30 &&
                data[3] == (byte)0x19 &&
                data[4] == (byte)0x49 &&
                data[data.length - 1] == (byte)0xF7);
        }

    /** Recognizes an All Combination Parameter Dump (4DH). */
    public static boolean recognizeBank(byte[] data)
        {
        return (data.length > 1000 &&
                data[0] == (byte)0xF0 &&
                data[1] == (byte)0x42 &&
                (data[2] & 0xF0) == 0x30 &&
                data[3] == (byte)0x19 &&
                data[4] == (byte)0x4D &&
                data[data.length - 1] == (byte)0xF7);
        }
    }
