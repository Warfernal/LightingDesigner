package com.phoenixcorp.overlay;

import java.util.Optional;

public final class SnapshotToMatrix {
    private static final boolean DEBUG = false;

    private final ColorMatrixBuilder builder;
    private final LightingOverrides overrides;

    public SnapshotToMatrix(ColorMatrixBuilder builder, LightingOverrides overrides) {
        this.builder = builder;
        this.overrides = overrides;
    }

    public int[][] toKeyboard(OcrReader.Snapshot s) {
        if (DEBUG && s != null) {
            double hpPct  = clampPct(s.hpCur,  s.hpMax);
            double resPct = clampPct(s.resCur, s.resMax);
            System.out.printf(
                    "[DBG] hp=%d/%d (%.2f)  res=%d/%d (%.2f)  type=%s%n",
                    s.hpCur, s.hpMax, hpPct, s.resCur, s.resMax, resPct, s.type
            );
        }

        // 1) Fond (background) si défini, sinon noir
        final int[][] m = overrides.hasBackground()
                ? builder.full(overrides.backgroundBgr())
                : builder.empty();

        // 2) Barre HP
        final double hpPct = clampPct(s.hpCur, s.hpMax);
        builder.applyBarRows(
                m,
                overrides.hpRow(),
                overrides.hpFirstCol(),
                overrides.hpLastCol(),
                overrides.hpBgr(),
                hpPct
        );

        // 3) Barre Ressource — couleur dépendante du type, sinon fallback resourceBgr()
        final int resColor = resourceBgrFor(s.type).orElseGet(() -> overrides.resourceBgr());
        final double resPct = clampPct(s.resCur, s.resMax);
        builder.applyBarRows(
                m,
                overrides.resourceRow(),
                overrides.resourceFirstCol(),
                overrides.resourceLastCol(),
                resColor,
                resPct
        );

        return m;
    }

    private Optional<Integer> resourceBgrFor(OcrReader.ResourceType type) {
        try {
            return overrides.resourceBgrFor(type);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private static double clampPct(int cur, int max) {
        if (max <= 0) return 0.0;
        double p = (double) cur / (double) max;
        if (p < 0) p = 0;
        if (p > 1) p = 1;
        return p;
    }
}
