package com.phoenixcorp.overlay;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class LightingDesignerController {

    private final ChromaSessionManager chroma = new ChromaSessionManager();
    private final ColorMatrixBuilder builder   = new ColorMatrixBuilder();
    private LightingOverrides overrides        = LightingOverrides.loadOrDefaults();

    private OcrRunner ocrRunner;
    private SnapshotToMatrix snapshotToMatrix;

    private double minDeltaPct = 0.01; // 1%

    @FXML private Button startBtn;
    @FXML private Button stopBtn;
    @FXML private Button defineAreaBtn;
    @FXML private Label  statusLbl;

    @FXML
    public void initialize() {
        snapshotToMatrix = new SnapshotToMatrix(builder, overrides);
        if (startBtn     != null) startBtn.setOnAction(e -> onStart());
        if (stopBtn      != null) stopBtn.setOnAction(e -> onStop());
        if (defineAreaBtn!= null) defineAreaBtn.setOnAction(e -> onDefineArea());
        setStatus("Stopped");
        if (stopBtn != null) stopBtn.setDisable(true);
    }

    private void onStart() {
        if (ocrRunner != null && ocrRunner.isRunning()) return;

        // Pousse la couleur de fond sur les autres périphériques si définie
        if (overrides.hasBackground()) {
            chroma.setStaticAllDevices(overrides.backgroundBgr());
        }

        OcrReader reader = buildOcrReaderFromConfigOrDefault();

        final AtomicReference<int[][]> lastMatrix = new AtomicReference<>();
        final AtomicReference<Double> lastHpPct   = new AtomicReference<>(-1.0);
        final AtomicReference<Double> lastResPct  = new AtomicReference<>(-1.0);

        ocrRunner = new OcrRunner(reader, snap -> {
            double hpPct = pct(snap.hpCur,  snap.hpMax);
            double rPct  = pct(snap.resCur, snap.resMax);

            Double lh = lastHpPct.get();
            Double lr = lastResPct.get();
            if (lh >= 0 && Math.abs(hpPct - lh) < minDeltaPct && lr >= 0 && Math.abs(rPct - lr) < minDeltaPct) {
                return;
            }

            int[][] m = snapshotToMatrix.toKeyboard(snap);
            int[][] prev = lastMatrix.get();
            if (!deepEquals(prev, m)) {
                chroma.keyboardCustom(m);
                lastMatrix.set(copyMatrix(m));
                lastHpPct.set(hpPct);
                lastResPct.set(rPct);
            }
        }, 100); // 100 ms => suffisant et moins bruyant

        ocrRunner.start();
        if (startBtn != null) startBtn.setDisable(true);
        if (stopBtn  != null) stopBtn.setDisable(false);
        setStatus("Running");
    }

    private void onStop() {
        if (ocrRunner != null) {
            ocrRunner.stop();
            ocrRunner = null;
        }
        if (startBtn != null) startBtn.setDisable(false);
        if (stopBtn  != null) stopBtn.setDisable(true);
        setStatus("Stopped");
        // On laisse l’affichage tel quel
    }

    private void onDefineArea() {
        try {
            Optional<Rectangle> sel = SelectCaptureArea.selectInteractive();
            if (sel.isEmpty()) return;
            Rectangle area = sel.get();
            ConfigManager cm = ConfigManager.getInstance();
            Config cfg = cm.getConfig();
            cfg.setOcrCaptureArea(area);
            cm.save(cfg);
            setStatus("Zone définie: " + area.width + "x" + area.height + "@" + area.x + "," + area.y);
        } catch (Throwable t) {
            System.err.println("[OCR] Define area failed: " + t.getMessage());
        }
    }

    private void setStatus(String s) {
        if (statusLbl != null) Platform.runLater(() -> statusLbl.setText(s));
    }

    private OcrReader buildOcrReaderFromConfigOrDefault() {
        Rectangle area = loadOcrAreaFromConfig().orElse(new Rectangle(100, 100, 400, 120));
        ConfigManager cm = ConfigManager.getInstance();
        Config cfg = cm.getConfig();
        String lang = Optional.ofNullable(cfg.getTessLang()).orElse("eng");
        String tessDataPath = cfg.getTessDataPath();

        if (tessDataPath == null || tessDataPath.isBlank()) {
            java.nio.file.Path tessDir = TessdataBootstrapper.ensureLocalTessdata(lang);
            tessDataPath = tessDir.toString();
            cfg.tessDataPath = tessDataPath;
            if (cfg.tessLang == null || cfg.tessLang.isBlank()) cfg.tessLang = lang;
            cm.save(cfg);
            System.out.println("[Tessdata] datapath=" + tessDataPath + " (lang=" + lang + ")");
        }

        return new TesseractOcrReader(area, tessDataPath, lang);
        // Pour un test sans OCR : return new FakeOcrReader();
    }

    private Optional<Rectangle> loadOcrAreaFromConfig() {
        try {
            Config cfg = ConfigManager.getInstance().getConfig();
            return Optional.ofNullable(cfg.getOcrCaptureArea());
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    public void shutdown() {
        try {
            if (ocrRunner != null) { ocrRunner.shutdown(); ocrRunner = null; }
        } catch (Exception ignore) {}
        try { chroma.close(); } catch (Exception ignore) {}
    }

    // ===== Utils =====
    private static boolean deepEquals(int[][] a, int[][] b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (!java.util.Arrays.equals(a[i], b[i])) return false;
        return true;
    }
    private static int[][] copyMatrix(int[][] m) {
        if (m == null) return null;
        int[][] c = new int[m.length][];
        for (int i = 0; i < m.length; i++) c[i] = java.util.Arrays.copyOf(m[i], m[i].length);
        return c;
    }
    private static double pct(int cur, int max) {
        if (max <= 0) return 0;
        double p = (double) cur / (double) max;
        if (p < 0) p = 0; if (p > 1) p = 1;
        return p;
    }
}
