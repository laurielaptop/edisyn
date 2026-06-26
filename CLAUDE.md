# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Active Work

- **Korg M1 editor** — Phases 1–5 complete (Program editor + Combination editor). See `docs/korg-m1-editor-plan.md` for the full plan.
  - **Phase 1 (done):** `KorgM1.java`, `KorgM1Rec.java`, `KorgM1.init`, `synths.txt` entry; SanityCheck passes clean
  - **Phase 2 (done):** Full editing UI — 7 tabs (Global, OSC, Pitch EG, VDF, VDA, Mod, FX), all 143 parameters
  - **Phase 3 (done):** MIDI integration — `emitAll()` with write command (11H), `parseParameter()` for error detection, `getAlwaysChangesPatchesOnRequestDump()`
  - **Phase 4 (done):** Librarian support — `getSupportsPatchWrites()`, `getBankNames()`, `getWriteableBanks()`, `getPatchNumberNames()`
  - **Phase 5 (done):** Combination editor — `KorgM1Combi.java`, `KorgM1CombiRec.java`, `KorgM1Combi.init`, `KorgM1Combi.html`; SanityCheck passes clean
  - **Hardware testing (in progress):** Program editor fully working. Combi editor: Request Current Patch confirmed working (spurious 24H from MIDI THRU echo suppressed by arming flag in `requestCurrentDump()`). Send/Write cycle for Combi still under test.
  - Reference files: `docs/M1_E4.pdf` (manual), `docs/KorgM1_piano16.syx` (init), `docs/M1_midi_export.syx` (factory bank)

## What is Edisyn

Edisyn is a pure-Java synthesizer patch editor and librarian. It runs on macOS, Linux, and Windows via Swing. It communicates with hardware synthesizers over MIDI/SysEx and provides patch exploration tools (randomization, hill-climbing, morphing, mutation).

## Build and Run

```sh
make all        # compile all .java files
make run        # run from class files (requires prior compile)
make jar        # build install/edisyn.jar (self-contained with coremidi4j)
make install    # build macOS .app via jpackage
make clean      # remove .class files and editor artifacts
```

The classpath is `libraries/coremidi4j-1.6.jar:edisyn` (or `.` when running from the jar).

## Testing

Run the automated sanity checker (fuzzing tester) from the project root after compiling:

```sh
# Test all synths
java -cp libraries/coremidi4j-1.6.jar:. edisyn.test.SanityCheck

# Test a specific synth editor class
java -cp libraries/coremidi4j-1.6.jar:. edisyn.test.SanityCheck -c edisyn.synth.yamahadx7.YamahaDX7

# Verbose output
java -cp libraries/coremidi4j-1.6.jar:. edisyn.test.SanityCheck -v

# Dump failing sysex
java -cp libraries/coremidi4j-1.6.jar:. edisyn.test.SanityCheck -d
```

`SanityCheck` randomizes each synth's parameters, emits them to a byte stream, parses them back into a second instance, and checks that both are identical. See `docs/testing/TESTING.md` for manual testing protocols.

## Architecture

### Core classes (`edisyn/`)

- **`Synth.java`** — abstract base class every synth editor extends. It owns the UI (tabs, menu bar), MIDI I/O, undo/redo, mutation, hill-climbing, morphing, and the librarian connection. Subclasses implement `parse()`, `emit()`, `recognize()`, `gatherPatchInfo()`, etc.
- **`Model.java`** — key/value store for patch parameters (string keys → integer or string values). Supports min/max ranges, mutation status (`FREE`/`IMMUTABLE`/`RESTRICTED`), listeners (`Updatable`), and undo integration.
- **`Midi.java`** — MIDI port management and SysEx I/O (uses CoreMIDI4J on macOS).
- **`Librarian.java`** / **`Library.java`** — patch library grid UI and underlying patch collection.
- **`Edisyn.java`** — application entry point; manages open windows and Mac-specific integration.

### Synth editors (`edisyn/synth/`)

Each synthesizer lives in its own subdirectory (e.g., `edisyn/synth/yamahadx7/`). Every editor consists of at least:
- `FooBar.java` — extends `Synth`; builds the UI, implements `parse()`/`emit()`, etc.
- `FooBarRec.java` — recognition class with `public static boolean recognize(byte[] data)` used to identify incoming SysEx.

To register an editor so Edisyn loads it, add a line to **`edisyn/synth/synths.txt`** in the format:
```
edisyn.synth.mysynth.MyClass<Tab>Manufacturer<Tab>Display Name
```
(Lines starting with `#` are disabled editors.)

**`Blank.java`** / **`BlankRec.java`** are the templates to copy when creating a new synth editor. `Blank.java` has extensive comments explaining every hook method.

### GUI toolkit (`edisyn/gui/`)

Custom Swing components: `LabelledDial`, `LabelledSlider`, `CheckBox`, `Chooser`, `EnvelopeDisplay`, `SynthPanel`, `VBox`/`HBox`, etc. All wire into `Model` via the `Updatable` interface.

### Utilities and extras

- `edisyn/util/` — `FFT`, `WavFile`, `WindowedSinc`, byte/int bag collections.
- `edisyn/nn/` — small neural network runtime used by the DX7 VAE patch explorer.

### Data files bundled in the jar

- `edisyn/synth/synths.txt` — synth registry
- `edisyn/gui/wordlist.txt` — random patch name word list
- `edisyn/Manufacturers.txt` — MIDI manufacturer ID table
- Per-synth: `*.init` (default patch), `*.html` (About panel), `*.png`/`*.jpg` (images), `*.txt.gz` / `n_*.txt` (patch name lists)
