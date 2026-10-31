package com.gathin.tvhome.backlight;

/**
 * Album colours the way the launcher's aurora background makes them (AuroraPalette and AuroraView, copied here: an
 * add-on never reaches into the launcher). Four colours from the cover's quarters; four soft clouds of them drifting
 * on 90-second paths; the strip shows the screen's overall tint as they pass. The clock is the system's
 * uptime, the same in every app, so with the aurora on screen the strip matches it.
 */
final class Aurora {
    private Aurora() {
    }

    /** A colour for the strip: hue 0-360, saturation 0-1000 (Tuya's scale). */
    static final class Hue {
        final int h, s;

        Hue(int h, int s) {
            this.h = h;
            this.s = s;
        }

        @Override
        public String toString() {
            return h + "/" + s;
        }
    }

    /** The aurora's default colours (wine, plum, amber, deep blue), when the cover has none. */
    static final int[] DEFAULT = {0xFF7A1E3A, 0xFF4B2A6B, 0xFFB4532A, 0xFF1F3A6E};
    /** Each cloud's path: x and y frequencies over the loop, and phases. */
    private static final float[][] PATH = {{1, 2, 0.0f, 0.3f}, {2, 1, 0.4f, 0.1f}, {1, 3, 0.7f, 0.6f}, {3, 2, 0.2f, 0.85f}};
    static final long LOOP_MS = 90_000, FADE_MS = 2000;
    /** Hue turns per quarter, so a cover in one colour still gives four distinct clouds. */
    private static final float[] HUE_SPREAD = {0f, -0.06f, 0.07f, 0.13f};
    /** The screen's shape, for the clouds' round edges. */
    private static final float W = 16, H = 9;

    /** Four colours from an 8 × 8 copy of the cover: the average of each quarter, vivid and dimmed as a backdrop. */
    static int[] colours(int[] argb, int w, int h) {
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

    /** Saturation at least 0.45 (greys stay grey), brightness 0.28 to 0.55, hue turned by spread. */
    static int backdrop(int r, int g, int b, float spread) {
        float[] hsv = new float[3];
        hsv(0xFF000000 | r << 16 | g << 8 | b, hsv);
        float hue = hsv[0] / 360f, s = hsv[1], v = hsv[2];
        if (s > 0.08f) s = Math.max(s, 0.45f);
        else spread = 0;
        hue = ((hue + spread) % 1f + 1f) % 1f;
        v = Math.max(0.28f, Math.min(0.55f, v));
        return hsvToRgb(hue, s, v);
    }

    /**
     * The aurora's colour at a point of the screen (0..1 across, 0..1 down) at {@code phase} (0..1 of the loop):
     * a dark base in the first colour, then each cloud over it, opaque at its centre and gone at its edge.
     */
    static int colourAt(int[] c, float phase, float px, float py) {
        int col = mix(0xFF000000, c[0], 0.18f);
        float radius = Math.max(W, H) * 0.48f;
        for (int i = 0; i < 4; i++) {
            float[] p = PATH[i];
            float x = 0.5f + 0.42f * (float) Math.sin(2 * Math.PI * (p[0] * phase + p[2]));
            float y = 0.45f + 0.40f * (float) Math.cos(2 * Math.PI * (p[1] * phase + p[3]));
            float d = (float) Math.hypot((x - px) * W, (y - py) * H) / radius;
            col = mix(col, c[i], alpha(d));
        }
        return col;
    }

    /** The cloud's gradient: 0xF0 at its centre, 0x70 at 45 %, nothing at its edge. */
    static float alpha(float d) {
        if (d >= 1) return 0;
        if (d <= 0.45f) return (0xF0 + (0x70 - 0xF0) * d / 0.45f) / 255f;
        return 0x70 * (1 - (d - 0.45f) / 0.55f) / 255f;
    }

    /** The strip's colour now: the screen's overall tint in the aurora, as hue and saturation for the box. */
    static Hue now(int[] from, int[] to, long fadeStart, long uptime) {
        float t = Math.min(1f, (uptime - fadeStart) / (float) FADE_MS);
        int[] c = new int[4];
        for (int i = 0; i < 4; i++) c[i] = mix(from[i], to[i], t);
        return tint(c, (uptime % LOOP_MS) / (float) LOOP_MS);
    }

    /**
     * What the whole screen looks like at {@code phase}: the aurora at 8 x 5 points, their hues averaged round the
     * colour wheel, each weighted by how vivid and bright it is (a plain average of the colours turns to mud where
     * clouds of different colours meet).
     */
    static Hue tint(int[] c, float phase) {
        double x = 0, y = 0, sat = 0, weight = 0;
        float[] hsv = new float[3];
        for (int gy = 0; gy < 5; gy++) {
            for (int gx = 0; gx < 8; gx++) {
                hsv(colourAt(c, phase, (gx + 0.5f) / 8, (gy + 0.5f) / 5), hsv);
                double w = hsv[1] * hsv[2];
                double rad = Math.toRadians(hsv[0]);
                x += Math.cos(rad) * w;
                y += Math.sin(rad) * w;
                sat += hsv[1] * w;
                weight += w;
            }
        }
        if (weight < 1e-3) return new Hue(0, 0);                 // a grey cover: white
        int h = (int) Math.round((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
        float s = (float) (sat / weight);                       // the vivid points' saturation, not the gaps'
        return forStrip(hsvToRgb(h / 360f, s, 0.5f));
    }

    /**
     * A screen colour as the strip should show it. Screen colours are gamma-encoded and an LED's light is not, so
     * sent as they are they look washed out; and a strip's green looks far brighter than its blue, so a blue with a
     * little green in it shows cyan. So: decoded (gamma 2.2), green toned down by a quarter, then hue and saturation
     * from that. Greys stay white.
     */
    static Hue forStrip(int rgb) {
        float[] hsv = new float[3];
        hsv(rgb, hsv);
        if (hsv[1] < 0.1f) return new Hue(0, 0);
        int vivid = hsvToRgb(hsv[0] / 360f, hsv[1], 1f);          // the colour at full brightness: the box sets that
        double r = linear((vivid >> 16) & 0xFF), g = linear((vivid >> 8) & 0xFF) * GREEN, b = linear(vivid & 0xFF);
        double max = Math.max(r, Math.max(g, b));
        int led = 0xFF000000 | (int) Math.round(r / max * 255) << 16 | (int) Math.round(g / max * 255) << 8
                | (int) Math.round(b / max * 255);
        hsv(led, hsv);
        return new Hue(Math.round(hsv[0]) % 360, Math.round(hsv[1] * 1000));
    }

    /** How bright a strip's green looks next to its red and blue. */
    private static final double GREEN = 0.75;

    private static double linear(int c) {
        return Math.pow(c / 255.0, 2.2);
    }

    /** Android's Color.colorToHSV, here so the tests run on a plain JVM. */
    static void hsv(int argb, float[] out) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = 60 * (((g - b) / d) % 6);
        else if (max == g) h = 60 * ((b - r) / d + 2);
        else h = 60 * ((r - g) / d + 4);
        if (h < 0) h += 360;
        out[0] = h;
        out[1] = max == 0 ? 0 : d / max;
        out[2] = max;
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
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
        return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }
}
