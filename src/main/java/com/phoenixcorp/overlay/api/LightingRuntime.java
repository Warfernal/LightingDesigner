package com.phoenixcorp.overlay.api;

import com.phoenixcorp.overlay.*;

import java.awt.Rectangle;
import java.util.Optional;

/**
 * Service runtime centralisant les interactions OCR ↔ Chroma.
 */
public class LightingRuntime {

    private final ChromaSessionManager chroma = new ChromaSessionManager();
    private final ColorMatrixBuilder builder  = new ColorMatrixBuilder();

    private LightingOverrides overrides;
    private SnapshotToMatrix snapshotToMatrix;
    private OcrRunner ocrRunner;

    private volatile OcrReader.Snapshot lastSnapshot;
    private volatile int[][] lastMatrix;
    private double lastHpPct  = -1.0;
    private double lastResPct = -1.0;

    private final double minDeltaPct = 0.01; // 1%

    public LightingRuntime(LightingOverrides overrides) {
        this.overrides = overrides == null ? LightingOverrides.loadOrDefaults() : overrides;
        this.snapshotToMatrix = new SnapshotToMatrix(builder, this.overrides);
    }

    public synchronized LightingOverrides getOverrides() {
        return overrides;
    }

    public Optional<OcrReader.Snapshot> getLastSnapshot() {
        return Optional.ofNullable(lastSnapshot);
    }

    public synchronized int[][] currentPreviewMatrix() {
        if (lastSnapshot != null) {
            try {
                return copyMatrix(snapshotToMatrix.toKeyboard(lastSnapshot));
            } catch (Exception ignore) { }
        }
        if (overrides != null && overrides.hasBackground()) {
            return builder.full(overrides.backgroundBgr());
        }
        return builder.empty();
    }

    public synchronized boolean start() {
        if (ocrRunner != null && ocrRunner.isRunning()) {
            return false;
        }

        applyBackgroundToDevices();

        OcrReader reader = buildOcrReaderFromConfigOrDefault();
        lastHpPct = -1.0;
        lastResPct = -1.0;
        lastMatrix = null;

        ocrRunner = new OcrRunner(reader, snapshot -> {
            synchronized (LightingRuntime.this) {
                lastSnapshot = snapshot;

                double hpPct = pct(snapshot.hpCur, snapshot.hpMax);
                double resPct = pct(snapshot.resCur, snapshot.resMax);

                if (lastHpPct >= 0 && Math.abs(hpPct - lastHpPct) < minDeltaPct
                        && lastResPct >= 0 && Math.abs(resPct - lastResPct) < minDeltaPct) {
                    return;
                }

                int[][] matrix = snapshotToMatrix.toKeyboard(snapshot);
                if (!deepEquals(lastMatrix, matrix)) {
                    chroma.keyboardCustom(matrix);
                    lastMatrix = copyMatrix(matrix);
                    lastHpPct = hpPct;
                    lastResPct = resPct;
                }
            }
        }, 100);

        ocrRunner.start();
        return true;
    }

    public synchronized boolean stop() {
        if (ocrRunner == null) {
            return false;
        }
        ocrRunner.stop();
        ocrRunner = null;
        return true;
    }

    public synchronized void refreshOverrides(boolean persist) {
        snapshotToMatrix = new SnapshotToMatrix(builder, overrides);
        applyBackgroundToDevices();
        if (persist) {
            LightingOverrides.save(overrides);
        }
        repaintImmediate();
    }

    public synchronized void updateOverrides(LightingOverrides newOverrides, boolean persist) {
        this.overrides = newOverrides == null ? LightingOverrides.loadOrDefaults() : newOverrides;
        refreshOverrides(persist);
    }

    public synchronized void defineOcrArea(Rectangle area) {
        if (area == null) {
            return;
        }
        ConfigManager cm = ConfigManager.getInstance();
        Config cfg = cm.getConfig();
        cfg.setOcrCaptureArea(area);
        cm.save(cfg);
    }

    public Optional<Rectangle> loadOcrAreaFromConfig() {
        try {
            Config cfg = ConfigManager.getInstance().getConfig();
            return Optional.ofNullable(cfg.getOcrCaptureArea());
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    public void shutdown() {
        try {
            if (ocrRunner != null) {
                ocrRunner.shutdown();
                ocrRunner = null;
            }
        } catch (Exception ignore) { }
        try {
            chroma.close();
        } catch (Exception ignore) { }
    }

    private void applyBackgroundToDevices() {
        if (overrides != null && overrides.hasBackground()) {
            chroma.setStaticAllDevices(overrides.backgroundBgr());
        }
    }

    public synchronized void repaintImmediate() {
        if (snapshotToMatrix == null) {
            snapshotToMatrix = new SnapshotToMatrix(builder, overrides);
        }
        if (lastSnapshot != null) {
            try {
                int[][] matrix = snapshotToMatrix.toKeyboard(lastSnapshot);
                chroma.keyboardCustom(matrix);
                lastMatrix = copyMatrix(matrix);
                return;
            } catch (Exception ignore) { }
        }

        if (overrides != null && overrides.hasBackground()) {
            chroma.setStaticAllDevices(overrides.backgroundBgr());
        }
        int[][] matrix = builder.full(overrides != null ? overrides.backgroundBgr() : 0);
        chroma.keyboardCustom(matrix);
        lastMatrix = copyMatrix(matrix);
    }

    private OcrReader buildOcrReaderFromConfigOrDefault() {
        Rectangle area = loadOcrAreaFromConfig().orElse(new Rectangle(100, 100, 400, 120));
        ConfigManager cm = ConfigManager.getInstance();
        Config cfg = cm.getConfig();
        String lang = java.util.Optional.ofNullable(cfg.getTessLang()).orElse("eng");
        String tessDataPath = cfg.getTessDataPath();

        if (tessDataPath == null || tessDataPath.isBlank()) {
            java.nio.file.Path tessDir = TessdataBootstrapper.ensureLocalTessdata(lang);
            tessDataPath = tessDir.toString();
            cfg.tessDataPath = tessDataPath;
            if (cfg.tessLang == null || cfg.tessLang.isBlank()) {
                cfg.tessLang = lang;
            }
            cm.save(cfg);
            System.out.println("[Tessdata] datapath=" + tessDataPath + " (lang=" + lang + ")");
        }

        return new TesseractOcrReader(area, tessDataPath, lang);
    }

    private static boolean deepEquals(int[][] a, int[][] b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (!java.util.Arrays.equals(a[i], b[i])) {
                return false;
            }
        }
        return true;
    }

    private static int[][] copyMatrix(int[][] matrix) {
        if (matrix == null) {
            return null;
        }
        int[][] copy = new int[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            copy[i] = java.util.Arrays.copyOf(matrix[i], matrix[i].length);
        }
        return copy;
    }

    private static double pct(int cur, int max) {
        if (max <= 0) {
            return 0;
        }
        double p = (double) cur / (double) max;
        if (p < 0) p = 0;
        if (p > 1) p = 1;
        return p;
    }
}
