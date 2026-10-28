package com.gathin.tvhome;

/** The aurora background's colours from artwork: one per quarter of the image, made vivid and dark enough. */
final class AuroraPalette {
    private AuroraPalette() {
    }

    /** The default colours: wine, plum, amber and deep blue, as in the dashboard mock. */
    static final int[] DEFAULT = {0xFF7A1E3A, 0xFF4B2A6B, 0xFFB4532A, 0xFF1F3A6E};

    /** Four colours from ARGB pixels (w × h): the average of each quarter, saturated and dimmed for a backdrop. */
    static int[] fromPixels(int[] argb, int w, int h) {
        if (argb == null || w < 2 || h < 2 || argb.length < w * h) return DEFAULT.clone();
        int[] out = new int[4];
        for (int q = 0; q < 4; q++) {
            int x0 = (q % 2) * (w / 2), y0 = (q / 2) * (h / 2), x1 = q % 2 == 0 ? w / 2 : w, y1 = q / 2 == 0 ? h / 2 : h;
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int c = argb[y * w + x];
                    r += (c >> 16) & 0xFF;
                    g += (c >> 8) & 0xFF;
                    b += c & 0xFF;
                    n++;
                }
            }
            out[q] = backdrop((int) (r / n), (int) (g / n), (int) (b / n), HUE_SPREAD[q]);
        }
        return out;
    }

    /** Hue turns per quarter, so artwork in one colour still gives four distinct clouds. */
    private static final float[] HUE_SPREAD = {0f, -0.06f, 0.07f, 0.13f};

    static int backdrop(int r, int g, int b) {
        return backdrop(r, g, b, 0f);
    }

    /** A colour for behind white text: saturation at least 0.45, brightness between 0.28 and 0.55, hue turned by spread. */
    static int backdrop(int r, int g, int b, float spread) {
        float max = Math.max(r, Math.max(g, b)) / 255f, min = Math.min(r, Math.min(g, b)) / 255f;
        float v = max, s = max == 0 ? 0 : (max - min) / max, hue = 0;
        if (max != min) {
            float rf = r / 255f, gf = g / 255f, bf = b / 255f, d = max - min;
            if (max == rf) hue = ((gf - bf) / d + (gf < bf ? 6 : 0)) / 6f;
            else if (max == gf) hue = ((bf - rf) / d + 2) / 6f;
            else hue = ((rf - gf) / d + 4) / 6f;
        }
        if (s > 0.08f) s = Math.max(s, 0.45f);       // greys stay grey
        else spread = 0;
        hue = ((hue + spread) % 1f + 1f) % 1f;
        v = Math.max(0.28f, Math.min(0.55f, v));
        return 0xFF000000 | hsvToRgb(hue, s, v);
    }

    private static int hsvToRgb(float h, float s, float v) {
        int i = (int) Math.floor(h * 6) % 6;
        float f = h * 6 - (float) Math.floor(h * 6), p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    /** a → b at t (0..1), per channel. */
    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}
