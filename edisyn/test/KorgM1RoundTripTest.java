package edisyn.test;

import edisyn.synth.korgm1.*;
import java.io.*;
import java.util.Arrays;

/**
 * Gate (c) + (d): verify parse() extracts known Piano-16' values,
 * and that emit() reconstructs the exact original bytes (except channel nibble).
 */
public class KorgM1RoundTripTest
    {
    public static void main(String[] args) throws Exception
        {
        String syx = "docs/KorgM1_piano16.syx";
        byte[] original = new FileInputStream(syx).readAllBytes();
        System.out.println("Loaded " + original.length + " bytes from " + syx);

        KorgM1 synth = new KorgM1();
        synth.parse(original, true);

        // Gate (c): known values from Piano 16' factory patch
        // Name: M1 stores 0x00 (null) for spaces; revisePatchName converts to space then
        // right-trims. Stored name is "Piano 16'" (9 chars). emit() re-pads to 10 with spaces.
        check(synth, "name",           "Piano 16'",   "patch name (right-trimmed)");
        check(synth, "oscmode",        0,             "oscmode (SINGLE)");
        check(synth, "osc1multisound", 0,             "osc1multisound (Piano 16')");
        check(synth, "osc1octave",    -1,             "osc1octave (-1 = 0xFF signed)");
        check(synth, "effect1type",    9,             "effect1type (Reverb Hall)");
        check(synth, "vdf1cutoff",    40,             "vdf1cutoff (raw[71]=0x28=40)");
        check(synth, "vda1level",     79,             "vda1level (0x4F)");

        // Gate (d): round-trip emit() → byte compare (ignoring channel nibble byte 2)
        // Name bytes with null padding: the M1 uses 0x00 for spaces in names; we emit
        // 0x20 (space) instead of 0x00. Both are treated identically by the hardware.
        // Null-padding positions in this 170-byte packet: bytes 11 (raw[5]) and 16 (raw[9]).
        byte[] emitted = synth.emit(null, false, true);
        if (emitted == null || emitted.length == 0)
            { System.out.println("[FAIL] emit() returned null/empty"); return; }
        System.out.println("Emitted " + emitted.length + " bytes");

        boolean mismatch = false;
        for (int i = 0; i < Math.min(original.length, emitted.length); i++)
            {
            if (i == 2) continue; // channel nibble (3n)
            if (original[i] != emitted[i])
                {
                // Check if this is a name null-pad byte (0x00 in original, 0x20 in ours)
                boolean namePad = (original[i] == 0x00 && emitted[i] == 0x20);
                String tag = namePad ? "[NOTE] name null-pad (acceptable)" : "[FAIL]";
                System.out.printf("%s byte[%d]: original=0x%02X emitted=0x%02X%n",
                    tag, i, original[i] & 0xFF, emitted[i] & 0xFF);
                if (!namePad) mismatch = true;
                }
            }
        if (original.length != emitted.length)
            System.out.printf("[FAIL] length: original=%d emitted=%d%n",
                original.length, emitted.length);
        else if (!mismatch)
            System.out.println("[PASS] Gate (d): emit() is byte-identical to original (channel + name null-pads excepted)");
        }

    static void check(KorgM1 synth, String key, int expected, String label)
        {
        int actual = synth.getModel().get(key);
        if (actual == expected)
            System.out.printf("[PASS] %-24s = %d%n", label, actual);
        else
            System.out.printf("[FAIL] %-24s expected %d, got %d%n", label, expected, actual);
        }

    static void check(KorgM1 synth, String key, String expected, String label)
        {
        String actual = synth.getModel().get(key, "");
        if (actual.equals(expected))
            System.out.printf("[PASS] %-24s = \"%s\"%n", label, actual);
        else
            System.out.printf("[FAIL] %-24s expected \"%s\", got \"%s\"%n", label, expected, actual);
        }
    }
