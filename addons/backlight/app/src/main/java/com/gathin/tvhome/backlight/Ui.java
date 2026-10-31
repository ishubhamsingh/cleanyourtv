package com.gathin.tvhome.backlight;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;

/** Names and colours for looks, and the dashboard's font. */
final class Ui {
    private Ui() {
    }

    private static Typeface regular, medium;

    /** Outfit, the launcher's font, at weight 400 for text and 500 for headings (a variable font: set, not picked). */
    static Typeface font(Context c, boolean heading) {
        if (regular == null) {
            regular = outfit(c, 400);
            medium = outfit(c, 500);
        }
        return heading ? medium : regular;
    }

    private static Typeface outfit(Context c, int weight) {
        try {
            return new Typeface.Builder(c.getAssets(), "outfit.ttf")
                    .setFontVariationSettings("'wght' " + weight).setWeight(weight).build();
        } catch (RuntimeException e) {
            return Typeface.DEFAULT;
        }
    }

    static String lookName(Context c, Looks.State s) {
        if (s.isSync()) return c.getString(R.string.mode_sync) + " · " + c.getResources().getStringArray(R.array.sync_levels)[s.syncLevel()];
        int t = s.tempIndex();
        if (t >= 0) return c.getString(R.string.calm) + " · " + c.getResources().getStringArray(R.array.temps)[t];
        int i = s.sceneIndex();
        if (i >= 0) return Looks.SCENE_NAMES[i];
        int h = s.hueIndex();
        if (h >= 0) return c.getResources().getStringArray(R.array.hues)[h];
        if ("colour".equals(s.mode)) return c.getString(R.string.colour);
        if ("scene".equals(s.mode) && s.sceneCode >= 0x98) return c.getString(R.string.music_mode);
        return c.getString(R.string.mode_other);
    }

    /** The colour a white of this warmth looks like (0 warm to 1000 cool), for its swatch and the screen's glow. */
    static int white(int temp) {
        float t = temp / 1000f;
        int warm = 0xFFFFB46B, cool = 0xFFD6E4FF;
        return blend(warm, cool, t);
    }

    static int hue(int h, int s) {
        return Color.HSVToColor(new float[]{h, s / 1000f, 1f});
    }

    /** The light's colour now, for the glow behind the controller. */
    static int glow(Looks.State s) {
        if (!s.on) return 0xFF20242A;
        if (s.isSync()) return 0xFF8E6CF0;
        if ("white".equals(s.mode)) return white(s.temp);
        if ("colour".equals(s.mode) && s.hsv != null) return hue(s.hsv[0], s.hsv[1]);
        return 0xFF5A8DEE;
    }

    static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }
}
