package com.phoenixcorp.overlay;

public class ColorMatrixBuilder {
    public static final int ROWS = 6;
    public static final int COLS = 22;

    public int[][] empty() {
        return new int[ROWS][COLS];
    }

    public int[][] full(int bgr) {
        int[][] m = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) m[r][c] = bgr;
        }
        return m;
    }

    /**
     * Remplit une barre horizontale [firstCol..lastCol] sur la ligne 'row' avec la couleur 'bgr'
     * proportionnellement à pct. IMPORTANT : efface d'abord toute la plage (met à 0) pour éviter
     * la persistance des LEDs quand pct diminue ou vaut 0.
     */
    public void applyBarRows(int[][] m, int row, int firstCol, int lastCol, int bgr, double pct) {
        if (m == null) return;
        if (row < 0 || row >= ROWS) return;

        if (firstCol < 0) firstCol = 0;
        if (lastCol >= COLS) lastCol = COLS - 1;
        if (lastCol < firstCol) return;

        // Clamp pct
        if (pct < 0) pct = 0;
        if (pct > 1) pct = 1;

        final int width = lastCol - firstCol + 1;

        // Efface toute la zone d'abord
        for (int c = firstCol; c <= lastCol; c++) {
            m[row][c] = 0x000000;
        }

        // Calcule le nombre de colonnes à allumer
        int fill = (int) Math.floor(pct * width);
        // Option "garde 1 LED si pct>0"
        if (pct > 0 && fill == 0) fill = 1;
        if (fill > width) fill = width;

        // Remplit
        for (int i = 0; i < fill; i++) {
            int c = firstCol + i;
            m[row][c] = bgr;
        }
    }
}
