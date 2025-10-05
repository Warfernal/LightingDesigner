package com.phoenixcorp.overlay;

import com.phoenixcorp.overlay.api.LightingRuntime;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;

public final class LightingDesignerController {

    private LightingOverrides overrides        = LightingOverrides.loadOrDefaults();
    private LightingRuntime runtime;
    private static final int PREVIEW_COLS = 22;

    // Boutons
    @FXML private Button startBtn;
    @FXML private Button stopBtn;
    @FXML private Button defineAreaBtn;
    @FXML private Button presetWowBtn;
    @FXML private Button resetColorsBtn;

    // Statut
    @FXML private Label statusLbl;

    // Color pickers (simples)
    @FXML private ColorPicker hpColorPicker;
    @FXML private ColorPicker resColorPicker;
    @FXML private ColorPicker bgColorPicker;

    // Color pickers (avancés)
    @FXML private ColorPicker manaColorPicker;
    @FXML private ColorPicker rageColorPicker;
    @FXML private ColorPicker energyColorPicker;
    @FXML private ColorPicker focusColorPicker;
    @FXML private ColorPicker furyColorPicker;
    @FXML private ColorPicker insanityColorPicker;
    @FXML private ColorPicker maelstromColorPicker;
    @FXML private ColorPicker runicColorPicker;

    // Aperçu
    @FXML private Slider hpPreviewSlider;
    @FXML private Slider resPreviewSlider;
    @FXML private Label  hpPreviewLabel;
    @FXML private Label  resPreviewLabel;
    @FXML private HBox   hpPreviewRow;
    @FXML private HBox   resPreviewRow;

    private javafx.scene.layout.Region[] hpKeys;
    private javafx.scene.layout.Region[] resKeys;

    @FXML
    public void initialize() {
        runtime = new LightingRuntime(overrides);

        // Actions
        if (startBtn     != null) startBtn.setOnAction(e -> onStart());
        if (stopBtn      != null) stopBtn.setOnAction(e -> onStop());
        if (defineAreaBtn!= null) defineAreaBtn.setOnAction(e -> onDefineArea());
        if (presetWowBtn != null) presetWowBtn.setOnAction(e -> applyWowPresetAndSave());
        if (resetColorsBtn!=null) resetColorsBtn.setOnAction(e -> resetDefaultsAndSave());

        // Init pickers depuis overrides (Object -> int RGB -> Color)
        syncUiFromOverrides();

        // Auto-apply & auto-save sur changement
        attachColorPicker(hpColorPicker,      () -> { overrides.hpColor = toRgbInt(hpColorPicker.getValue()); onColorsChanged(true); });
        attachColorPicker(resColorPicker,     () -> { overrides.resourceColor = toRgbInt(resColorPicker.getValue()); onColorsChanged(true); });
        attachColorPicker(bgColorPicker,      () -> { overrides.backgroundColor = toRgbInt(bgColorPicker.getValue()); onColorsChanged(true); });

        attachColorPicker(manaColorPicker,    () -> { putRes("MANA",        manaColorPicker); onColorsChanged(true); });
        attachColorPicker(rageColorPicker,    () -> { putRes("RAGE",        rageColorPicker); onColorsChanged(true); });
        attachColorPicker(energyColorPicker,  () -> { putRes("ENERGY",      energyColorPicker); onColorsChanged(true); });
        attachColorPicker(focusColorPicker,   () -> { putRes("FOCUS",       focusColorPicker); onColorsChanged(true); });
        attachColorPicker(furyColorPicker,    () -> { putRes("FURY",        furyColorPicker); onColorsChanged(true); });
        attachColorPicker(insanityColorPicker,()-> { putRes("INSANITY",     insanityColorPicker); onColorsChanged(true); });
        attachColorPicker(maelstromColorPicker,()->{ putRes("MAELSTROM",    maelstromColorPicker); onColorsChanged(true); });
        attachColorPicker(runicColorPicker,   () -> { putRes("RUNIC_POWER", runicColorPicker); onColorsChanged(true); });

        // Preview widgets
        setupPreviewRows();
        if (hpPreviewSlider != null) {
            hpPreviewSlider.valueProperty().addListener((obs, o, v) -> {
                if (hpPreviewLabel != null) hpPreviewLabel.setText(v.intValue() + "%");
                updatePreview();
            });
        }
        if (resPreviewSlider != null) {
            resPreviewSlider.valueProperty().addListener((obs, o, v) -> {
                if (resPreviewLabel != null) resPreviewLabel.setText(v.intValue() + "%");
                updatePreview();
            });
        }
        updatePreview();

        setStatus("Prêt");
        if (stopBtn != null) stopBtn.setDisable(true);
    }

    // ---------- Actions principales ----------

    private void onStart() {
        if (!runtime.start()) return;

        if (startBtn != null) startBtn.setDisable(true);
        if (stopBtn  != null) stopBtn.setDisable(false);
        setStatus("En cours (OCR actif)");
    }

    private void onStop() {
        runtime.stop();
        if (startBtn != null) startBtn.setDisable(false);
        if (stopBtn  != null) stopBtn.setDisable(true);
        setStatus("Arrêté");
        // On laisse l’affichage courant
    }

    private void onDefineArea() {
        try {
            SelectCaptureArea.SelectionResult res = SelectCaptureArea.selectInteractiveForApi();
            Rectangle area = res.area();
            if (area == null) {
                if (res.timedOut()) setStatus("Sélection OCR expirée");
                return;
            }
            runtime.defineOcrArea(area);
            setStatus("Zone OCR définie: " + area.width + "x" + area.height + "@" + area.x + "," + area.y);
        } catch (Throwable t) {
            System.err.println("[OCR] Define area failed: " + t.getMessage());
        }
    }

    // ---------- UI helpers ----------

    private void applyWowPresetAndSave() {
        // HP vert, Mana bleu, Runic cyan, Rage rouge, Énergie orange
        overrides.hpColor        = 0x00FF00;
        overrides.resourceColor  = 0xFFA500;
        overrides.backgroundColor= 0x102040;
        ensureResMap();
        overrides.resourceColors.put("MANA",        0x0000FF);
        overrides.resourceColors.put("RAGE",        0xFF0000);
        overrides.resourceColors.put("ENERGY",      0xFFA500);
        overrides.resourceColors.put("FOCUS",       0xFFFF00);
        overrides.resourceColors.put("FURY",        0xFF00FF);
        overrides.resourceColors.put("INSANITY",    0x00FFFF);
        overrides.resourceColors.put("MAELSTROM",   0x808080);
        overrides.resourceColors.put("RUNIC_POWER", 0x00FFFF);

        runtime.refreshOverrides(true);
        syncUiFromOverrides();
        updatePreview();
        setStatus("Preset WoW appliqué");
    }

    private void resetDefaultsAndSave() {
        overrides = LightingOverrides.loadOrDefaults(); // recharge defaults si pas de fichier
        runtime.updateOverrides(overrides, true);
        syncUiFromOverrides();
        updatePreview();
        setStatus("Couleurs réinitialisées");
    }

    private void onColorsChanged(boolean persist) {
        runtime.refreshOverrides(persist);
        updatePreview();
    }

    private void syncUiFromOverrides() {
        if (hpColorPicker != null) hpColorPicker.setValue(toFxColorObj(overrides.hpColor));
        if (resColorPicker != null) resColorPicker.setValue(toFxColorObj(overrides.resourceColor));
        if (bgColorPicker != null) bgColorPicker.setValue(toFxColorObj(overrides.backgroundColor));

        ensureResMap();
        if (manaColorPicker != null)      manaColorPicker.setValue(toFxColorObj(overrides.resourceColors.get("MANA")));
        if (rageColorPicker != null)      rageColorPicker.setValue(toFxColorObj(overrides.resourceColors.get("RAGE")));
        if (energyColorPicker != null)    energyColorPicker.setValue(toFxColorObj(overrides.resourceColors.get("ENERGY")));
        if (focusColorPicker != null)     focusColorPicker.setValue(toFxColorObj(overrides.resourceColors.get("FOCUS")));
        if (furyColorPicker != null)      furyColorPicker.setValue(toFxColorObj(overrides.resourceColors.get("FURY")));
        if (insanityColorPicker != null)  insanityColorPicker.setValue(toFxColorObj(overrides.resourceColors.get("INSANITY")));
        if (maelstromColorPicker != null) maelstromColorPicker.setValue(toFxColorObj(overrides.resourceColors.get("MAELSTROM")));
        if (runicColorPicker != null)     runicColorPicker.setValue(toFxColorObj(overrides.resourceColors.get("RUNIC_POWER")));
    }

    private void attachColorPicker(ColorPicker cp, Runnable onChange) {
        if (cp == null) return;
        cp.setOnAction(e -> onChange.run());
    }

    private void putRes(String key, ColorPicker cp) {
        ensureResMap();
        overrides.resourceColors.put(key, toRgbInt(cp.getValue()));
    }

    private void ensureResMap() {
        if (overrides.resourceColors == null) overrides.resourceColors = new HashMap<>();
    }

    private void setStatus(String s) {
        if (statusLbl != null) Platform.runLater(() -> statusLbl.setText(s));
    }

    public void shutdown() {
        if (runtime == null) return;
        try {
            runtime.stop();
        } catch (Exception ignore) {}
        runtime.shutdown();
    }

    // ---------- Aperçu (simulation) ----------

    private void setupPreviewRows() {
        hpKeys = new javafx.scene.layout.Region[PREVIEW_COLS];
        resKeys = new javafx.scene.layout.Region[PREVIEW_COLS];
        if (hpPreviewRow != null) {
            hpPreviewRow.getChildren().clear();
            for (int i = 0; i < PREVIEW_COLS; i++) {
                javafx.scene.layout.Region r = new javafx.scene.layout.Region();
                r.getStyleClass().add("key");
                hpPreviewRow.getChildren().add(r);
                hpKeys[i] = r;
            }
        }
        if (resPreviewRow != null) {
            resPreviewRow.getChildren().clear();
            for (int i = 0; i < PREVIEW_COLS; i++) {
                javafx.scene.layout.Region r = new javafx.scene.layout.Region();
                r.getStyleClass().add("key");
                resPreviewRow.getChildren().add(r);
                resKeys[i] = r;
            }
        }
    }

    private void updatePreview() {
        if (hpKeys == null || resKeys == null) return;
        int hpFill = (int) Math.round((hpPreviewSlider != null ? hpPreviewSlider.getValue() : 75) / 100.0 * PREVIEW_COLS);
        int rsFill = (int) Math.round((resPreviewSlider != null ? resPreviewSlider.getValue() : 40) / 100.0 * PREVIEW_COLS);

        String bgCss  = cssFromRgbObj(overrides.backgroundColor);
        String hpCss  = cssFromRgbObj(overrides.hpColor);
        String resCss = cssFromRgbObj(overrides.resourceColor);

        for (int i = 0; i < PREVIEW_COLS; i++) {
            hpKeys[i].setStyle("-fx-background-color: " + (i < hpFill ? hpCss : bgCss) + ";");
            resKeys[i].setStyle("-fx-background-color: " + (i < rsFill ? resCss : bgCss) + ";");
        }
        if (hpPreviewLabel != null && hpPreviewSlider != null) hpPreviewLabel.setText(((int) hpPreviewSlider.getValue()) + "%");
        if (resPreviewLabel != null && resPreviewSlider != null) resPreviewLabel.setText(((int) resPreviewSlider.getValue()) + "%");
    }

    // ---------- Conversions locales ----------

    private static Color toFxColorObj(Object any) {
        int rgb = toRgbInt(any, 0xFFFFFF);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8)  & 0xFF;
        int b = (rgb)       & 0xFF;
        return Color.rgb(r, g, b);
    }

    private static String cssFromRgbObj(Object any) {
        int rgb = toRgbInt(any, 0xFFFFFF);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8)  & 0xFF;
        int b = (rgb)       & 0xFF;
        return String.format("rgb(%d,%d,%d)", r, g, b);
    }

    private static int toRgbInt(Color c) {
        if (c == null) return 0xFFFFFF;
        int r = (int)Math.round(c.getRed()*255);
        int g = (int)Math.round(c.getGreen()*255);
        int b = (int)Math.round(c.getBlue()*255);
        return (r<<16)|(g<<8)|b;
    }

    /** Parse int RGB depuis Object (Number, "0xRRGGBB", "#RRGGBB", "16753920"). */
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
