package com.phoenixcorp.overlay;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

public final class LightingOverrides {
    // Zones
    public int hpRow, hpFirstCol, hpLastCol;
    public int resourceRow, resourceFirstCol, resourceLastCol;

    // Couleurs saisies en JSON (on les considère en RGB)
    public Integer hpColor;                 // RGB
    public Integer resourceColor;           // RGB (fallback)
    public Map<String,Integer> resourceColors; // RGB par type ("MANA","RAGE",...)
    public Integer backgroundColor;         // RGB

    private static final ObjectMapper OM = new ObjectMapper();

    public static LightingOverrides loadOrDefaults() {
        // 1) Working dir
        try {
            File f = new File("lighting_overrides.json");
            if (f.exists()) {
                try (InputStream in = new FileInputStream(f)) {
                    LightingOverrides lo = OM.readValue(in, LightingOverrides.class);
                    System.out.println("[Overrides] loaded from working dir: " + f.getAbsolutePath());
                    return lo;
                }
            }
        } catch (Exception e) {
            System.err.println("[Overrides] parse error (working dir): " + e.getMessage());
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

        // 3) Defaults (en RGB)
        LightingOverrides d = new LightingOverrides();
        d.hpRow = 0; d.hpFirstCol = 0; d.hpLastCol = 21;
        d.resourceRow = 1; d.resourceFirstCol = 0; d.resourceLastCol = 21;
        d.hpColor = 0x0000FF;        // Bleu (RGB)
        d.resourceColor = 0x00FF00;  // Vert (RGB)
        d.backgroundColor = 0x102040;// Bleu nuit (RGB)
        System.err.println("[Overrides] using defaults");
        return d;
    }

    // ======= Getters utilisés par SnapshotToMatrix (retournent du BGR) =======

    public int hpRow()        { return hpRow; }
    public int hpFirstCol()   { return hpFirstCol; }
    public int hpLastCol()    { return hpLastCol; }
    public int hpBgr()        { return rgbToBgr(hpColor != null ? hpColor : 0x0000FF); }

    public int resourceRow()      { return resourceRow; }
    public int resourceFirstCol() { return resourceFirstCol; }
    public int resourceLastCol()  { return resourceLastCol; }
    public int resourceBgr()      { return rgbToBgr(resourceColor != null ? resourceColor : 0x00FF00); }

    public Optional<Integer> resourceBgrFor(OcrReader.ResourceType t) {
        if (t == null || resourceColors == null) return Optional.empty();
        Integer rgb = resourceColors.get(t.name());
        return Optional.ofNullable(rgb).map(LightingOverrides::rgbToBgr);
    }

    public boolean hasBackground() { return backgroundColor != null; }
    public int backgroundBgr()     { return rgbToBgr(backgroundColor != null ? backgroundColor : 0x102040); }

    // ======= Conversion =======
    /** Convertit 0xRRGGBB (RGB) -> 0xBBGGRR (BGR) attendu par Razer. */
    public static int rgbToBgr(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8)  & 0xFF;
        int b = (rgb)       & 0xFF;
        return (b << 16) | (g << 8) | r;
    }
}
