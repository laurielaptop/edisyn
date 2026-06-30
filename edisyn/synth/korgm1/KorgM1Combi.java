/**
   Copyright 2024 by Sean Luke
   Licensed under the Apache License version 2.0
*/

package edisyn.synth.korgm1;

import edisyn.*;
import edisyn.gui.*;
import java.awt.*;
import java.awt.geom.*;
import javax.swing.border.*;
import javax.swing.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import javax.sound.midi.*;

/**
   A patch editor for the Korg M1 Combination mode.

   Physical byte layout (124 raw bytes, TABLE 2):
     00-09  Combination Name (10 ASCII)
     10     Combination Type (0=Single 1=Layer 2=Split 3=Vel.SW 4=Multi)
     11     Effect 1 Type
     12     Effect 2 Type
     13     Effect 1/2 Balance L
     14     Effect 1/2 Balance R
     15     Effect 2 Level L
     16     Effect 2 Level R
     17     Output 3 Pan
     18     Output 4 Pan
     19     Effect I/O Routing
     20-27  Effect 1 Parameters (8 bytes)
     28-35  Effect 2 Parameters (8 bytes)
     36-46  Timbre 1 (11 bytes)
     47-57  Timbre 2 (11 bytes)
     ...
     113-123 Timbre 8 (11 bytes)

   Per-timbre byte layout (base = 36 + (n-1)*11, n=1..8):
     +0   Program No (0-99=Internal, 100-199=Card)
     +1   Output Level (0-99)
     +2   Key Transpose (signed, -12..+12)
     +3   Detune (signed, -50..+50)
     +4   Pan/Inst packed: bit7=Inst(0=Tim,1=Ins), bit3~0=Pan(0-13)
     +5   Key Window Top (0-127)
     +6   Key Window Bottom (0-127)
     +7   Vel Window Top (1-127)
     +8   Vel Window Bottom (1-127)
     +9   Control/On packed: bit4=TimbreOff(0=On,1=Off), bit3=CC, bit2=AT, bit1=Damper, bit0=ProgCh
     +10  MIDI Channel (bit3~0, 0-15)
*/

public class KorgM1Combi extends Synth
    {
    public static final int MAXIMUM_NAME_LENGTH = 10;
    public static final String[] BANKS = { "Internal", "Card" };

    // Set to true after a successful 49H parse so the immediately-following
    // spurious 24H (caused by MIDI THRU echoing our dump back to the M1) is ignored.
    private volatile boolean suppressNextDataLoadError = false;

    public static final String[] COMBI_TYPES =
        { "Single", "Layer", "Split", "Vel. Switch", "Multi" };

    public static final String[] EFFECT_TYPES = {
        "No Effect",
        "Hall", "Ensemble Hall", "Concert Hall",
        "Room", "Large Room", "Live Stage",
        "Early Ref 1", "Early Ref 2", "Early Ref 3",
        "Stereo Delay", "Cross Delay",
        "Stereo Chorus 1", "Stereo Chorus 2",
        "Stereo Flanger", "Cross Flanger",
        "Phaser 1", "Phaser 2",
        "Stereo Tremolo 1", "Stereo Tremolo 2",
        "Equalizer",
        "Overdrive", "Distortion",
        "Exciter",
        "Symphonic Ensemble", "Rotary Speaker",
        "Delay/Hall", "Delay/Room", "Delay/Early Ref",
        "Delay/Delay", "Delay/Chorus", "Delay/Flanger",
        "Delay/Phaser", "Delay/Tremolo"
        };

    public static final String[] MIDI_CHANNELS =
        { "1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16" };

    public static final String[] PROGRAMS;
    static
        {
        PROGRAMS = new String[200];
        for (int i = 0; i < 100; i++)
            PROGRAMS[i] = "I" + String.format("%02d", i);
        for (int i = 0; i < 100; i++)
            PROGRAMS[100 + i] = "C" + String.format("%02d", i);
        }


    public KorgM1Combi()
        {
        // --- Global tab ---
        JComponent globalPanel = new SynthPanel(this);
        VBox vbox = new VBox();
        vbox.add(addNameGlobal(Style.COLOR_GLOBAL()));
        globalPanel.add(vbox, BorderLayout.CENTER);
        addTab("Global", globalPanel);

        // --- FX tab ---
        JComponent fxPanel = new SynthPanel(this);
        vbox = new VBox();
        vbox.add(addFX(1, Style.COLOR_A()));
        vbox.add(addFX(2, Style.COLOR_B()));
        vbox.add(addFXPlacement(Style.COLOR_C()));
        fxPanel.add(vbox, BorderLayout.CENTER);
        addTab("FX", fxPanel);

        // --- Timbres 1-4 tab ---
        JComponent t14Panel = new SynthPanel(this);
        HBox hbox = new HBox();
        hbox.add(addTimbre(1, Style.COLOR_A()));
        hbox.add(addTimbre(2, Style.COLOR_B()));
        hbox.add(addTimbre(3, Style.COLOR_C()));
        hbox.addLast(addTimbre(4, Style.COLOR_A()));
        t14Panel.add(hbox, BorderLayout.CENTER);
        addTab("Timbres 1-4", t14Panel);

        // --- Timbres 5-8 tab ---
        JComponent t58Panel = new SynthPanel(this);
        hbox = new HBox();
        hbox.add(addTimbre(5, Style.COLOR_B()));
        hbox.add(addTimbre(6, Style.COLOR_C()));
        hbox.add(addTimbre(7, Style.COLOR_A()));
        hbox.addLast(addTimbre(8, Style.COLOR_B()));
        t58Panel.add(hbox, BorderLayout.CENTER);
        addTab("Timbres 5-8", t58Panel);

        model.set("name", "Init      ");
        model.set("bank", 0);
        model.set("number", 0);
        loadDefaults();

        // Location metadata
        model.setMin("bank", 0);            model.setMax("bank", 1);
        model.setMin("number", 0);          model.setMax("number", 99);

        // Combination type
        model.setMin("combitype", 0);       model.setMax("combitype", 4);

        // Effects
        model.setMin("effect1type", 0);         model.setMax("effect1type", 33);
        model.setMin("effect2type", 0);         model.setMax("effect2type", 33);
        model.setMin("effect12balleft", 0);     model.setMax("effect12balleft", 100);
        model.setMin("effect12balright", 0);    model.setMax("effect12balright", 100);
        model.setMin("effect2levelleft", 0);    model.setMax("effect2levelleft", 100);
        model.setMin("effect2levelright", 0);   model.setMax("effect2levelright", 100);
        model.setMin("output3pan", 0);          model.setMax("output3pan", 101);
        model.setMin("output4pan", 0);          model.setMax("output4pan", 101);
        model.setMin("effectrouting", 0);       model.setMax("effectrouting", 127);
        for (int i = 1; i <= 8; i++)
            {
            model.setMin("effect1p" + i, 0);    model.setMax("effect1p" + i, 255);
            model.setMin("effect2p" + i, 0);    model.setMax("effect2p" + i, 255);
            }

        // Per-timbre parameters
        for (int t = 1; t <= 8; t++)
            {
            String p = "t" + t;
            model.setMin(p + "progno", 0);          model.setMax(p + "progno", 199);
            model.setMin(p + "level", 0);            model.setMax(p + "level", 99);
            model.setMin(p + "transpose", -12);      model.setMax(p + "transpose", 12);
            model.setMin(p + "detune", -50);         model.setMax(p + "detune", 50);
            model.setMin(p + "inst", 0);             model.setMax(p + "inst", 1);
            model.setMin(p + "pan", 0);              model.setMax(p + "pan", 13);
            model.setMin(p + "keywintop", 0);        model.setMax(p + "keywintop", 127);
            model.setMin(p + "keywinbtm", 0);        model.setMax(p + "keywinbtm", 127);
            model.setMin(p + "velwintop", 1);        model.setMax(p + "velwintop", 127);
            model.setMin(p + "velwinbtm", 1);        model.setMax(p + "velwinbtm", 127);
            model.setMin(p + "progchgen", 0);        model.setMax(p + "progchgen", 1);
            model.setMin(p + "damperen", 0);         model.setMax(p + "damperen", 1);
            model.setMin(p + "aten", 0);             model.setMax(p + "aten", 1);
            model.setMin(p + "ccen", 0);             model.setMax(p + "ccen", 1);
            model.setMin(p + "on", 0);               model.setMax(p + "on", 1);
            model.setMin(p + "midichan", 0);         model.setMax(p + "midichan", 15);
            }
        }


    // =========================================================================
    // UI helper methods
    // =========================================================================

    JComponent addNameGlobal(Color color)
        {
        Category category = new Category(this, getSynthName(), color);
        HBox hbox = new HBox();

        VBox vbox = new VBox();
        HBox hbox2 = new HBox();
        hbox2.add(new PatchDisplay(this, 9));
        vbox.add(hbox2);
        JComponent comp = new StringComponent("Patch Name", this, "name", 10,
            "Name must be up to 10 ASCII characters.")
            {
            public String replace(String val) { return revisePatchName(val); }
            public void update(String key, Model model)
                {
                super.update(key, model);
                updateTitle();
                }
            };
        vbox.addBottom(comp);
        hbox.add(vbox);

        vbox = new VBox();
        comp = new Chooser("Combination Type", this, "combitype", COMBI_TYPES);
        vbox.add(comp);
        hbox.add(vbox);

        category.add(hbox, BorderLayout.WEST);
        return category;
        }

    JComponent addFX(int n, Color color)
        {
        Category category = new Category(this, "Effect " + n, color);
        HBox hbox = new HBox();
        String p = "effect" + n;

        VBox vbox = new VBox();
        JComponent comp = new Chooser("Type", this, p + "type", EFFECT_TYPES);
        vbox.add(comp);
        hbox.add(vbox);

        for (int i = 1; i <= 8; i++)
            {
            comp = new LabelledDial("P" + i, this, p + "p" + i, color, 0, 255);
            hbox.add(comp);
            }

        category.add(hbox, BorderLayout.CENTER);
        return category;
        }

    JComponent addFXPlacement(Color color)
        {
        Category category = new Category(this, "Effect Placement", color);
        HBox hbox = new HBox();

        JComponent comp;
        comp = new LabelledDial("1/2 Bal L", this, "effect12balleft", color, 0, 100);
        hbox.add(comp);
        comp = new LabelledDial("1/2 Bal R", this, "effect12balright", color, 0, 100);
        hbox.add(comp);
        comp = new LabelledDial("Eff2 Lvl L", this, "effect2levelleft", color, 0, 100);
        hbox.add(comp);
        comp = new LabelledDial("Eff2 Lvl R", this, "effect2levelright", color, 0, 100);
        hbox.add(comp);
        comp = new LabelledDial("Out 3 Pan", this, "output3pan", color, 0, 101);
        hbox.add(comp);
        comp = new LabelledDial("Out 4 Pan", this, "output4pan", color, 0, 101);
        hbox.add(comp);
        comp = new LabelledDial("Routing", this, "effectrouting", color, 0, 127);
        hbox.add(comp);

        category.add(hbox, BorderLayout.CENTER);
        return category;
        }

    JComponent addTimbre(int n, Color color)
        {
        String p = "t" + n;
        Category category = new Category(this, "Timbre " + n, color);
        VBox outer = new VBox();

        // Row 1: Program chooser + On/Off checkbox
        HBox hbox = new HBox();
        VBox vbox = new VBox();
        JComponent comp = new Chooser("Program", this, p + "progno", PROGRAMS);
        vbox.add(comp);
        comp = new CheckBox("On", this, p + "on");
        vbox.addBottom(comp);
        hbox.add(vbox);
        outer.add(hbox);

        // Row 2: Level, Transpose, Detune
        hbox = new HBox();
        comp = new LabelledDial("Level", this, p + "level", color, 0, 99);
        hbox.add(comp);
        comp = new LabelledDial("Transpose", this, p + "transpose", color, -12, 12);
        hbox.add(comp);
        comp = new LabelledDial("Detune", this, p + "detune", color, -50, 50);
        hbox.add(comp);
        outer.add(hbox);

        // Row 3: Pan, MIDI Channel, Inst checkbox
        hbox = new HBox();
        comp = new LabelledDial("Pan", this, p + "pan", color, 0, 13);
        hbox.add(comp);
        vbox = new VBox();
        comp = new Chooser("MIDI Ch", this, p + "midichan", MIDI_CHANNELS);
        vbox.add(comp);
        comp = new CheckBox("Ins Mode", this, p + "inst");
        vbox.addBottom(comp);
        hbox.add(vbox);
        outer.add(hbox);

        // Row 4: Key Window
        hbox = new HBox();
        comp = new LabelledDial("Key Win", this, p + "keywintop", color, 0, 127);
        ((LabelledDial)comp).addAdditionalLabel("Top");
        hbox.add(comp);
        comp = new LabelledDial("Key Win", this, p + "keywinbtm", color, 0, 127);
        ((LabelledDial)comp).addAdditionalLabel("Bottom");
        hbox.add(comp);
        outer.add(hbox);

        // Row 5: Vel Window
        hbox = new HBox();
        comp = new LabelledDial("Vel Win", this, p + "velwintop", color, 1, 127);
        ((LabelledDial)comp).addAdditionalLabel("Top");
        hbox.add(comp);
        comp = new LabelledDial("Vel Win", this, p + "velwinbtm", color, 1, 127);
        ((LabelledDial)comp).addAdditionalLabel("Bottom");
        hbox.add(comp);
        outer.add(hbox);

        // Row 6: Control filter checkboxes
        hbox = new HBox();
        vbox = new VBox();
        comp = new CheckBox("Prog Chg", this, p + "progchgen");
        vbox.add(comp);
        comp = new CheckBox("Damper", this, p + "damperen");
        vbox.add(comp);
        outer.add(hbox);
        hbox.add(vbox);
        vbox = new VBox();
        comp = new CheckBox("After Touch", this, p + "aten");
        vbox.add(comp);
        comp = new CheckBox("Ctrl Chg", this, p + "ccen");
        vbox.add(comp);
        hbox.addLast(vbox);

        category.add(outer, BorderLayout.CENTER);
        return category;
        }


    // =========================================================================
    // Identity
    // =========================================================================

    public static String getSynthName() { return "Korg M1 [Combi]"; }
    public String getDefaultResourceFileName() { return "KorgM1Combi.init"; }
    public String getHTMLResourceFileName() { return "KorgM1Combi.html"; }


    // =========================================================================
    // Patch name
    // =========================================================================

    public String getPatchName(Model model) { return model.get("name", "Init      "); }

    public String revisePatchName(String name)
        {
        name = super.revisePatchName(name);
        if (name.length() > MAXIMUM_NAME_LENGTH)
            name = name.substring(0, MAXIMUM_NAME_LENGTH);
        StringBuffer sb = new StringBuffer(name);
        for (int i = 0; i < sb.length(); i++)
            {
            char c = sb.charAt(i);
            if (c < 0x20 || c > 0x7E) sb.setCharAt(i, ' ');
            }
        return super.revisePatchName(sb.toString());
        }

    public void revise()
        {
        super.revise();
        String nm = model.get("name", "Init      ");
        String newnm = revisePatchName(nm);
        if (!nm.equals(newnm)) model.set("name", newnm);
        }


    // =========================================================================
    // Patch location
    // =========================================================================

    public String getPatchLocationName(Model model)
        {
        if (!model.exists("number")) return null;
        if (!model.exists("bank")) return null;
        return (model.get("bank") == 0 ? "I" : "C") +
               String.format("%02d", model.get("number"));
        }

    public Model getNextPatchLocation(Model model)
        {
        int bank = model.get("bank");
        int number = model.get("number") + 1;
        if (number >= 100) { bank++; number = 0; }
        if (bank >= 2) bank = 0;
        Model m = buildModel();
        m.set("bank", bank);
        m.set("number", number);
        return m;
        }

    public boolean gatherPatchInfo(String title, Model change, boolean writing)
        {
        JComboBox bank = new JComboBox(BANKS);
        bank.setSelectedIndex(model.get("bank"));
        JTextField number = new SelectedTextField(
            String.format("%02d", model.get("number")), 3);

        while (true)
            {
            boolean result = showMultiOption(this,
                new String[]{ "Bank", "Combi Number" },
                new JComponent[]{ bank, number },
                title, "Enter combination bank and number (00-99).");
            if (!result) return false;
            int n;
            try { n = Integer.parseInt(number.getText().trim()); }
            catch (NumberFormatException e) { continue; }
            if (n < 0 || n > 99) continue;
            change.set("bank", bank.getSelectedIndex());
            change.set("number", n);
            return true;
            }
        }


    // =========================================================================
    // MIDI I/O
    // =========================================================================

    public void changePatch(Model tempModel)
        {
        // Program Change in Combi mode selects Combination number.
        // Bank offset: Internal=0, Card=100 (treated as program change 0-199).
        int pc = tempModel.get("bank") * 100 + tempModel.get("number");
        try
            {
            tryToSendMIDI(new ShortMessage(ShortMessage.PROGRAM_CHANGE,
                getChannelOut(), pc & 0x7F, 0));
            }
        catch (Exception e) { Synth.handleException(e); }
        }

    public int getPauseAfterChangePatch() { return 50; }

    public boolean getAlwaysChangesPatchesOnRequestDump() { return true; }

    public boolean testVerify(Synth synth2, String key, Object obj1, Object obj2)
        {
        return key.equals("bank") || key.equals("number");
        }

    /** Request current Combination Parameter Dump (func 19H). */
    public byte[] requestCurrentDump()
        {
        suppressNextDataLoadError = true;   // arm before any response can arrive
        return new byte[] {
            (byte)0xF0, (byte)0x42,
            (byte)(0x30 | Math.max(0, getChannelOut() - 1)),
            (byte)0x19, (byte)0x19, (byte)0xF7
            };
        }

    public void parseParameter(byte[] data)
        {
        if (data.length < 6) return;
        if (data[0] != (byte)0xF0 || data[1] != (byte)0x42 ||
            (data[2] & 0xF0) != 0x30 || data[3] != (byte)0x19) return;
        byte func = data[4];
        if (func == (byte)0x22)
            showSimpleError("Write Error",
                "Write failed. The M1 may be memory-protected or the card is not inserted.");
        else if (func == (byte)0x24)
            {
            if (suppressNextDataLoadError)
                { suppressNextDataLoadError = false; }   // spurious echo after successful parse
            else
                showSimpleError("Data Load Error",
                    "The M1 could not process the request.  Make sure Memory Protect (MEM PROT) is OFF in Global mode, and that the M1 is in Combination mode.");
            }
        }

    public Object[] emitAll(Model tempModel, boolean toWorkingMemory, boolean toFile)
        {
        if (tempModel == null) tempModel = getModel();

        byte[] patch = emit(tempModel, toWorkingMemory, toFile);

        if (toWorkingMemory || toFile)
            {
            if (!toFile) suppressNextDataLoadError = true;  // suppress THRU-echo 24H
            return new Object[] { patch };
            }

        // Write combination to specific slot (func 1AH).
        suppressNextDataLoadError = true;  // suppress THRU-echo 24H
        int ch = Math.max(0, getChannelOut() - 1);
        byte[] writeCmd = new byte[] {
            (byte)0xF0, (byte)0x42,
            (byte)(0x30 | ch),
            (byte)0x19, (byte)0x1A,
            (byte)(tempModel.get("bank") & 0x0F),
            (byte)(tempModel.get("number") & 0x7F),
            (byte)0xF7
            };
        return new Object[] { patch, writeCmd };
        }

    public int parse(byte[] data, boolean fromFile)
        {
        // data[5..data.length-2] is the packed payload (142 bytes → 124 raw)
        byte[] raw = KorgM1Rec.convertTo8Bit(data, 5);

        // Name (00-09)
        char[] name = new char[10];
        for (int i = 0; i < 10; i++)
            {
            char c = (char)(raw[i] & 0xFF);
            if (c < 0x20 || c > 0x7E) c = ' ';
            name[i] = c;
            }
        model.set("name", new String(name));

        // Combination type (10)
        model.set("combitype", raw[10] & 0x07);

        // Effects (11-35)
        model.set("effect1type",       raw[11] & 0xFF);
        model.set("effect2type",       raw[12] & 0xFF);
        model.set("effect12balleft",   raw[13] & 0xFF);
        model.set("effect12balright",  raw[14] & 0xFF);
        model.set("effect2levelleft",  raw[15] & 0xFF);
        model.set("effect2levelright", raw[16] & 0xFF);
        model.set("output3pan",        raw[17] & 0xFF);
        model.set("output4pan",        raw[18] & 0xFF);
        model.set("effectrouting",     raw[19] & 0xFF);
        for (int i = 1; i <= 8; i++)
            {
            model.set("effect1p" + i, raw[20 + i - 1] & 0xFF);
            model.set("effect2p" + i, raw[28 + i - 1] & 0xFF);
            }

        // Timbres 1-8
        for (int t = 0; t < 8; t++)
            {
            int base = 36 + t * 11;
            String p = "t" + (t + 1);

            model.set(p + "progno",    raw[base]     & 0xFF);
            model.set(p + "level",     raw[base + 1] & 0xFF);
            model.set(p + "transpose", (int)(byte)raw[base + 2]);
            model.set(p + "detune",    (int)(byte)raw[base + 3]);

            int panByte = raw[base + 4] & 0xFF;
            model.set(p + "inst", (panByte >> 7) & 1);
            model.set(p + "pan",   panByte & 0x0F);

            model.set(p + "keywintop", raw[base + 5] & 0xFF);
            model.set(p + "keywinbtm", raw[base + 6] & 0xFF);
            model.set(p + "velwintop", raw[base + 7] & 0xFF);
            model.set(p + "velwinbtm", raw[base + 8] & 0xFF);

            int ctrlByte = raw[base + 9] & 0xFF;
            // bit4=0 means ON, bit4=1 means OFF; store as 1=ON 0=OFF
            model.set(p + "on",        1 - ((ctrlByte >> 4) & 1));
            model.set(p + "progchgen", ctrlByte & 1);
            model.set(p + "damperen", (ctrlByte >> 1) & 1);
            model.set(p + "aten",     (ctrlByte >> 2) & 1);
            model.set(p + "ccen",     (ctrlByte >> 3) & 1);

            model.set(p + "midichan", raw[base + 10] & 0x0F);
            }

        revise();
        return PARSE_SUCCEEDED;
        }

    public byte[] emit(Model tempModel, boolean toWorkingMemory, boolean toFile)
        {
        if (tempModel == null) tempModel = getModel();

        byte[] raw = new byte[124];

        // Name (00-09)
        String name = tempModel.get("name", "Init      ");
        for (int i = 0; i < 10; i++)
            {
            char c = i < name.length() ? name.charAt(i) : ' ';
            if (c < 0x20 || c > 0x7E) c = ' ';
            raw[i] = (byte)c;
            }

        // Combination type (10)
        raw[10] = (byte)tempModel.get("combitype");

        // Effects (11-35)
        raw[11] = (byte)tempModel.get("effect1type");
        raw[12] = (byte)tempModel.get("effect2type");
        raw[13] = (byte)tempModel.get("effect12balleft");
        raw[14] = (byte)tempModel.get("effect12balright");
        raw[15] = (byte)tempModel.get("effect2levelleft");
        raw[16] = (byte)tempModel.get("effect2levelright");
        raw[17] = (byte)tempModel.get("output3pan");
        raw[18] = (byte)tempModel.get("output4pan");
        raw[19] = (byte)tempModel.get("effectrouting");
        for (int i = 1; i <= 8; i++)
            {
            raw[20 + i - 1] = (byte)tempModel.get("effect1p" + i);
            raw[28 + i - 1] = (byte)tempModel.get("effect2p" + i);
            }

        // Timbres 1-8
        for (int t = 0; t < 8; t++)
            {
            int base = 36 + t * 11;
            String p = "t" + (t + 1);

            raw[base]     = (byte)tempModel.get(p + "progno");
            raw[base + 1] = (byte)tempModel.get(p + "level");
            raw[base + 2] = (byte)tempModel.get(p + "transpose");
            raw[base + 3] = (byte)tempModel.get(p + "detune");

            int inst = tempModel.get(p + "inst");
            int pan  = tempModel.get(p + "pan");
            raw[base + 4] = (byte)((inst << 7) | (pan & 0x0F));

            raw[base + 5] = (byte)tempModel.get(p + "keywintop");
            raw[base + 6] = (byte)tempModel.get(p + "keywinbtm");
            raw[base + 7] = (byte)tempModel.get(p + "velwintop");
            raw[base + 8] = (byte)tempModel.get(p + "velwinbtm");

            // bit4=0:ON bit4=1:OFF; model stores 1=ON 0=OFF so invert
            int on       = tempModel.get(p + "on");
            int progchg  = tempModel.get(p + "progchgen");
            int damper   = tempModel.get(p + "damperen");
            int at       = tempModel.get(p + "aten");
            int cc       = tempModel.get(p + "ccen");
            raw[base + 9] = (byte)(((1 - on) << 4) | (cc << 3) | (at << 2) | (damper << 1) | progchg);

            raw[base + 10] = (byte)(tempModel.get(p + "midichan") & 0x0F);
            }

        // Pack and wrap in SysEx header/footer (148 bytes total)
        byte[] packed = KorgM1Rec.convertTo7Bit(raw);
        byte[] result = new byte[6 + packed.length];
        result[0] = (byte)0xF0;
        result[1] = (byte)0x42;
        result[2] = (byte)(0x30 | Math.max(0, getChannelOut() - 1));
        result[3] = (byte)0x19;
        result[4] = (byte)0x49;
        System.arraycopy(packed, 0, result, 5, packed.length);
        result[5 + packed.length] = (byte)0xF7;
        return result;
        }


    // =========================================================================
    // Librarian support
    // =========================================================================

    public boolean getSupportsPatchWrites() { return true; }

    public String[] getBankNames() { return BANKS; }

    public boolean[] getWriteableBanks() { return new boolean[] { true, true }; }

    public String[] getPatchNumberNames()
        {
        String[] names = new String[100];
        for (int i = 0; i < 100; i++)
            names[i] = String.format("%02d", i);
        return names;
        }

    public int getPatchNameLength() { return MAXIMUM_NAME_LENGTH; }
    }
