package com.gathin.tvhome;

/**
 * "columns": "auto": tiles per row from the panel's size. Android has no "inches" for a TV, but many panels report
 * their true pixel density (a 55-inch 4K panel says 80 dpi), so the diagonal follows from the pixels. Firmware that
 * reports a made-up density (a standard bucket such as 160 or 320, or x and y that disagree) gives 0: unknown.
 */
final class ScreenSize {
    private ScreenSize() {
    }

    /** Columns when the size is unknown: what most living-room TVs (50 to 65 inches) get. */
    static final int FALLBACK_COLUMNS = 6;
    /** The densities Android buckets use; a panel reporting exactly one of them is reporting the bucket. */
    private static final int[] BUCKETS = {120, 160, 213, 240, 320, 480, 640};

    /** The diagonal in inches, or 0 when the reported density cannot be trusted. */
    static float inches(int widthPx, int heightPx, float xdpi, float ydpi) {
        if (widthPx <= 0 || heightPx <= 0 || xdpi < 10 || ydpi < 10) return 0;
        if (Math.abs(xdpi - ydpi) > 0.1f * Math.max(xdpi, ydpi)) return 0;
        for (int b : BUCKETS) if (Math.abs(xdpi - b) < 0.5f) return 0;
        double w = widthPx / (double) xdpi, h = heightPx / (double) ydpi;
        float d = (float) Math.sqrt(w * w + h * h);
        return d >= 19 && d <= 150 ? d : 0;      // no TV is outside this: the density is wrong
    }

    /** Tiles per row: 4 under 40 inches, 5 to 50, 6 to 65, 7 above; the fallback when unknown. */
    static int columns(float inches) {
        if (inches <= 0) return FALLBACK_COLUMNS;
        if (inches < 40) return 4;
        if (inches < 50) return 5;
        if (inches < 66) return 6;
        return 7;
    }
}
