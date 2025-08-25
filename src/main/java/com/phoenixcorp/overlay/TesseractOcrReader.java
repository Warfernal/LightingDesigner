package com.phoenixcorp.overlay;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TesseractOcrReader implements OcrReader {

    private final Rectangle captureArea;
    private final Tesseract tess;

    // HP: n'importe quel "nnnn/nnnn"
    private static final Pattern HP_PATTERN =
            Pattern.compile("(\\d{1,4})\\s*/\\s*(\\d{1,4})");

    // RESSOURCE: **token OBLIGATOIRE** + "nnnn/nnnn"
    // On supporte EN/FR les principaux types.
    private static final Pattern RES_PATTERN =
            Pattern.compile(
                    "(MANA|RAGE|ENERGY|FOCUS|FURY|INSANITY|MAELSTROM|RUNIC(?:\\s*POWER)?|"
                            + "ENERGIE|PUISSANCE\\s*RUNIQUE)\\s*[:=]?\\s*(\\d{1,4})\\s*/\\s*(\\d{1,4})",
                    Pattern.CASE_INSENSITIVE
            );

    private OcrReader.ResourceType lastType = ResourceType.MANA;
    private int lastHpMax = 1000, lastResMax = 1000;

    public TesseractOcrReader(Rectangle captureArea, String tessDataPath, String lang) {
        this.captureArea = new Rectangle(captureArea);
        this.tess = new Tesseract();
        if (tessDataPath != null && !tessDataPath.isBlank()) {
            tess.setDatapath(tessDataPath); // Doit pointer sur le dossier "tessdata"
        }
        tess.setLanguage((lang == null || lang.isBlank()) ? "eng" : lang);

        // Laisse Tesseract choisir le moteur dispo
        tess.setOcrEngineMode(3); // OEM_DEFAULT
        tess.setPageSegMode(6);   // Bloc de texte uniforme
        try { tess.setTessVariable("debug_file", "NUL"); } catch (Exception ignore) {}
        try { tess.setConfigs(java.util.Collections.emptyList()); } catch (Exception ignore) {}
    }

    @Override
    public Snapshot read() {
        try {
            BufferedImage img = new Robot().createScreenCapture(captureArea);
            String raw = tess.doOCR(img);
            if (raw == null) return null;

            // Normalisation simple (pour matcher "ÉNERGIE" etc.)
            String text = stripAccents(raw);

            // ====== HP ======
            int hpCur = -1, hpMax = -1;
            Matcher hp = HP_PATTERN.matcher(text);
            if (hp.find()) {
                hpCur = parseSafe(hp.group(1));
                hpMax = parseSafe(hp.group(2));
                if (hpMax > 0) lastHpMax = hpMax;
            }

            // ====== RESOURCE (avec token obligatoire) ======
            int resCur = -1, resMax = -1;
            ResourceType type = lastType;

            Matcher rm = RES_PATTERN.matcher(text);
            while (rm.find()) {
                String token = rm.group(1);
                int cur = parseSafe(rm.group(2));
                int max = parseSafe(rm.group(3));
                ResourceType t = toType(token);
                if (t != ResourceType.UNKNOWN && max > 0) {
                    type = t;
                    resCur = cur;
                    resMax = max;
                    break; // on prend la première occurrence valide
                }
            }

            // Conserve les max connus si non lus cette frame (évite de retomber à 0)
            if (hpMax <= 0) hpMax = lastHpMax;
            if (resMax <= 0) resMax = lastResMax;

            // Si on a bien eu une ressource valide, mémorise son max
            if (resMax > 0) lastResMax = resMax;
            lastType = type;

            // Sanity + clamp
            if (hpMax <= 0 || resMax <= 0) return null;
            if (hpCur < 0) hpCur = 0;
            if (resCur < 0) resCur = 0;
            hpCur = Math.min(hpCur, hpMax);
            resCur = Math.min(resCur, resMax);

            return new Snapshot(hpCur, hpMax, resCur, resMax, type);

        } catch (AWTException | TesseractException e) {
            System.err.println("[OCR] " + e.getMessage());
            return null;
        } catch (Throwable t) {
            System.err.println("[OCR] Unexpected: " + t.getMessage());
            return null;
        }
    }

    private static int parseSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }

    // Mappe EN/FR vers ResourceType
    private static ResourceType toType(String token) {
        if (token == null) return ResourceType.UNKNOWN;
        String k = stripAccents(token).toUpperCase(Locale.ROOT).replaceAll("\\s+","");
        if ("MANA".equals(k)) return ResourceType.MANA;
        if ("RAGE".equals(k)) return ResourceType.RAGE;
        if ("ENERGY".equals(k) || "ENERGIE".equals(k)) return ResourceType.ENERGY;
        if ("FOCUS".equals(k)) return ResourceType.FOCUS;
        if ("FURY".equals(k)) return ResourceType.FURY;
        if ("INSANITY".equals(k)) return ResourceType.INSANITY;
        if ("MAELSTROM".equals(k)) return ResourceType.MAELSTROM;
        if ("RUNICPOWER".equals(k) || "PUISSANCERUNIQUE".equals(k) || "RUNIC".equals(k)) return ResourceType.RUNIC_POWER;
        return ResourceType.UNKNOWN;
    }

    private static String stripAccents(String in) {
        try {
            String norm = Normalizer.normalize(in, Normalizer.Form.NFD);
            return norm.replaceAll("\\p{M}+", "");
        } catch (Exception e) {
            return in;
        }
    }
}
