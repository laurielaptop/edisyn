# Korg M1 Editor — Implementation Plan

## Overview

Add a Korg M1 Program patch editor to Edisyn. The M1 (1988) is a PCM workstation using Korg's "AI Synthesis" system. In Single mode it has 16 voices / 1 oscillator section; in Double mode 8 voices / 2 oscillator sections. Each section has a VDF (digital filter), VDA (amplifier), and pitch EG. There are two onboard digital multi-effects.

The initial target is **Program (single patch) editing only**. Combination mode is deferred to a later phase.

---

## Files to Create

```
edisyn/synth/korgm1/
    KorgM1.java         — main editor, extends Synth
    KorgM1Rec.java      — SysEx recognizer, extends Recognize
    KorgM1.init         — default patch as raw SysEx bytes
    KorgM1.html         — About panel (HTML)
```

Register in `edisyn/synth/synths.txt`:
```
edisyn.synth.korgm1.KorgM1    Korg    Korg M1
```

---

## SysEx Format (from M1 Owner's Manual, Section 3)

### Header (all M1 SysEx messages)

```
F0  42  3n  19  <func>  <data...>  F7
```

| Byte | Value | Meaning |
|---|---|---|
| `F0` | — | Exclusive Status |
| `42` | — | Korg Manufacturer ID |
| `3n` | `0x30 \| ch` | Global MIDI channel (n = 0–15) |
| `19` | — | M1 Model ID |
| func | — | Function code (see below) |
| data | — | Packed payload |
| `F7` | — | End of Exclusive |

### 7-to-8 Bit Packing (confirmed from manual p.126)

The M1 uses Korg's standard packing: every **7 raw bytes** become **8 MIDI bytes**.

```
Raw data:   [ D0 ][ D1 ][ D2 ][ D3 ][ D4 ][ D5 ][ D6 ]   (8-bit each)
MIDI bytes: [ 0 | D6b7 D5b7 D4b7 D3b7 D2b7 D1b7 D0b7 ]   MSBs packed
            [ 0 | D0 bits 6-0 ]
            [ 0 | D1 bits 6-0 ]
            ...
            [ 0 | D6 bits 6-0 ]
```

The first MIDI byte in each group holds bit 7 of each of the 7 data bytes (bit 0 = MSB of D0, bit 1 = MSB of D1, etc.). The following 7 MIDI bytes hold the low 7 bits of each data byte.

Unpacking (Java):
```java
static byte[] unpack(byte[] midi, int offset, int count) {
    byte[] data = new byte[count];
    int j = 0;
    for (int i = offset; j < count; i += 8) {
        byte msbs = midi[i];
        for (int x = 0; x < 7 && j < count; x++, j++)
            data[j] = (byte)((midi[i + 1 + x] & 0x7F) | (((msbs >> x) & 1) << 7));
    }
    return data;
}
```

Packing (Java):
```java
static byte[] pack(byte[] data) {
    int groups = (data.length + 6) / 7;
    byte[] midi = new byte[groups * 8];
    for (int g = 0; g < groups; g++) {
        byte msbs = 0;
        for (int x = 0; x < 7; x++) {
            int di = g * 7 + x;
            if (di < data.length) {
                msbs |= (byte)(((data[di] >> 7) & 1) << x);
                midi[g * 8 + 1 + x] = (byte)(data[di] & 0x7F);
            }
        }
        midi[g * 8] = msbs;
    }
    return midi;
}
```

---

## Function Codes and Message Formats

### Messages Edisyn Sends to M1

| Purpose | Format |
|---|---|
| **Request current program dump** | `F0 42 3n 19 10 F7` |
| **Request all programs (bank dump)** | `F0 42 3n 19 1C 0c F7` (c=0:Internal, 1:Card) |
| **Write current working memory to slot** | `F0 42 3n 19 11 0c pp F7` (c=bank, pp=0–99) |
| **Request combination dump** | `F0 42 3n 19 19 F7` |
| **Request all combinations** | `F0 42 3n 19 1D 0c F7` |

### Messages Edisyn Receives from M1

| Function | Format | Description |
|---|---|---|
| `40H` | `F0 42 3n 19 40 <164 bytes> F7` | Program Parameter Dump |
| `4CH` | `F0 42 3n 19 4C 0c mem <data> F7` | All Program Parameter Dump |
| `41H` | `F0 42 3n 19 41 pp pos vL vH F7` | Parameter Change (real-time) |
| `23H` | `F0 42 3n 19 23 F7` | Data Load Completed |
| `24H` | `F0 42 3n 19 24 F7` | Data Load Error |
| `21H` | `F0 42 3n 19 21 F7` | Write Completed |
| `22H` | `F0 42 3n 19 22 F7` | Write Error |

### Program Parameter Dump (40H) — Total 170 bytes

```
F0 42 3n 19 40 <164 data bytes> F7
```

143 raw parameters → packed to 164 MIDI bytes: `7×20+3 = 143` → `8×20+(1+3) = 164`

---

## Program Parameter Map — TABLE 1 (143 parameters, 0–142)

All byte indices are raw (unpacked). Ranges are given in hex where signed, decimal where unsigned.

### Program Name (00–09)
10 ASCII characters, `20h–7Fh` (' ' to '~')

### Oscillator Common (10–18)
| # | Parameter | Range / Notes |
|---|---|---|
| 10 | Oscillator Mode | 0=SINGLE, 1=DOUBLE, 2=DRUM |
| 11 | Assign / Hold | bit0=0:POLY 1:MONO; bit1=0:HOLD OFF 1:ON |
| 12 | OSC-1 Multisound | 00h–63h=Internal, 64h+=Card |
| 13 | OSC-1 Octave | FFh–01h (signed: 16', 8', 4') |
| 14 | OSC-2 Multisound | 00h–63h=Internal, 64h+=Card |
| 15 | OSC-2 Octave | FFh–01h |
| 16 | Interval | F4h–0Ch = -12 to +12 semitones |
| 17 | Detune | CEh–32h = -50 to +50 cents |
| 18 | Delay Start | 00h–63h = 0–99 |

### Pitch MG (19–22)
| # | Parameter | Range / Notes |
|---|---|---|
| 19 | Wave Form + Enable flags | bits 0–1: wave (0=Tri,1=UpSaw,2=DnSaw,3=Rect); bit5=OSC-1 MG on; bit6=OSC-2 MG on; bit7=Key Sync |
| 20 | Frequency | 00h–63h = 0–99 |
| 21 | Delay | 00h–63h = 0–99 |
| 22 | Intensity | 00h–63h = 0–99 |

### Cutoff MG (23–26)
| # | Parameter | Range / Notes |
|---|---|---|
| 23 | Wave Form + Enable flags | same bit layout as Pitch MG |
| 24 | Frequency | 00h–63h = 0–99 |
| 25 | Delay | 00h–63h = 0–99 |
| 26 | Intensity | 00h–63h = 0–99 |

### After Touch (27–31)
| # | Parameter | Range / Notes |
|---|---|---|
| 27 | Pitch | F4h–0Ch = -12 to +12 |
| 28 | Pitch MG | F4h–0Ch = -12 to +12 |
| 29 | VDF Cutoff | 9Dh–63h = -99 to +99 |
| 30 | VDF MG | 9Dh–63h = -99 to +99 |
| 31 | VDA Amplitude | 9Dh–63h = -99 to +99 |

### Joy Stick (32–37)
| # | Parameter | Range / Notes |
|---|---|---|
| 32 | Pitch Bend | F4h–0Ch = -12 to +12 |
| 33 | VDF Sweep Int | 9Dh–63h = -99 to +99 |
| 34 | Pitch MG Int | 00h–63h = 0–99 |
| 35 | VDF MG Frequency | 00h–03h = 0–3 |
| 36 | VDF MG Int | 00h–63h = 0–99 |
| 37 | VDF MG Frequency | 00h–03h = 0–3 |

### Effect Parameters (38–62)
25 bytes covering two effects. See `*11 EFFECT PARAMETER` table (manual p.129):

| # | Parameter | Notes |
|---|---|---|
| 38 | Effect 1 Type | 0–20: effect types, 21–33: True Stereo types |
| 39 | Effect 2 Type | same |
| 40 | Effect 1/2 Balance (L-Ch) | 00h–64h = 0–100 |
| 41 | Effect 1/2 Balance (R-Ch) | 00h–64h = 0–100 |
| 42 | Effect 2 Level (L-Ch) | 00h–64h = 0–100 |
| 43 | Effect 2 Level (R-Ch) | 00h–64h = 0–100 |
| 44 | Output 3 Pan | 00h–65h |
| 45 | Output 4 Pan | 00h–65h |
| 46 | Effect I/O Routing | bit field (see *11-1 in manual) |
| 47–54 | Effect 1 Parameters | 8 bytes, layout depends on effect type (*11-3) |
| 55–62 | Effect 2 Parameters | 8 bytes, same |

Effect types 0–33 and their per-type parameter layouts are detailed in the *11 table (manual p.129). Effect parameters include things like reverb time, delay time, chorus depth, etc.

### OSC-1 Pitch EG (63–70)
| # | Parameter | Range |
|---|---|---|
| 63 | Start Level | 9Dh–63h = -99 to +99 |
| 64 | Attack Time | 00h–63h = 0–99 |
| 65 | Attack Level | 9Dh–63h = -99 to +99 |
| 66 | Decay Time | 00h–63h = 0–99 |
| 67 | Release Time | 00h–63h = 0–99 |
| 68 | Release Level | 9Dh–63h = -99 to +99 |
| 69 | Time Velocity Sense | 9Dh–63h = -99 to +99 |
| 70 | Level Velocity Sense | 9Dh–63h = -99 to +99 |

### VDF-1 (71–77)
| # | Parameter | Range |
|---|---|---|
| 71 | Cutoff Value | 00h–63h = 0–99 |
| 72 | KBD Track Center | 00h–7Fh = C-1 to G9 |
| 73 | Cutoff KBD Track | 9Dh–63h = -99 to +99 |
| 74 | EG Intensity | 00h–63h = 0–99 |
| 75 | EG Time KBD Track | 00h–63h = 0–99 |
| 76 | EG Int Velocity Sense | 9Dh–63h = -99 to +99 |
| 77 | Cutoff Velocity Sense | 9Dh–63h = -99 to +99 |

### VDF-1 EG (78–85)
| # | Parameter | Range |
|---|---|---|
| 78 | Attack Time | 00h–63h = 0–99 |
| 79 | Attack Level | 9Dh–63h = -99 to +99 |
| 80 | Decay Time | 00h–63h = 0–99 |
| 81 | Break Point | 9Dh–63h = -99 to +99 |
| 82 | Slope Time | 00h–63h = 0–99 |
| 83 | Sustain Level | 9Dh–63h = -99 to +99 |
| 84 | Release Time | 00h–63h = 0–99 |
| 85 | Release Level | 9Dh–63h = -99 to +99 |

### VDA-1 (86–91)
| # | Parameter | Range |
|---|---|---|
| 86 | Oscillator Level | 00h–63h = 0–99 |
| 87 | KBD Track Center | 00h–7Fh = C-1 to G9 |
| 88 | Amp KBD Track Int | 9Dh–63h = -99 to +99 |
| 89 | Amp Velocity Sense | 9Dh–63h = -99 to +99 |
| 90 | EG Time KBD Track | 00h–63h = 0–99 |
| 91 | EG Time Vel Sense | 00h–63h = 0–99 |

### VDA-1 EG (92–98)
| # | Parameter | Range |
|---|---|---|
| 92 | Attack Time | 00h–63h = 0–99 |
| 93 | Attack Level | 9Dh–63h = -99 to +99 |
| 94 | Decay Time | 00h–63h = 0–99 |
| 95 | Break Point | 9Dh–63h = -99 to +99 |
| 96 | Slope Time | 00h–63h = 0–99 |
| 97 | Sustain Level | 9Dh–63h = -99 to +99 |
| 98 | Release Time | 00h–63h = 0–99 |

### EG Polarity / SW Bytes (99–102)
Each byte is a bit field (see `*1` in manual):

| Bit | Meaning |
|---|---|
| 0 | Attack Time SW (0=OFF, 1=ON) |
| 1 | Decay Time SW |
| 2 | Slope Time SW |
| 3 | Release Time SW |
| 4 | Attack Time Polarity (0=+, 1=-) |
| 5 | Decay Time Polarity |
| 6 | Slope Time Polarity |
| 7 | Release Time Polarity |

| # | Parameter |
|---|---|
| 99 | OSC-1 EG Time KBD Track, Vel SW & Polarity |
| 100 | F (VDF) EG Time Vel SW & Polarity |
| 101 | A (VDA) EG Time KBD Track SW & Polarity |
| 102 | A (VDA) EG Time Vel SW & Polarity |

### OSC-2 Section (103–142)
Exact mirror of parameters 63–102 for OSC-2. Only relevant when Oscillator Mode = DOUBLE.

---

## SysEx Recognition

```java
// KorgM1Rec.java
public static boolean recognize(byte[] data) {
    return (data.length == 170 &&
            data[0] == (byte)0xF0 &&
            data[1] == (byte)0x42 &&
            (data[2] & 0xF0) == 0x30 &&
            data[3] == (byte)0x19 &&
            data[4] == (byte)0x40 &&
            data[data.length - 1] == (byte)0xF7);
}

public static boolean recognizeBulk(byte[] data) {
    // All Program Parameter Dump (4CH): 100 programs × 164 bytes + header overhead
    // Exact length depends on memory allocation (100 or 50 programs)
    // Confirmed length from NOTE 7: 143×100 = 14300 raw bytes → 8×2042+6 = 16342 MIDI bytes + 6 header = 16342+6 = ~16348
    return (data.length > 1000 &&
            data[0] == (byte)0xF0 &&
            data[1] == (byte)0x42 &&
            (data[2] & 0xF0) == 0x30 &&
            data[3] == (byte)0x19 &&
            data[4] == (byte)0x4C);
}
```

---

## Implementation Phases

### Phase 1 — Scaffold and SysEx Round-trip

**Goal:** Compile and pass `SanityCheck` with no UI beyond a placeholder.

1. Create `edisyn/synth/korgm1/` directory
2. Copy `Blank.java` → `KorgM1.java`, `BlankRec.java` → `KorgM1Rec.java`
3. Implement `KorgM1Rec.recognize()` — check bytes as shown above
4. Add `unpack()` and `pack()` static helpers
5. Implement `KorgM1.parse()` — call `unpack()`, map 143 bytes into model keys
6. Implement `KorgM1.emit()` — map model keys to 143-byte array, call `pack()`, wrap in header
7. Create a minimal `KorgM1.init` — capture a real patch SysEx from hardware or the Korg plugin
8. Stub constructor, register in `synths.txt`
9. Run `SanityCheck` — all 143 parameters must round-trip

### Phase 2 — Core Program UI

**Goal:** All Program parameters editable via dials/choosers, organized in tabs.

Suggested tab layout:

| Tab | Contents |
|---|---|
| **OSC** | Mode, Assign/Hold; OSC-1 Multisound, Octave; OSC-2 Multisound, Octave, Interval, Detune, Delay |
| **Pitch EG** | OSC-1 pitch EG (Start/Atk/AtkLvl/Dcy/Rel/RelLvl), Vel sense; OSC-2 same |
| **VDF** | VDF-1 cutoff, KBD track, EG intensity; VDF-1 EG (ADBSR); Vel sense; VDF-2 same |
| **VDA** | VDA-1 level, KBD track, vel sense; VDA-1 EG (ADBSR); VDA-2 same |
| **Mod** | Pitch MG (wave, freq, delay, intensity, enables); Cutoff MG same; After Touch; Joy Stick |
| **FX** | Effect 1 type + 8 params; Effect 2 type + 8 params; Placement/routing |
| **Global** | Program name (10-char text field) |
| **About** | HTML panel |

Use `LabelledDial` for 0–99 and signed ranges, `Chooser` for Multisound (100+ items), Oscillator Mode, wave forms, effect types. Use `CheckBox` for bit-flag fields (Assign, Hold, MG enables). Use `LongTextField` for name.

For OSC-1/OSC-2 mirroring: show OSC-2 parameters grayed out when Oscillator Mode = SINGLE. This requires a model listener on key `oscmode`.

Reference: `KawaiK1.java` — same era PCM synth, similar parameter structure and panel layout.

### Phase 3 — MIDI Integration

**Goal:** Send, receive, and request patches from real hardware.

1. `requestCurrentDump()` → `F0 42 3n 19 10 F7`
2. `requestDump(Model)` → not available directly; call `changePatch()` then `requestCurrentDump()`; set `getAlwaysChangesPatchesOnRequestDump()` to return `true`
3. `changePatch(Model)` → Program Change message on global channel, number = bank×100 + patch (or just patch number if Internal)
4. `emit(Model, toWorkingMemory, toFile)` → build 170-byte SysEx; function `40H` for working memory
5. Program write → send `F0 42 3n 19 11 0c pp F7` after sending patch data; implement `gatherPatchInfo()` dialog for bank (Internal/Card) and number (0–99)
6. `getPauseAfterChangePatch()` → return ~50 ms (M1 needs brief settling time)
7. Implement `parseParameter()` to handle function `41H` (real-time parameter changes from the M1)

### Phase 4 — Librarian Support

**Goal:** Full librarian integration for managing 100-program banks.

1. `getPatchNumberNames()` → `{ "00", "01", ..., "99" }` (or 0–49 if 50-program allocation)
2. `getBankNames()` → `{ "Internal", "Card" }` (Card is conditionally writeable)
3. `getWriteableBanks()` → `{ true, true }` (Card bank writeable only if RAM card inserted)
4. `getSupportsPatchWrites()` → `true`
5. `getPatchLocationName(Model)` → e.g. `"I00"`, `"I99"`, `"C00"`
6. `getNextPatchLocation(Model)` → increment number 0–99, wrap Internal→Card
7. `requestCurrentDump()` / batch download loop already wired from Phase 3
8. Test batch download of all 100 Internal programs

> **Hardware finding (post-Phase 5):** `requestCurrentDump()` alone is not
> enough to make Librarian batch download (`performRequestDump()`) work.
> `Synth.performRequestDump()` calls `requestDump(Model)`, not
> `requestCurrentDump()` — the two editors must also override
> `requestDump(Model tempModel) { return requestCurrentDump(); }`, matching
> the pattern used by `YamahaDX7`, `RolandJV880`, `WaldorfMicrowave`, etc.
> Without it, `requestDump()` falls back to the `Synth` default (sends
> nothing) and the M1 never replies.
>
> Separately, since the M1's parameter dump carries no bank/number bytes,
> `changePatch()` must also update the live `model`'s `"bank"`/`"number"`
> (guarded by `setSendMIDI(false)`/`true` and `!isMerging()`), or
> `patchLocationEquals()` rejects every dump after the first as "received
> unexpected patch." See `KorgSG.java` for the reference pattern. Both fixes
> are in place as of the commit "Korg M1: fix Librarian batch download for
> Program and Combi"; confirmed working on hardware for the Internal bank on
> both editors. Card bank is untested — see the Known issue in `CLAUDE.md`
> about `changePatch()`'s Program Change value exceeding 127 for Card
> patches.

### Phase 5 — Polish and Combination Mode

1. Write `KorgM1.html` — About panel with MIDI channel setup instructions, note on memory allocation, known quirks (e.g., memory protect error on write)
2. Verify init patch and finalize `KorgM1.init`
3. Set `librarianTested()` to return `true` after thorough testing
4. Add Combination mode editor (`KorgM1Combi.java`) — 124 parameters per Combination (TABLE 2), up to 8 timbres in Multi mode; this is a separate editor registered separately in `synths.txt`

---

## Memory Allocation Note

The M1 can be configured (Global mode) for either:
- **100 Programs / 100 Combinations** (default)
- **50 Programs / 50 Combinations** (alternate, for more sequencer memory)

The SysEx bank dump size differs between the two. The editor should handle both; the `recognizeBulk()` check should accept either length. When writing patches, program numbers 0–99 (or 0–49) are valid.

---

## Key References

- **`docs/M1_E4.pdf`** — M1 Owner's Manual:
  - p.122–130: Full MIDI Exclusive Format (Section 3)
  - p.127: TABLE 1 — Program Parameter Map (143 params, 0–142)
  - p.128: TABLE 2 — Combination Parameter Map (124 params)
  - p.129: `*11` — Effect Parameter details (33 effect types, 8 params each)
  - p.130: TABLE 5 — Program Parameter Page/Position→Offset (for Parameter Change messages)
- `edisyn/synth/kawaik1/KawaiK1.java` — best structural reference (~1,433 lines, same era)
- `edisyn/synth/korgmicrokorg/KorgMicroKorgRec.java` — reference for Korg 7-to-8 packing
- `edisyn/synth/Blank.java` — template with all hook methods documented

---

## Risk Notes

- **Multisound list** — the M1 has 100 internal PCM multisamples and 44 drum sounds. The complete **MULTISOUND LIST** (all 100 names) and **DRUM SOUND LIST** (all 44 names) are printed on the back page of `docs/M1_E4.pdf`. Use these to build the `Chooser` in Phase 2. A placeholder numeric chooser (0–99 Internal) is acceptable for Phase 1.
- **Effect parameter complexity** — 33 effect types with varying 8-byte parameter layouts mean the FX tab needs conditional UI: hiding/showing parameters based on selected effect type. This is the most complex UI piece.
- **Memory protect** — the M1 can be memory-protected in Global mode; write requests will return a Write Error (`22H`). The editor should detect this and warn the user.
- **Card bank** — writing to Card requires a RAM card inserted with write-protect off. Treat Card bank writes as best-effort with error handling.

---

## Validated SysEx Findings (from analysis of factory dump files)

### Source Files

| File | Format | Contents |
|---|---|---|
| `docs/M1_midi_export.syx` | MIDI SysEx | Single `50H` ALL DATA dump — entire M1 state (100 programs + 100 combis + global + sequencer) packed as one block |
| `docs/M1_plugin_export.m1all` | Korg plugin proprietary (`m1a ` header) | Raw unpacked parameter data — **not** standard SysEx, cannot be used directly |
| `docs/KorgM1_piano16.syx` | MIDI SysEx | Extracted single-program `40H` dump of factory patch "Piano 16'" — ready to use as `KorgM1.init` |

### ALL DATA (50H) Structure — Confirmed

The `M1_midi_export.syx` file is function `50H`, **not** 100 individual `40H` dumps. The entire payload is **one continuous packed block**:

```
F0 42 30 19 50 mm ss ss [single packed block] F7
```

- Bytes `[5]`: `00h` = memory allocation (100 prog / 100 combi mode)
- Bytes `[6–7]`: `23h 1Ch` = sequence note count = (0x23 << 7) | 0x1C = **4508 notes**
- Payload: 49782 bytes packed → **43559 bytes unpacked**

Unpacked section offsets (for 100-program allocation):

| Section | Raw size | Start offset (unpacked) |
|---|---|---|
| Global | 861 bytes | 0 |
| All Combinations | 12400 bytes (100 × 124) | 861 |
| All Programs | 14300 bytes (100 × 143) | **13261** |
| Sequence | remainder | 27561 |

### Pack / Unpack — Verified Working

The Python implementation below was tested and round-trips losslessly against all 100 factory patches:

```python
def unpack(midi_bytes):
    out = bytearray()
    i = 0
    while i + 7 < len(midi_bytes):
        msbs = midi_bytes[i]
        for x in range(7):
            out.append((midi_bytes[i + 1 + x] & 0x7F) | (((msbs >> x) & 1) << 7))
        i += 8
    if i < len(midi_bytes):          # handle final partial group
        msbs = midi_bytes[i]
        for x in range(min(7, len(midi_bytes) - i - 1)):
            out.append((midi_bytes[i + 1 + x] & 0x7F) | (((msbs >> x) & 1) << 7))
    return bytes(out)

def pack(raw_bytes):
    out = bytearray()
    for i in range(0, len(raw_bytes), 7):
        group = raw_bytes[i:i+7]
        msbs = 0
        data_bytes = bytearray(7)
        for x, b in enumerate(group):
            msbs |= ((b >> 7) & 1) << x
            data_bytes[x] = b & 0x7F
        out.append(msbs)
        out.extend(data_bytes[:len(group)])
    return bytes(out)
```

The Java equivalents are already in the plan above.

### Confirmed: Single Program Dump is 170 bytes

Extracted "Piano 16'" (program 1), repacked as a `40H` message and verified:
- Raw params: 143 bytes
- Packed: 164 bytes (143 bytes → 20 full groups of 7 + 3 remainder → 20×8 + 4 = 164)
- Total SysEx: `F0 42 30 19 40` (5) + 164 + `F7` (1) = **170 bytes** ✓

### All 100 Factory Program Names (Internal Bank)

Extracted from `M1_midi_export.syx`, programs 0–99:

```
[00] Universe    [01] Piano 16'   [02] Brass 1     [03] Ooh/Ahh
[04] Guitar 1    [05] BottleBell  [06] Fretless     [07] Symphonic
[08] Pan Flute   [09] Drums #1    [10] PanMallet    [11] E.Piano 1
[12] Trumpet     [13] Nimbus      [14] DistGuitar   [15] Vibes
[16] Pick Bass   [17] Organ 2     [18] Flute        [19] Pole
[20] Dream Pad   [21] MagicPiano  [22] Solo Sax     [23] Choir
[24] 12-String   [25] Kalimba     [26] A.Bass       [27] Strings
[28] SynMallet   [29] Drums #2    [30] Lore         [31] Harpsicord
[32] DoubleReed  [33] Bottles     [34] Koto Trem    [35] Bell Ring
[36] SynthBass1  [37] Timp&Bells  [38] Solo Synth   [39] Pop
[40] Magician    [41] Piano 8'    [42] Overture     [43] Angels
[44] Sitar 1     [45] Tubular     [46] Slap Bass    [47] Pipe Organ
[48] Wire        [49] Drum #3     [50] Bambu Trem   [51] E.Piano 4
[52] TubaFlugel  [53] Voice Wave  [54] Guitar 2     [55] Metal Hit
[56] SynthBass2  [57] StringRise  [58] Pan Wave     [59] Hammer
[60] Cloud Nine  [61] Clav        [62] Tenor Sax    [63] Voices
[64] RockGuitar  [65] WindBells   [66] SynthBass3   [67] Organ 1
[68] Block       [69] FingerSnap  [70] MagicOrgan   [71] E.Piano 2
[72] Brass 2     [73] FV Wave     [74] PickGuitar   [75] Digi-Bells
[76] AnalogBass  [77] Ping Wave   [78] Vibe Hit     [79] Pluck
[80] Good & Bad  [81] Digital 2   [82] Mute Trp.    [83] Stratos
[84] Sitar 2     [85] Flexatone   [86] Digital 4    [87] Soft Horns
[88] HellsBells  [89] Drop        [90] Zephyr       [91] E.Piano 3
[92] SynthBrass  [93] Digital 5   [94] E.Guitar 1   [95] Rhythm
[96] Mono Synth  [97] Hold......  [98] Wait......   [99] Surprise!!
```

### Init Patch

`docs/KorgM1_piano16.syx` contains factory program 1 ("Piano 16'") as a valid standalone `40H` SysEx message (170 bytes). Copy to `edisyn/synth/korgm1/KorgM1.init` when creating the editor directory.

Key parameters of "Piano 16'" (useful for verifying `parse()` output):

| Param # | Key | Value |
|---|---|---|
| 10 | `oscmode` | 0 (Single) |
| 12 | `osc1multisound` | 0 (Internal, first PCM sample) |
| 13 | `osc1octave` | 0xFF (= -1, i.e., 16' octave) |
| 38 | `effect1type` | 9 |
| 71 | `vdf1cutoff` | 40 (0x28) |
| 86 | `vda1level` | 79 (0x4F) |
