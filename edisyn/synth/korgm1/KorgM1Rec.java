/**
   Copyright 2024 by Sean Luke
   Licensed under the Apache License version 2.0
*/

package edisyn.synth.korgm1;
import edisyn.*;

public class KorgM1Rec extends Recognize
    {
    /** Unpack Korg 7-to-8 MIDI bytes starting at offset, ignoring trailing F7. */
    public static byte[] convertTo8Bit(byte[] data, int offset)
        {
        int size = (data.length - offset - 1) / 8 * 7;
        if ((data.length - offset - 1) % 8 > 0)
            size += ((data.length - offset - 1) % 8 - 1);
        byte[] newd = new byte[size];
        int j = 0;
        for (int i = offset; i < data.length; i += 8)
            {
            for (int x = 0; x < 7; x++)
                {
                if (j + x < newd.length)
                    newd[j + x] = (byte)(data[i + x + 1] | (byte)(((data[i] >>> x) & 0x1) << 7));
                }
            j += 7;
            }
        return newd;
        }

    /** Pack 8-bit bytes into Korg 7-to-8 MIDI format. */
    public static byte[] convertTo7Bit(byte[] data)
        {
        int size = (data.length) / 7 * 8;
        if (data.length % 7 > 0)
            size += (1 + data.length % 7);
        byte[] newd = new byte[size];
        int j = 0;
        for (int i = 0; i < data.length; i += 7)
            {
            for (int x = 0; x < 7; x++)
                {
                if (j + x + 1 < newd.length)
                    {
                    newd[j + x + 1] = (byte)(data[i + x] & 127);
                    newd[j] = (byte)(newd[j] | (((data[i + x] >>> 7) & 1) << x));
                    }
                }
            j += 8;
            }
        return newd;
        }

    /** Recognizes a single Program Parameter Dump (40H), 170 bytes. */
    public static boolean recognize(byte[] data)
        {
        return (data.length == 170 &&
                data[0] == (byte)0xF0 &&
                data[1] == (byte)0x42 &&
                (data[2] & 0xF0) == 0x30 &&
                data[3] == (byte)0x19 &&
                data[4] == (byte)0x40 &&
                data[data.length - 1] == (byte)0xF7);
        }

    /** Recognizes an ALL DATA bank dump (50H). */
    public static boolean recognizeBank(byte[] data)
        {
        return (data.length > 1000 &&
                data[0] == (byte)0xF0 &&
                data[1] == (byte)0x42 &&
                (data[2] & 0xF0) == 0x30 &&
                data[3] == (byte)0x19 &&
                data[4] == (byte)0x50 &&
                data[data.length - 1] == (byte)0xF7);
        }
    }
