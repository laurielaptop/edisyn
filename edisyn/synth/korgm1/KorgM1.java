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
   A patch editor for the Korg M1.
*/

public class KorgM1 extends Synth
    {
    public static final String[] BANKS = { "Internal", "Card" };
    public static final int MAXIMUM_NAME_LENGTH = 10;

    public static final String[] OSC_MODES = { "Single", "Double", "Drum" };
    public static final String[] POLY_MODES = { "Poly", "Mono" };
    public static final String[] MG_WAVES = { "Triangle", "Up Saw", "Down Saw", "Rectangle" };

    // M1 sends 0=Hall..32=Delay/Tremolo, 33=No Effect (0-indexed; Off at end)
    public static final String[] EFFECT_TYPES = {
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
        "Delay/Phaser", "Delay/Tremolo",
        "No Effect"
        };

    // Internal multisounds 00-99, Card placeholders 100-127
    public static final String[] MULTISOUNDS;
    static
        {
        String[] internal = {
            "00 Piano",       "01 E.Piano 1",  "02 E.Piano 2",  "03 Clav",
            "04 Harpsicord",  "05 Organ 1",    "06 Organ 2",    "07 MagicOrgan",
            "08 Guitar 1",    "09 Guitar 2",   "10 E.Guitar",   "11 Sitar 1",
            "12 Sitar 2",     "13 A.Bass",     "14 Pick Bass",  "15 E.Bass",
            "16 Fretless",    "17 SynthBass 1","18 SynthBass 2","19 Vibes",
            "20 Bell",        "21 Tubular",    "22 Bell Ring",  "23 Karimba",
            "24 KarimbaNT",   "25 SynMallet",  "26 Flute",      "27 Pan Flute",
            "28 Bottles",     "29 Voices",     "30 Choir",      "31 Strings",
            "32 Brass 1",     "33 Brass 2",    "34 Tenor Sax",  "35 Mute TP",
            "36 Trumpet",     "37 TubaFlugel", "38 DoubleReed", "39 Koto Trem",
            "40 BambooTrem",  "41 Rhythm",     "42 Lore",       "43 Lore NT",
            "44 Flexatone",   "45 WindBells",  "46 Pole",       "47 Pole NT",
            "48 Block",       "49 Block NT",   "50 FingerSnap", "51 Pop",
            "52 Drop",        "53 Drop NT",    "54 Breath",     "55 Breath NT",
            "56 Pluck",       "57 Pluck NT",   "58 Vibe Hit",   "59 VibeHit NT",
            "60 Hammer",      "61 Metal Hit",  "62 MetalHit NT","63 Pick",
            "64 Distortion",  "65 Dist NT",    "66 Bass Thumb", "67 BasThum NT1",
            "68 BasThum NT2", "69 Wire",       "70 Pan Wave",   "71 Ping Wave",
            "72 Fv Wave",     "73 Mv Wave",    "74 Voice Wave", "75 VoiceWvNT 1",
            "76 VoiceWvNT 2", "77 DWGS E.P.1", "78 DWGS E.P.2", "79 DWGS E.P.3",
            "80 DWGS Piano",  "81 DWGS Clav",  "82 DWGS Vibe 1","83 DWGS Bass 1",
            "84 DWGS Bass 2", "85 DWGS Bell 1","86 DWGS Orgn 1","87 DWGS Orgn 2",
            "88 DWGS Voice",  "89 SquareWave", "90 Digital 1",  "91 Saw Wave",
            "92 Digital 2",   "93 25% Pulse",  "94 10% Pulse",  "95 Digital 3",
            "96 Digital 4",   "97 Digital 5",  "98 DWGS TRI",   "99 DWGS Sine"
            };
        MULTISOUNDS = new String[128];
        for (int i = 0; i < 100; i++)
            MULTISOUNDS[i] = internal[i];
        for (int i = 100; i < 128; i++)
            MULTISOUNDS[i] = "Card " + String.format("%02d", i - 100);
        }


    public KorgM1()
        {
        // --- Global tab ---
        JComponent globalPanel = new SynthPanel(this);
        VBox vbox = new VBox();
        vbox.add(addNameGlobal(Style.COLOR_GLOBAL()));
        globalPanel.add(vbox, BorderLayout.CENTER);
        addTab("Global", globalPanel);

        // --- OSC tab ---
        JComponent oscPanel = new SynthPanel(this);
        vbox = new VBox();
        vbox.add(addOscCommon(Style.COLOR_A()));
        HBox hbox = new HBox();
        hbox.add(addOsc1(Style.COLOR_B()));
        hbox.addLast(addOsc2(Style.COLOR_C()));
        vbox.add(hbox);
        oscPanel.add(vbox, BorderLayout.CENTER);
        addTab("OSC", oscPanel);

        // --- Pitch EG tab ---
        JComponent pitchPanel = new SynthPanel(this);
        vbox = new VBox();
        vbox.add(addPitchEG(1, Style.COLOR_A()));
        vbox.add(addPitchEG(2, Style.COLOR_B()));
        pitchPanel.add(vbox, BorderLayout.CENTER);
        addTab("Pitch EG", pitchPanel);

        // --- VDF tab ---
        JComponent vdfPanel = new SynthPanel(this);
        vbox = new VBox();
        hbox = new HBox();
        hbox.add(addVDF(1, Style.COLOR_A()));
        hbox.addLast(addVDFEG(1, Style.COLOR_B()));
        vbox.add(hbox);
        hbox = new HBox();
        hbox.add(addVDF(2, Style.COLOR_C()));
        hbox.addLast(addVDFEG(2, Style.COLOR_A()));
        vbox.add(hbox);
        vdfPanel.add(vbox, BorderLayout.CENTER);
        addTab("VDF", vdfPanel);

        // --- VDA tab ---
        JComponent vdaPanel = new SynthPanel(this);
        vbox = new VBox();
        hbox = new HBox();
        hbox.add(addVDA(1, Style.COLOR_A()));
        hbox.addLast(addVDAEG(1, Style.COLOR_B()));
        vbox.add(hbox);
        hbox = new HBox();
        hbox.add(addVDA(2, Style.COLOR_C()));
        hbox.addLast(addVDAEG(2, Style.COLOR_A()));
        vbox.add(hbox);
        vdaPanel.add(vbox, BorderLayout.CENTER);
        addTab("VDA", vdaPanel);

        // --- Mod tab ---
        JComponent modPanel = new SynthPanel(this);
        vbox = new VBox();
        hbox = new HBox();
        hbox.add(addMG("pitchmg", "Pitch MG", Style.COLOR_A()));
        hbox.addLast(addMG("cutoffmg", "Cutoff MG", Style.COLOR_B()));
        vbox.add(hbox);
        hbox = new HBox();
        hbox.add(addAfterTouch(Style.COLOR_C()));
        hbox.addLast(addJoyStick(Style.COLOR_A()));
        vbox.add(hbox);
        vbox.add(addEGPolarity(Style.COLOR_B()));
        modPanel.add(vbox, BorderLayout.CENTER);
        addTab("Mod", modPanel);

        // --- FX tab ---
        JComponent fxPanel = new SynthPanel(this);
        vbox = new VBox();
        vbox.add(addFX(1, Style.COLOR_A()));
        vbox.add(addFX(2, Style.COLOR_B()));
        vbox.add(addFXPlacement(Style.COLOR_C()));
        fxPanel.add(vbox, BorderLayout.CENTER);
        addTab("FX", fxPanel);

        model.set("name", "Init      ");
        model.set("bank", 0);
        model.set("number", 0);
        loadDefaults();

        // Location metadata
        model.setMin("bank", 0);            model.setMax("bank", 1);
        model.setMin("number", 0);          model.setMax("number", 99);

        // Oscillator Common
        model.setMin("oscmode", 0);         model.setMax("oscmode", 2);
        model.setMin("polymode", 0);        model.setMax("polymode", 1);
        model.setMin("hold", 0);            model.setMax("hold", 1);
        model.setMin("osc1multisound", 0);  model.setMax("osc1multisound", 127);
        model.setMin("osc1octave", -1);     model.setMax("osc1octave", 1);
        model.setMin("osc2multisound", 0);  model.setMax("osc2multisound", 127);
        model.setMin("osc2octave", -1);     model.setMax("osc2octave", 1);
        model.setMin("interval", -12);      model.setMax("interval", 12);
        model.setMin("detune", -50);        model.setMax("detune", 50);
        model.setMin("delaystart", 0);      model.setMax("delaystart", 99);

        // Pitch MG
        model.setMin("pitchmgwave", 0);         model.setMax("pitchmgwave", 3);
        model.setMin("pitchmgosc1on", 0);       model.setMax("pitchmgosc1on", 1);
        model.setMin("pitchmgosc2on", 0);       model.setMax("pitchmgosc2on", 1);
        model.setMin("pitchmgkeysync", 0);      model.setMax("pitchmgkeysync", 1);
        model.setMin("pitchmgfreq", 0);         model.setMax("pitchmgfreq", 99);
        model.setMin("pitchmgdelay", 0);        model.setMax("pitchmgdelay", 99);
        model.setMin("pitchmgintensity", 0);    model.setMax("pitchmgintensity", 99);

        // Cutoff MG
        model.setMin("cutoffmgwave", 0);        model.setMax("cutoffmgwave", 3);
        model.setMin("cutoffmgosc1on", 0);      model.setMax("cutoffmgosc1on", 1);
        model.setMin("cutoffmgosc2on", 0);      model.setMax("cutoffmgosc2on", 1);
        model.setMin("cutoffmgkeysync", 0);     model.setMax("cutoffmgkeysync", 1);
        model.setMin("cutoffmgfreq", 0);        model.setMax("cutoffmgfreq", 99);
        model.setMin("cutoffmgdelay", 0);       model.setMax("cutoffmgdelay", 99);
        model.setMin("cutoffmgintensity", 0);   model.setMax("cutoffmgintensity", 99);

        // After Touch
        model.setMin("atpitch", -12);       model.setMax("atpitch", 12);
        model.setMin("atpitchmg", -12);     model.setMax("atpitchmg", 12);
        model.setMin("atvdfcutoff", -99);   model.setMax("atvdfcutoff", 99);
        model.setMin("atvdfmg", -99);       model.setMax("atvdfmg", 99);
        model.setMin("atvdaamp", -99);      model.setMax("atvdaamp", 99);

        // Joy Stick
        model.setMin("joypitchbend", -12);  model.setMax("joypitchbend", 12);
        model.setMin("joysweepint", -99);   model.setMax("joysweepint", 99);
        model.setMin("joypitchmgint", 0);   model.setMax("joypitchmgint", 99);
        model.setMin("joyvdfmgfreq1", 0);   model.setMax("joyvdfmgfreq1", 3);
        model.setMin("joyvdfmgint", 0);     model.setMax("joyvdfmgint", 99);
        model.setMin("joyvdfmgfreq2", 0);   model.setMax("joyvdfmgfreq2", 3);

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

        // OSC-1 Pitch EG
        model.setMin("pitcheg1startlevel", -99);    model.setMax("pitcheg1startlevel", 99);
        model.setMin("pitcheg1attacktime", 0);       model.setMax("pitcheg1attacktime", 99);
        model.setMin("pitcheg1attacklevel", -99);    model.setMax("pitcheg1attacklevel", 99);
        model.setMin("pitcheg1decaytime", 0);        model.setMax("pitcheg1decaytime", 99);
        model.setMin("pitcheg1releasetime", 0);      model.setMax("pitcheg1releasetime", 99);
        model.setMin("pitcheg1releaselevel", -99);   model.setMax("pitcheg1releaselevel", 99);
        model.setMin("pitcheg1timevelsense", -99);   model.setMax("pitcheg1timevelsense", 99);
        model.setMin("pitcheg1levelvelsense", -99);  model.setMax("pitcheg1levelvelsense", 99);

        // VDF-1
        model.setMin("vdf1cutoff", 0);              model.setMax("vdf1cutoff", 99);
        model.setMin("vdf1kbdtrackcenter", 0);      model.setMax("vdf1kbdtrackcenter", 127);
        model.setMin("vdf1cutoffkbdtrack", -99);    model.setMax("vdf1cutoffkbdtrack", 99);
        model.setMin("vdf1egintensity", 0);         model.setMax("vdf1egintensity", 99);
        model.setMin("vdf1egtimekbdtrack", 0);      model.setMax("vdf1egtimekbdtrack", 99);
        model.setMin("vdf1egintvelsense", -99);     model.setMax("vdf1egintvelsense", 99);
        model.setMin("vdf1cutoffvelsense", -99);    model.setMax("vdf1cutoffvelsense", 99);

        // VDF-1 EG
        model.setMin("vdfeg1attacktime", 0);        model.setMax("vdfeg1attacktime", 99);
        model.setMin("vdfeg1attacklevel", -99);     model.setMax("vdfeg1attacklevel", 99);
        model.setMin("vdfeg1decaytime", 0);         model.setMax("vdfeg1decaytime", 99);
        model.setMin("vdfeg1breakpoint", -99);      model.setMax("vdfeg1breakpoint", 99);
        model.setMin("vdfeg1slopetime", 0);         model.setMax("vdfeg1slopetime", 99);
        model.setMin("vdfeg1sustainlevel", -99);    model.setMax("vdfeg1sustainlevel", 99);
        model.setMin("vdfeg1releasetime", 0);       model.setMax("vdfeg1releasetime", 99);
        model.setMin("vdfeg1releaselevel", -99);    model.setMax("vdfeg1releaselevel", 99);

        // VDA-1
        model.setMin("vda1level", 0);               model.setMax("vda1level", 99);
        model.setMin("vda1kbdtrackcenter", 0);      model.setMax("vda1kbdtrackcenter", 127);
        model.setMin("vda1ampkbdtrack", -99);       model.setMax("vda1ampkbdtrack", 99);
        model.setMin("vda1ampvelsense", -99);       model.setMax("vda1ampvelsense", 99);
        model.setMin("vda1egtimekbdtrack", 0);      model.setMax("vda1egtimekbdtrack", 99);
        model.setMin("vda1egtimevelsense", 0);      model.setMax("vda1egtimevelsense", 99);

        // VDA-1 EG
        model.setMin("vdaeg1attacktime", 0);        model.setMax("vdaeg1attacktime", 99);
        model.setMin("vdaeg1attacklevel", -99);     model.setMax("vdaeg1attacklevel", 99);
        model.setMin("vdaeg1decaytime", 0);         model.setMax("vdaeg1decaytime", 99);
        model.setMin("vdaeg1breakpoint", -99);      model.setMax("vdaeg1breakpoint", 99);
        model.setMin("vdaeg1slopetime", 0);         model.setMax("vdaeg1slopetime", 99);
        model.setMin("vdaeg1sustainlevel", -99);    model.setMax("vdaeg1sustainlevel", 99);
        model.setMin("vdaeg1releasetime", 0);       model.setMax("vdaeg1releasetime", 99);

        // EG Polarity/SW bytes (full 8-bit bit fields)
        model.setMin("egpol1", 0);  model.setMax("egpol1", 255);
        model.setMin("egpol2", 0);  model.setMax("egpol2", 255);
        model.setMin("egpol3", 0);  model.setMax("egpol3", 255);
        model.setMin("egpol4", 0);  model.setMax("egpol4", 255);

        // OSC-2 Pitch EG
        model.setMin("pitcheg2startlevel", -99);    model.setMax("pitcheg2startlevel", 99);
        model.setMin("pitcheg2attacktime", 0);       model.setMax("pitcheg2attacktime", 99);
        model.setMin("pitcheg2attacklevel", -99);    model.setMax("pitcheg2attacklevel", 99);
        model.setMin("pitcheg2decaytime", 0);        model.setMax("pitcheg2decaytime", 99);
        model.setMin("pitcheg2releasetime", 0);      model.setMax("pitcheg2releasetime", 99);
        model.setMin("pitcheg2releaselevel", -99);   model.setMax("pitcheg2releaselevel", 99);
        model.setMin("pitcheg2timevelsense", -99);   model.setMax("pitcheg2timevelsense", 99);
        model.setMin("pitcheg2levelvelsense", -99);  model.setMax("pitcheg2levelvelsense", 99);

        // VDF-2
        model.setMin("vdf2cutoff", 0);              model.setMax("vdf2cutoff", 99);
        model.setMin("vdf2kbdtrackcenter", 0);      model.setMax("vdf2kbdtrackcenter", 127);
        model.setMin("vdf2cutoffkbdtrack", -99);    model.setMax("vdf2cutoffkbdtrack", 99);
        model.setMin("vdf2egintensity", 0);         model.setMax("vdf2egintensity", 99);
        model.setMin("vdf2egtimekbdtrack", 0);      model.setMax("vdf2egtimekbdtrack", 99);
        model.setMin("vdf2egintvelsense", -99);     model.setMax("vdf2egintvelsense", 99);
        model.setMin("vdf2cutoffvelsense", -99);    model.setMax("vdf2cutoffvelsense", 99);

        // VDF-2 EG
        model.setMin("vdfeg2attacktime", 0);        model.setMax("vdfeg2attacktime", 99);
        model.setMin("vdfeg2attacklevel", -99);     model.setMax("vdfeg2attacklevel", 99);
        model.setMin("vdfeg2decaytime", 0);         model.setMax("vdfeg2decaytime", 99);
        model.setMin("vdfeg2breakpoint", -99);      model.setMax("vdfeg2breakpoint", 99);
        model.setMin("vdfeg2slopetime", 0);         model.setMax("vdfeg2slopetime", 99);
        model.setMin("vdfeg2sustainlevel", -99);    model.setMax("vdfeg2sustainlevel", 99);
        model.setMin("vdfeg2releasetime", 0);       model.setMax("vdfeg2releasetime", 99);
        model.setMin("vdfeg2releaselevel", -99);    model.setMax("vdfeg2releaselevel", 99);

        // VDA-2
        model.setMin("vda2level", 0);               model.setMax("vda2level", 99);
        model.setMin("vda2kbdtrackcenter", 0);      model.setMax("vda2kbdtrackcenter", 127);
        model.setMin("vda2ampkbdtrack", -99);       model.setMax("vda2ampkbdtrack", 99);
        model.setMin("vda2ampvelsense", -99);       model.setMax("vda2ampvelsense", 99);
        model.setMin("vda2egtimekbdtrack", 0);      model.setMax("vda2egtimekbdtrack", 99);
        model.setMin("vda2egtimevelsense", 0);      model.setMax("vda2egtimevelsense", 99);

        // VDA-2 EG
        model.setMin("vdaeg2attacktime", 0);        model.setMax("vdaeg2attacktime", 99);
        model.setMin("vdaeg2attacklevel", -99);     model.setMax("vdaeg2attacklevel", 99);
        model.setMin("vdaeg2decaytime", 0);         model.setMax("vdaeg2decaytime", 99);
        model.setMin("vdaeg2breakpoint", -99);      model.setMax("vdaeg2breakpoint", 99);
        model.setMin("vdaeg2slopetime", 0);         model.setMax("vdaeg2slopetime", 99);
        model.setMin("vdaeg2sustainlevel", -99);    model.setMax("vdaeg2sustainlevel", 99);
        model.setMin("vdaeg2releasetime", 0);       model.setMax("vdaeg2releasetime", 99);

        // EG Polarity/SW bytes (OSC-2)
        model.setMin("egpol5", 0);  model.setMax("egpol5", 255);
        model.setMin("egpol6", 0);  model.setMax("egpol6", 255);
        model.setMin("egpol7", 0);  model.setMax("egpol7", 255);
        model.setMin("egpol8", 0);  model.setMax("egpol8", 255);
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
        JComponent comp = new StringComponent("Patch Name", this, "name", 10, "Name must be up to 10 ASCII characters.")
            {
            public String replace(String val)
                {
                return revisePatchName(val);
                }
            public void update(String key, Model model)
                {
                super.update(key, model);
                updateTitle();
                }
            };
        vbox.addBottom(comp);
        hbox.add(vbox);
        category.add(hbox, BorderLayout.WEST);
        return category;
        }

    JComponent addOscCommon(Color color)
        {
        Category category = new Category(this, "Oscillator Common", color);
        HBox hbox = new HBox();

        VBox vbox = new VBox();
        JComponent comp = new Chooser("Mode", this, "oscmode", OSC_MODES);
        vbox.add(comp);
        comp = new Chooser("Poly/Mono", this, "polymode", POLY_MODES);
        vbox.add(comp);
        comp = new CheckBox("Hold", this, "hold");
        vbox.add(comp);
        hbox.add(vbox);

        category.add(hbox, BorderLayout.CENTER);
        return category;
        }

    JComponent addOsc1(Color color)
        {
        Category category = new Category(this, "OSC 1", color);
        HBox hbox = new HBox();

        JComponent comp = new Chooser("Multisound", this, "osc1multisound", MULTISOUNDS);
        hbox.add(comp);
        comp = new LabelledDial("Octave", this, "osc1octave", color, -1, 1);
        hbox.add(comp);

        category.add(hbox, BorderLayout.CENTER);
        return category;
        }

    JComponent addOsc2(Color color)
        {
        Category category = new Category(this, "OSC 2", color);
        HBox hbox = new HBox();

        JComponent comp = new Chooser("Multisound", this, "osc2multisound", MULTISOUNDS);
        hbox.add(comp);
        comp = new LabelledDial("Octave", this, "osc2octave", color, -1, 1);
        hbox.add(comp);
        comp = new LabelledDial("Interval", this, "interval", color, -12, 12);
        hbox.add(comp);
        comp = new LabelledDial("Detune", this, "detune", color, -50, 50);
        hbox.add(comp);
        comp = new LabelledDial("Delay Start", this, "delaystart", color, 0, 99);
        hbox.add(comp);

        category.add(hbox, BorderLayout.CENTER);
        return category;
        }

    JComponent addPitchEG(int n, Color color)
        {
        Category category = new Category(this, "OSC " + n + " Pitch EG", color);
        HBox hbox = new HBox();
        String p = "pitcheg" + n;

        JComponent comp;
        comp = new LabelledDial("Start", this, p + "startlevel", color, -99, 99);
        hbox.add(comp);
        comp = new LabelledDial("Atk Time", this, p + "attacktime", color, 0, 99);
        hbox.add(comp);
        comp = new LabelledDial("Atk Level", this, p + "attacklevel", color, -99, 99);
        hbox.add(comp);
        comp = new LabelledDial("Dcy Time", this, p + "decaytime", color, 0, 99);
        hbox.add(comp);
        comp = new LabelledDial("Rel Time", this, p + "releasetime", color, 0, 99);
        hbox.add(comp);
        comp = new LabelledDial("Rel Level", this, p + "releaselevel", color, -99, 99);
        hbox.add(comp);
        comp = new LabelledDial("Time Vel", this, p + "timevelsense", color, -99, 99);
        ((LabelledDial)comp).addAdditionalLabel("Sense");
        hbox.add(comp);
        comp = new LabelledDial("Level Vel", this, p + "levelvelsense", color, -99, 99);
        ((LabelledDial)comp).addAdditionalLabel("Sense");
        hbox.add(comp);

        category.add(hbox, BorderLayout.CENTER);
        return category;
        }

    JComponent addVDF(int n, Color color)
        {
        Category category = new Category(this, "VDF " + n, color);
        HBox hbox = new HBox();
        String p = "vdf" + n;

        JComponent comp;
        comp = new LabelledDial("Cutoff", this, p + "cutoff", color, 0, 99);
        hbox.add(comp);
        comp = new LabelledDial("KBD Track", this, p + "kbdtrackcenter", color, 0, 127);
        ((LabelledDial)comp).addAdditionalLabel("Center");
        hbox.add(comp);
        comp = new LabelledDial("Cutoff KBD", this, p + "cutoffkbdtrack", color, -99, 99);
        ((LabelledDial)comp).addAdditionalLabel("Track");
        hbox.add(comp);
        comp = new LabelledDial("EG Int", this, p + "egintensity", color, 0, 99);
        hbox.add(comp);
        comp = new LabelledDial("EG Time", this, p + "egtimekbdtrack", color, 0, 99);
        ((LabelledDial)comp).addAdditionalLabel("KBD Track");
        hbox.add(comp);
        comp = new LabelledDial("EG Int Vel", this, p + "egintvelsense", color, -99, 99);
        ((LabelledDial)comp).addAdditionalLabel("Sense");
        hbox.add(comp);
        comp = new LabelledDial("Cutoff Vel", this, p + "cutoffvelsense", color, -99, 99);
        ((LabelledDial)comp).addAdditionalLabel("Sense");
        hbox.add(comp);

        category.add(hbox, BorderLayout.CENTER);
        return category;
        }

    JComponent addVDFEG(int n, Color color)
        {
        Category category = new Category(this, "VDF " + n + " EG", color);
        HBox hbox = new HBox();
        String p = "vdfeg" + n;

        JComponent comp;
        comp = new LabelledDial("Atk Time", this, p + "attacktime", color, 0, 99);
        hbox.add(comp);
        comp = new LabelledDial("Atk Level", this, p + "attacklevel", color, -99, 99);
        hbox.add(comp);
        comp = new LabelledDial("Dcy Time", this, p + "decaytime", color, 0, 99);
        hbox.add(comp);
        comp = new LabelledDial("Break", this, p + "breakpoint", color, -99, 99);
        ((LabelledDial)comp).addAdditionalLabel("Point");
        hbox.add(comp);
        comp = new LabelledDial("Slope", this, p + "slopetime", color, 0, 99);
        ((LabelledDial)comp).addAdditionalLabel("Time");
        hbox.add(comp);
        comp = new LabelledDial("Sustain", this, p + "sustainlevel", color, -99, 99);
        ((LabelledDial)comp).addAdditionalLabel("Level");
        hbox.add(comp);
        comp = new LabelledDial("Rel Time", this, p + "releasetime", color, 0, 99);
        hbox.add(comp);
        comp = new LabelledDial("Rel Level", this, p + "releaselevel", color, -99, 99);
        hbox.add(comp);

        category.add(hbox, BorderLayout.CENTER);
        return category;
        }

    JComponent addVDA(int n, Color color)
        {
        Category category = new Category(this, "VDA " + n, color);
        HBox hbox = new HBox();
        String p = "vda" + n;

        JComponent comp;
        comp = new LabelledDial("Level", this, p + "level", color, 0, 99);
        hbox.add(comp);
        comp = new LabelledDial("KBD Track", this, p + "kbdtrackcenter", color, 0, 127);
        ((LabelledDial)comp).addAdditionalLabel("Center");
        hbox.add(comp);
        comp = new LabelledDial("Amp KBD", this, p + "ampkbdtrack", color, -99, 99);
        ((LabelledDial)comp).addAdditionalLabel("Track");
        hbox.add(comp);
        comp = new LabelledDial("Amp Vel", this, p + "ampvelsense", color, -99, 99);
        ((LabelledDial)comp).addAdditionalLabel("Sense");
        hbox.add(comp);
        comp = new LabelledDial("EG Time", this, p + "egtimekbdtrack", color, 0, 99);
        ((LabelledDial)comp).addAdditionalLabel("KBD Track");
        hbox.add(comp);
        comp = new LabelledDial("EG Time Vel", this, p + "egtimevelsense", color, 0, 99);
        ((LabelledDial)comp).addAdditionalLabel("Sense");
        hbox.add(comp);

        category.add(hbox, BorderLayout.CENTER);
        return category;
        }

    JComponent addVDAEG(int n, Color color)
        {
        Category category = new Category(this, "VDA " + n + " EG", color);
        HBox hbox = new HBox();
        String p = "vdaeg" + n;

        JComponent comp;
        comp = new LabelledDial("Atk Time", this, p + "attacktime", color, 0, 99);
        hbox.add(comp);
        comp = new LabelledDial("Atk Level", this, p + "attacklevel", color, -99, 99);
        hbox.add(comp);
        comp = new LabelledDial("Dcy Time", this, p + "decaytime", color, 0, 99);
        hbox.add(comp);
        comp = new LabelledDial("Break", this, p + "breakpoint", color, -99, 99);
        ((LabelledDial)comp).addAdditionalLabel("Point");
        hbox.add(comp);
        comp = new LabelledDial("Slope", this, p + "slopetime", color, 0, 99);
        ((LabelledDial)comp).addAdditionalLabel("Time");
        hbox.add(comp);
        comp = new LabelledDial("Sustain", this, p + "sustainlevel", color, -99, 99);
        ((LabelledDial)comp).addAdditionalLabel("Level");
        hbox.add(comp);
        comp = new LabelledDial("Rel Time", this, p + "releasetime", color, 0, 99);
        hbox.add(comp);

        category.add(hbox, BorderLayout.CENTER);
        return category;
        }

    JComponent addMG(String prefix, String title, Color color)
        {
        Category category = new Category(this, title, color);
        HBox hbox = new HBox();

        VBox vbox = new VBox();
        JComponent comp = new Chooser("Wave", this, prefix + "wave", MG_WAVES);
        vbox.add(comp);
        comp = new CheckBox("OSC 1 On", this, prefix + "osc1on");
        vbox.add(comp);
        comp = new CheckBox("OSC 2 On", this, prefix + "osc2on");
        vbox.add(comp);
        comp = new CheckBox("Key Sync", this, prefix + "keysync");
        vbox.addBottom(comp);
        hbox.add(vbox);

        comp = new LabelledDial("Frequency", this, prefix + "freq", color, 0, 99);
        hbox.add(comp);
        comp = new LabelledDial("Delay", this, prefix + "delay", color, 0, 99);
        hbox.add(comp);
        comp = new LabelledDial("Intensity", this, prefix + "intensity", color, 0, 99);
        hbox.add(comp);

        category.add(hbox, BorderLayout.CENTER);
        return category;
        }

    JComponent addAfterTouch(Color color)
        {
        Category category = new Category(this, "After Touch", color);
        HBox hbox = new HBox();

        JComponent comp;
        comp = new LabelledDial("Pitch", this, "atpitch", color, -12, 12);
        hbox.add(comp);
        comp = new LabelledDial("Pitch MG", this, "atpitchmg", color, -12, 12);
        hbox.add(comp);
        comp = new LabelledDial("VDF Cutoff", this, "atvdfcutoff", color, -99, 99);
        hbox.add(comp);
        comp = new LabelledDial("VDF MG", this, "atvdfmg", color, -99, 99);
        hbox.add(comp);
        comp = new LabelledDial("VDA Amp", this, "atvdaamp", color, -99, 99);
        hbox.add(comp);

        category.add(hbox, BorderLayout.CENTER);
        return category;
        }

    JComponent addJoyStick(Color color)
        {
        Category category = new Category(this, "Joy Stick", color);
        HBox hbox = new HBox();

        JComponent comp;
        comp = new LabelledDial("Pitch Bend", this, "joypitchbend", color, -12, 12);
        hbox.add(comp);
        comp = new LabelledDial("Sweep Int", this, "joysweepint", color, -99, 99);
        hbox.add(comp);
        comp = new LabelledDial("Pitch MG", this, "joypitchmgint", color, 0, 99);
        ((LabelledDial)comp).addAdditionalLabel("Int");
        hbox.add(comp);
        comp = new LabelledDial("VDF MG", this, "joyvdfmgfreq1", color, 0, 3);
        ((LabelledDial)comp).addAdditionalLabel("Freq 1");
        hbox.add(comp);
        comp = new LabelledDial("VDF MG", this, "joyvdfmgint", color, 0, 99);
        ((LabelledDial)comp).addAdditionalLabel("Int");
        hbox.add(comp);
        comp = new LabelledDial("VDF MG", this, "joyvdfmgfreq2", color, 0, 3);
        ((LabelledDial)comp).addAdditionalLabel("Freq 2");
        hbox.add(comp);

        category.add(hbox, BorderLayout.CENTER);
        return category;
        }

    JComponent addEGPolarity(Color color)
        {
        Category category = new Category(this, "EG SW / Polarity", color);
        HBox hbox = new HBox();

        String[] labels = { "OSC1 Pitch", "VDF 1",     "VDA1 KBD", "VDA1 Vel",
                            "OSC2 Pitch", "VDF 2",     "VDA2 KBD", "VDA2 Vel" };
        String[] keys   = { "egpol1",     "egpol2",    "egpol3",   "egpol4",
                            "egpol5",     "egpol6",    "egpol7",   "egpol8" };
        for (int i = 0; i < 8; i++)
            {
            JComponent comp = new LabelledDial(labels[i], this, keys[i], color, 0, 255);
            hbox.add(comp);
            }

        category.add(hbox, BorderLayout.CENTER);
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


    // =========================================================================
    // Identity
    // =========================================================================

    public static String getSynthName() { return "Korg M1"; }
    public String getDefaultResourceFileName() { return "KorgM1.init"; }
    public String getHTMLResourceFileName() { return "KorgM1.html"; }


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
                new String[]{ "Bank", "Patch Number" },
                new JComponent[]{ bank, number },
                title, "Enter patch bank and number (00-99).");
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
        int pc = tempModel.get("bank") * 100 + tempModel.get("number");
        try
            {
            tryToSendMIDI(new ShortMessage(ShortMessage.PROGRAM_CHANGE,
                getChannelOut(), pc, 0));
            }
        catch (Exception e) { Synth.handleException(e); }

        // The Program Parameter Dump carries no bank/number, so update our model's
        // notion of location now, else patchLocationEquals() will reject the reply.
        if (!isMerging())
            {
            setSendMIDI(false);
            model.set("bank", tempModel.get("bank"));
            model.set("number", tempModel.get("number"));
            setSendMIDI(true);
            }
        }

    public int getPauseAfterChangePatch() { return 50; }

    public boolean getAlwaysChangesPatchesOnRequestDump() { return true; }

    public boolean testVerify(Synth synth2, String key, Object obj1, Object obj2)
        {
        return key.equals("bank") || key.equals("number");
        }

    // We have to force a change patch always because we're doing the equivalent of requestCurrentDump here
    public byte[] requestDump(Model tempModel)
        {
        return requestCurrentDump();
        }

    public byte[] requestCurrentDump()
        {
        return new byte[] {
            (byte)0xF0, (byte)0x42,
            (byte)(0x30 | Math.max(0, getChannelOut() - 1)),
            (byte)0x19, (byte)0x10, (byte)0xF7
            };
        }

    public void parseParameter(byte[] data)
        {
        if (data.length < 6) return;
        if (data[0] != (byte)0xF0 || data[1] != (byte)0x42 ||
            (data[2] & 0xF0) != 0x30 || data[3] != (byte)0x19) return;
        byte func = data[4];
        if (func == (byte)0x22)
            showSimpleError("Write Error", "Write failed. The M1 may be memory-protected or the card is not inserted.");
        else if (func == (byte)0x24)
            showSimpleError("Data Load Error", "The M1 reported a data load error.");
        }

    public Object[] emitAll(Model tempModel, boolean toWorkingMemory, boolean toFile)
        {
        if (tempModel == null) tempModel = getModel();

        byte[] patch = emit(tempModel, toWorkingMemory, toFile);

        if (toWorkingMemory || toFile)
            return new Object[] { patch };

        // When writing to a specific slot, send patch data then the write command.
        int ch = Math.max(0, getChannelOut() - 1);
        byte[] writeCmd = new byte[] {
            (byte)0xF0, (byte)0x42,
            (byte)(0x30 | ch),
            (byte)0x19, (byte)0x11,
            (byte)(tempModel.get("bank") & 0x0F),
            (byte)(tempModel.get("number") & 0x7F),
            (byte)0xF7
            };
        return new Object[] { patch, writeCmd };
        }


    // =========================================================================
    // Parse
    // =========================================================================

    public int parse(byte[] data, boolean fromFile)
        {
        byte[] raw = KorgM1Rec.convertTo8Bit(data, 5);   // 143 raw bytes

        // Name (bytes 0-9)
        byte[] nb = new byte[10];
        for (int i = 0; i < 10; i++) nb[i] = (byte)(raw[i] & 0x7F);
        try { model.set("name", new String(nb, "US-ASCII")); }
        catch (UnsupportedEncodingException e) { Synth.handleException(e); }

        // Oscillator Common
        model.set("oscmode",        raw[10] & 0xFF);
        model.set("polymode",       raw[11] & 0x01);
        model.set("hold",           (raw[11] >> 1) & 0x01);
        model.set("osc1multisound", raw[12] & 0xFF);
        model.set("osc1octave",     (int)(byte)raw[13]);
        model.set("osc2multisound", raw[14] & 0xFF);
        model.set("osc2octave",     (int)(byte)raw[15]);
        model.set("interval",       (int)(byte)raw[16]);
        model.set("detune",         (int)(byte)raw[17]);
        model.set("delaystart",     raw[18] & 0xFF);

        // Pitch MG (byte 19: bits 0-1=wave, bit5=osc1on, bit6=osc2on, bit7=keysync)
        model.set("pitchmgwave",     raw[19] & 0x03);
        model.set("pitchmgosc1on",   (raw[19] >> 5) & 0x01);
        model.set("pitchmgosc2on",   (raw[19] >> 6) & 0x01);
        model.set("pitchmgkeysync",  (raw[19] >> 7) & 0x01);
        model.set("pitchmgfreq",      raw[20] & 0xFF);
        model.set("pitchmgdelay",     raw[21] & 0xFF);
        model.set("pitchmgintensity", raw[22] & 0xFF);

        // Cutoff MG (byte 23: same bit layout as Pitch MG)
        model.set("cutoffmgwave",     raw[23] & 0x03);
        model.set("cutoffmgosc1on",   (raw[23] >> 5) & 0x01);
        model.set("cutoffmgosc2on",   (raw[23] >> 6) & 0x01);
        model.set("cutoffmgkeysync",  (raw[23] >> 7) & 0x01);
        model.set("cutoffmgfreq",      raw[24] & 0xFF);
        model.set("cutoffmgdelay",     raw[25] & 0xFF);
        model.set("cutoffmgintensity", raw[26] & 0xFF);

        // After Touch
        model.set("atpitch",    (int)(byte)raw[27]);
        model.set("atpitchmg",  (int)(byte)raw[28]);
        model.set("atvdfcutoff",(int)(byte)raw[29]);
        model.set("atvdfmg",    (int)(byte)raw[30]);
        model.set("atvdaamp",   (int)(byte)raw[31]);

        // Joy Stick
        model.set("joypitchbend",  (int)(byte)raw[32]);
        model.set("joysweepint",   (int)(byte)raw[33]);
        model.set("joypitchmgint", raw[34] & 0xFF);
        model.set("joyvdfmgfreq1", raw[35] & 0xFF);
        model.set("joyvdfmgint",   raw[36] & 0xFF);
        model.set("joyvdfmgfreq2", raw[37] & 0xFF);

        // Effects
        model.set("effect1type",      raw[38] & 0xFF);
        model.set("effect2type",      raw[39] & 0xFF);
        model.set("effect12balleft",  raw[40] & 0xFF);
        model.set("effect12balright", raw[41] & 0xFF);
        model.set("effect2levelleft", raw[42] & 0xFF);
        model.set("effect2levelright",raw[43] & 0xFF);
        model.set("output3pan",       raw[44] & 0xFF);
        model.set("output4pan",       raw[45] & 0xFF);
        model.set("effectrouting",    raw[46] & 0xFF);
        for (int i = 0; i < 8; i++)
            {
            model.set("effect1p" + (i + 1), raw[47 + i] & 0xFF);
            model.set("effect2p" + (i + 1), raw[55 + i] & 0xFF);
            }

        // OSC-1 Pitch EG (63-70)
        model.set("pitcheg1startlevel",   (int)(byte)raw[63]);
        model.set("pitcheg1attacktime",    raw[64] & 0xFF);
        model.set("pitcheg1attacklevel",  (int)(byte)raw[65]);
        model.set("pitcheg1decaytime",     raw[66] & 0xFF);
        model.set("pitcheg1releasetime",   raw[67] & 0xFF);
        model.set("pitcheg1releaselevel", (int)(byte)raw[68]);
        model.set("pitcheg1timevelsense", (int)(byte)raw[69]);
        model.set("pitcheg1levelvelsense",(int)(byte)raw[70]);

        // VDF-1 (71-77)
        model.set("vdf1cutoff",         raw[71] & 0xFF);
        model.set("vdf1kbdtrackcenter", raw[72] & 0xFF);
        model.set("vdf1cutoffkbdtrack", (int)(byte)raw[73]);
        model.set("vdf1egintensity",    raw[74] & 0xFF);
        model.set("vdf1egtimekbdtrack", raw[75] & 0xFF);
        model.set("vdf1egintvelsense",  (int)(byte)raw[76]);
        model.set("vdf1cutoffvelsense", (int)(byte)raw[77]);

        // VDF-1 EG (78-85)
        model.set("vdfeg1attacktime",   raw[78] & 0xFF);
        model.set("vdfeg1attacklevel",  (int)(byte)raw[79]);
        model.set("vdfeg1decaytime",    raw[80] & 0xFF);
        model.set("vdfeg1breakpoint",   (int)(byte)raw[81]);
        model.set("vdfeg1slopetime",    raw[82] & 0xFF);
        model.set("vdfeg1sustainlevel", (int)(byte)raw[83]);
        model.set("vdfeg1releasetime",  raw[84] & 0xFF);
        model.set("vdfeg1releaselevel", (int)(byte)raw[85]);

        // VDA-1 (86-91)
        model.set("vda1level",          raw[86] & 0xFF);
        model.set("vda1kbdtrackcenter", raw[87] & 0xFF);
        model.set("vda1ampkbdtrack",    (int)(byte)raw[88]);
        model.set("vda1ampvelsense",    (int)(byte)raw[89]);
        model.set("vda1egtimekbdtrack", raw[90] & 0xFF);
        model.set("vda1egtimevelsense", raw[91] & 0xFF);

        // VDA-1 EG (92-98)
        model.set("vdaeg1attacktime",   raw[92] & 0xFF);
        model.set("vdaeg1attacklevel",  (int)(byte)raw[93]);
        model.set("vdaeg1decaytime",    raw[94] & 0xFF);
        model.set("vdaeg1breakpoint",   (int)(byte)raw[95]);
        model.set("vdaeg1slopetime",    raw[96] & 0xFF);
        model.set("vdaeg1sustainlevel", (int)(byte)raw[97]);
        model.set("vdaeg1releasetime",  raw[98] & 0xFF);

        // EG Polarity/SW (99-102)
        model.set("egpol1", raw[99]  & 0xFF);
        model.set("egpol2", raw[100] & 0xFF);
        model.set("egpol3", raw[101] & 0xFF);
        model.set("egpol4", raw[102] & 0xFF);

        // OSC-2 Pitch EG (103-110)
        model.set("pitcheg2startlevel",   (int)(byte)raw[103]);
        model.set("pitcheg2attacktime",    raw[104] & 0xFF);
        model.set("pitcheg2attacklevel",  (int)(byte)raw[105]);
        model.set("pitcheg2decaytime",     raw[106] & 0xFF);
        model.set("pitcheg2releasetime",   raw[107] & 0xFF);
        model.set("pitcheg2releaselevel", (int)(byte)raw[108]);
        model.set("pitcheg2timevelsense", (int)(byte)raw[109]);
        model.set("pitcheg2levelvelsense",(int)(byte)raw[110]);

        // VDF-2 (111-117)
        model.set("vdf2cutoff",         raw[111] & 0xFF);
        model.set("vdf2kbdtrackcenter", raw[112] & 0xFF);
        model.set("vdf2cutoffkbdtrack", (int)(byte)raw[113]);
        model.set("vdf2egintensity",    raw[114] & 0xFF);
        model.set("vdf2egtimekbdtrack", raw[115] & 0xFF);
        model.set("vdf2egintvelsense",  (int)(byte)raw[116]);
        model.set("vdf2cutoffvelsense", (int)(byte)raw[117]);

        // VDF-2 EG (118-125)
        model.set("vdfeg2attacktime",   raw[118] & 0xFF);
        model.set("vdfeg2attacklevel",  (int)(byte)raw[119]);
        model.set("vdfeg2decaytime",    raw[120] & 0xFF);
        model.set("vdfeg2breakpoint",   (int)(byte)raw[121]);
        model.set("vdfeg2slopetime",    raw[122] & 0xFF);
        model.set("vdfeg2sustainlevel", (int)(byte)raw[123]);
        model.set("vdfeg2releasetime",  raw[124] & 0xFF);
        model.set("vdfeg2releaselevel", (int)(byte)raw[125]);

        // VDA-2 (126-131)
        model.set("vda2level",          raw[126] & 0xFF);
        model.set("vda2kbdtrackcenter", raw[127] & 0xFF);
        model.set("vda2ampkbdtrack",    (int)(byte)raw[128]);
        model.set("vda2ampvelsense",    (int)(byte)raw[129]);
        model.set("vda2egtimekbdtrack", raw[130] & 0xFF);
        model.set("vda2egtimevelsense", raw[131] & 0xFF);

        // VDA-2 EG (132-138)
        model.set("vdaeg2attacktime",   raw[132] & 0xFF);
        model.set("vdaeg2attacklevel",  (int)(byte)raw[133]);
        model.set("vdaeg2decaytime",    raw[134] & 0xFF);
        model.set("vdaeg2breakpoint",   (int)(byte)raw[135]);
        model.set("vdaeg2slopetime",    raw[136] & 0xFF);
        model.set("vdaeg2sustainlevel", (int)(byte)raw[137]);
        model.set("vdaeg2releasetime",  raw[138] & 0xFF);

        // EG Polarity/SW (OSC-2) (139-142)
        model.set("egpol5", raw[139] & 0xFF);
        model.set("egpol6", raw[140] & 0xFF);
        model.set("egpol7", raw[141] & 0xFF);
        model.set("egpol8", raw[142] & 0xFF);

        revise();
        return PARSE_SUCCEEDED;
        }


    // =========================================================================
    // Emit
    // =========================================================================

    public byte[] emit(Model tempModel, boolean toWorkingMemory, boolean toFile)
        {
        if (tempModel == null) tempModel = getModel();

        byte[] raw = new byte[143];

        // Name (bytes 0-9)
        String name = (model.get("name", "Init      ") + "          ").substring(0, 10);
        for (int i = 0; i < 10; i++) raw[i] = (byte)(name.charAt(i) & 0x7F);

        // Oscillator Common
        raw[10] = (byte)model.get("oscmode");
        raw[11] = (byte)((model.get("polymode") & 0x01) |
                         ((model.get("hold")     & 0x01) << 1));
        raw[12] = (byte)model.get("osc1multisound");
        raw[13] = (byte)model.get("osc1octave");
        raw[14] = (byte)model.get("osc2multisound");
        raw[15] = (byte)model.get("osc2octave");
        raw[16] = (byte)model.get("interval");
        raw[17] = (byte)model.get("detune");
        raw[18] = (byte)model.get("delaystart");

        // Pitch MG
        raw[19] = (byte)((model.get("pitchmgwave")    & 0x03) |
                         ((model.get("pitchmgosc1on")  & 0x01) << 5) |
                         ((model.get("pitchmgosc2on")  & 0x01) << 6) |
                         ((model.get("pitchmgkeysync") & 0x01) << 7));
        raw[20] = (byte)model.get("pitchmgfreq");
        raw[21] = (byte)model.get("pitchmgdelay");
        raw[22] = (byte)model.get("pitchmgintensity");

        // Cutoff MG
        raw[23] = (byte)((model.get("cutoffmgwave")    & 0x03) |
                         ((model.get("cutoffmgosc1on")  & 0x01) << 5) |
                         ((model.get("cutoffmgosc2on")  & 0x01) << 6) |
                         ((model.get("cutoffmgkeysync") & 0x01) << 7));
        raw[24] = (byte)model.get("cutoffmgfreq");
        raw[25] = (byte)model.get("cutoffmgdelay");
        raw[26] = (byte)model.get("cutoffmgintensity");

        // After Touch
        raw[27] = (byte)model.get("atpitch");
        raw[28] = (byte)model.get("atpitchmg");
        raw[29] = (byte)model.get("atvdfcutoff");
        raw[30] = (byte)model.get("atvdfmg");
        raw[31] = (byte)model.get("atvdaamp");

        // Joy Stick
        raw[32] = (byte)model.get("joypitchbend");
        raw[33] = (byte)model.get("joysweepint");
        raw[34] = (byte)model.get("joypitchmgint");
        raw[35] = (byte)model.get("joyvdfmgfreq1");
        raw[36] = (byte)model.get("joyvdfmgint");
        raw[37] = (byte)model.get("joyvdfmgfreq2");

        // Effects
        raw[38] = (byte)model.get("effect1type");
        raw[39] = (byte)model.get("effect2type");
        raw[40] = (byte)model.get("effect12balleft");
        raw[41] = (byte)model.get("effect12balright");
        raw[42] = (byte)model.get("effect2levelleft");
        raw[43] = (byte)model.get("effect2levelright");
        raw[44] = (byte)model.get("output3pan");
        raw[45] = (byte)model.get("output4pan");
        raw[46] = (byte)model.get("effectrouting");
        for (int i = 0; i < 8; i++)
            {
            raw[47 + i] = (byte)model.get("effect1p" + (i + 1));
            raw[55 + i] = (byte)model.get("effect2p" + (i + 1));
            }

        // OSC-1 Pitch EG
        raw[63] = (byte)model.get("pitcheg1startlevel");
        raw[64] = (byte)model.get("pitcheg1attacktime");
        raw[65] = (byte)model.get("pitcheg1attacklevel");
        raw[66] = (byte)model.get("pitcheg1decaytime");
        raw[67] = (byte)model.get("pitcheg1releasetime");
        raw[68] = (byte)model.get("pitcheg1releaselevel");
        raw[69] = (byte)model.get("pitcheg1timevelsense");
        raw[70] = (byte)model.get("pitcheg1levelvelsense");

        // VDF-1
        raw[71] = (byte)model.get("vdf1cutoff");
        raw[72] = (byte)model.get("vdf1kbdtrackcenter");
        raw[73] = (byte)model.get("vdf1cutoffkbdtrack");
        raw[74] = (byte)model.get("vdf1egintensity");
        raw[75] = (byte)model.get("vdf1egtimekbdtrack");
        raw[76] = (byte)model.get("vdf1egintvelsense");
        raw[77] = (byte)model.get("vdf1cutoffvelsense");

        // VDF-1 EG
        raw[78] = (byte)model.get("vdfeg1attacktime");
        raw[79] = (byte)model.get("vdfeg1attacklevel");
        raw[80] = (byte)model.get("vdfeg1decaytime");
        raw[81] = (byte)model.get("vdfeg1breakpoint");
        raw[82] = (byte)model.get("vdfeg1slopetime");
        raw[83] = (byte)model.get("vdfeg1sustainlevel");
        raw[84] = (byte)model.get("vdfeg1releasetime");
        raw[85] = (byte)model.get("vdfeg1releaselevel");

        // VDA-1
        raw[86] = (byte)model.get("vda1level");
        raw[87] = (byte)model.get("vda1kbdtrackcenter");
        raw[88] = (byte)model.get("vda1ampkbdtrack");
        raw[89] = (byte)model.get("vda1ampvelsense");
        raw[90] = (byte)model.get("vda1egtimekbdtrack");
        raw[91] = (byte)model.get("vda1egtimevelsense");

        // VDA-1 EG
        raw[92] = (byte)model.get("vdaeg1attacktime");
        raw[93] = (byte)model.get("vdaeg1attacklevel");
        raw[94] = (byte)model.get("vdaeg1decaytime");
        raw[95] = (byte)model.get("vdaeg1breakpoint");
        raw[96] = (byte)model.get("vdaeg1slopetime");
        raw[97] = (byte)model.get("vdaeg1sustainlevel");
        raw[98] = (byte)model.get("vdaeg1releasetime");

        // EG Polarity/SW
        raw[99]  = (byte)model.get("egpol1");
        raw[100] = (byte)model.get("egpol2");
        raw[101] = (byte)model.get("egpol3");
        raw[102] = (byte)model.get("egpol4");

        // OSC-2 Pitch EG
        raw[103] = (byte)model.get("pitcheg2startlevel");
        raw[104] = (byte)model.get("pitcheg2attacktime");
        raw[105] = (byte)model.get("pitcheg2attacklevel");
        raw[106] = (byte)model.get("pitcheg2decaytime");
        raw[107] = (byte)model.get("pitcheg2releasetime");
        raw[108] = (byte)model.get("pitcheg2releaselevel");
        raw[109] = (byte)model.get("pitcheg2timevelsense");
        raw[110] = (byte)model.get("pitcheg2levelvelsense");

        // VDF-2
        raw[111] = (byte)model.get("vdf2cutoff");
        raw[112] = (byte)model.get("vdf2kbdtrackcenter");
        raw[113] = (byte)model.get("vdf2cutoffkbdtrack");
        raw[114] = (byte)model.get("vdf2egintensity");
        raw[115] = (byte)model.get("vdf2egtimekbdtrack");
        raw[116] = (byte)model.get("vdf2egintvelsense");
        raw[117] = (byte)model.get("vdf2cutoffvelsense");

        // VDF-2 EG
        raw[118] = (byte)model.get("vdfeg2attacktime");
        raw[119] = (byte)model.get("vdfeg2attacklevel");
        raw[120] = (byte)model.get("vdfeg2decaytime");
        raw[121] = (byte)model.get("vdfeg2breakpoint");
        raw[122] = (byte)model.get("vdfeg2slopetime");
        raw[123] = (byte)model.get("vdfeg2sustainlevel");
        raw[124] = (byte)model.get("vdfeg2releasetime");
        raw[125] = (byte)model.get("vdfeg2releaselevel");

        // VDA-2
        raw[126] = (byte)model.get("vda2level");
        raw[127] = (byte)model.get("vda2kbdtrackcenter");
        raw[128] = (byte)model.get("vda2ampkbdtrack");
        raw[129] = (byte)model.get("vda2ampvelsense");
        raw[130] = (byte)model.get("vda2egtimekbdtrack");
        raw[131] = (byte)model.get("vda2egtimevelsense");

        // VDA-2 EG
        raw[132] = (byte)model.get("vdaeg2attacktime");
        raw[133] = (byte)model.get("vdaeg2attacklevel");
        raw[134] = (byte)model.get("vdaeg2decaytime");
        raw[135] = (byte)model.get("vdaeg2breakpoint");
        raw[136] = (byte)model.get("vdaeg2slopetime");
        raw[137] = (byte)model.get("vdaeg2sustainlevel");
        raw[138] = (byte)model.get("vdaeg2releasetime");

        // EG Polarity/SW (OSC-2)
        raw[139] = (byte)model.get("egpol5");
        raw[140] = (byte)model.get("egpol6");
        raw[141] = (byte)model.get("egpol7");
        raw[142] = (byte)model.get("egpol8");

        // Pack and wrap in SysEx header/footer (170 bytes total)
        byte[] packed = KorgM1Rec.convertTo7Bit(raw);
        byte[] result = new byte[6 + packed.length];
        result[0] = (byte)0xF0;
        result[1] = (byte)0x42;
        result[2] = (byte)(0x30 | Math.max(0, getChannelOut() - 1));
        result[3] = (byte)0x19;
        result[4] = (byte)0x40;
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
