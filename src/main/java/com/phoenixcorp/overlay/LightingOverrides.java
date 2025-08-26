package com.phoenixcorp.overlay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class LightingOverrides {
    // Zones (HP sur row 0, Ressource sur row 1)
    public int hpRow = 0, hpFirstCol = 0, hpLastCol = 21;
    public int resourceRow = 1, resourceFirstCol = 0, resourceLastCol = 21;

    // Couleurs (RGB côté appli)
    public Object hpColor;                 // int ou string; on convertit à l’usage
    public Object resourceColor;
    public Map<String, Object> resourceColors;
    public Object backgroundColor;

    private static final ObjectMapper OM = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static LightingOverrides loadOrDefaults() {
        // 1) Working dir
        File f = new File("lighting_overrides.json");
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                LightingOverrides lo = OM.readValue(in, LightingOverrides.class);
                System.out.println("[Overrides] loaded from working dir: " + f.getAbsolutePath());
                return lo;
            } catch (Exception e) {
                System.err.println("[Overrides] parse error (working dir): " + e.getMessage());
            }
        }
        // 2) Classpath
        try (InputStream in = LightingOverrides.class.getResourceAsStream("/lighting_overrides.json")) {
            if (in != null) {
                LightingOverrides lo = OM.readValue(in, LightingOverrides.class);
                System.out.println("[Overrides] loaded from classpath: /lighting_overrides.json");
                return lo;
            }
        } catch (Exception e) {
            System.err.println("[Overrides] parse error (classpath): " + e.getMessage());
        }
        // 3) Defaults WoW (RGB)
        LightingOverrides d = new LightingOverrides();
        d.hpColor        = 0x00FF00; // HP vert
        d.resourceColor  = 0xFFA500; // res générique orange
        d.backgroundColor= 0x102040; // bleu nuit
        d.resourceColors = new HashMap<>();
        d.resourceColors.put("MANA",        0x0000FF); // bleu
        d.resourceColors.put("RAGE",        0xFF0000); // rouge
        d.resourceColors.put("ENERGY",      0xFFA500); // orange
        d.resourceColors.put("FOCUS",       0xFFFF00); // jaune
        d.resourceColors.put("FURY",        0xFF00FF); // magenta
        d.resourceColors.put("INSANITY",    0x00FFFF); // cyan
        d.resourceColors.put("MAELSTROM",   0x808080); // gris
        d.resourceColors.put("RUNIC_POWER", 0x00FFFF); // cyan
        System.err.println("[Overrides] using defaults");
        return d;
    }

    public static void save(LightingOverrides lo) {
        try (OutputStream out = new FileOutputStream(new File("lighting_overrides.json"))) {
            Map<String, Object> m = new HashMap<>();
            m.put("hpRow", lo.hpRow);
            m.put("hpFirstCol", lo.hpFirstCol);
            m.put("hpLastCol", lo.hpLastCol);
            m.put("resourceRow", lo.resourceRow);
            m.put("resourceFirstCol", lo.resourceFirstCol);
            m.put("resourceLastCol", lo.resourceLastCol);
            m.put("hpColor", toRgbInt(lo.hpColor));
            m.put("resourceColor", toRgbInt(lo.resourceColor));
            m.put("backgroundColor", toRgbInt(lo.backgroundColor));
            if (lo.resourceColors != null) {
                Map<String, Integer> rc = new HashMap<>();
                for (Map.Entry<String, Object> e : lo.resourceColors.entrySet()) {
                    rc.put(e.getKey(), toRgbInt(e.getValue()));
                }
                m.put("resourceColors", rc);
            }
            OM.writeValue(out, m);
            System.out.println("[Overrides] saved to working dir.");
        } catch (Exception e) {
            System.err.println("[Overrides] save error: " + e.getMessage());
        }
    }

    // Getters (BGR pour Razer)
    public int hpRow()        { return hpRow; }
    public int hpFirstCol()   { return hpFirstCol; }
    public int hpLastCol()    { return hpLastCol; }
    public int hpBgr()        { return rgbToBgr(toRgbInt(hpColor, 0x00FF00)); }

    public int resourceRow()      { return resourceRow; }
    public int resourceFirstCol() { return resourceFirstCol; }
    public int resourceLastCol()  { return resourceLastCol; }
    public int resourceBgr()      { return rgbToBgr(toRgbInt(resourceColor, 0xFFA500)); }

    public Optional<Integer> resourceBgrFor(OcrReader.ResourceType t) {
        if (t == null || resourceColors == null) return Optional.empty();
        Object val = resourceColors.get(t.name());
        Integer rgb = (val == null ? null : toRgbInt(val));
        return Optional.ofNullable(rgb).map(LightingOverrides::rgbToBgr);
    }

    public boolean hasBackground() { return backgroundColor != null; }
    public int backgroundBgr()     { return rgbToBgr(toRgbInt(backgroundColor, 0x102040)); }

    // Conversion / Parsing
    public static int rgbToBgr(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8)  & 0xFF;
        int b = (rgb)       & 0xFF;
        return (b << 16) | (g << 8) | r;
    }
    private static int toRgbInt(Object any) { return toRgbInt(any, 0xFFFFFF); }
    private static int toRgbInt(Object any, int def) {
        if (any == null) return def;
        if (any instanceof Number n) return n.intValue();
        if (any instanceof String s) {
            String t = s.trim();
            try {
                if (t.startsWith("#")) return Integer.parseInt(t.substring(1), 16);
                if (t.startsWith("0x") || t.startsWith("0X")) return Integer.parseInt(t.substring(2), 16);
                return Integer.parseInt(t);
            } catch (Exception ignore) { return def; }
        }
        return def;
    }
}
