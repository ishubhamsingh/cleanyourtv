package com.gathin.tvhome.backlight;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What the LDD11 MAX can show, as the data points that set it (watched from the Smart Life app): 21 the mode, 22
 * the brightness, 23 the white's warmth, 24 a colour, 25 Sync and the scenes. The box only shows a white or a colour
 * inside its scene for them (Calm 88, Color 97, as the app's tiles), so those looks set the scene first, then the mode
 * and the value; and every look names its mode, or a box left on a colour ignores a new scene.
 */
final class Looks {
    private Looks() {
    }

    static final String POWER = "20", MODE = "21", BRIGHT = "22", TEMP = "23", COLOUR = "24", SCENE = "25";
    static final int BRIGHT_MIN = 10, BRIGHT_MAX = 1000;

    /** The scenes after Calm, as the app lists them, and their codes (the first byte of data point 25). */
    static final String[] SCENE_NAMES = {"Rainbow", "Fire", "Fireworks", "Star", "Rain", "Atom", "Smooth", "Bounce",
            "Kinetic", "Breathe"};
    static final int[] SCENE_CODES = {0x86, 0x87, 0x89, 0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96};
    /** Sync's diffusion, Low to High. */
    static final int[] SYNC_CODES = {0x80, 0x81, 0x82};
    /** Calm's five whites, warmest first (data point 23). */
    static final int[] TEMPS = {0, 250, 500, 750, 1000};
    /** The colour swatches' hues: red, orange, amber, green, teal, blue, violet, pink. */
    static final int[] HUES = {0, 22, 40, 120, 175, 240, 270, 320};
    /** The scenes a white and a colour live in: Calm and Color. */
    static final int CALM_CODE = 0x88, COLOUR_CODE = 0x97;

    static Map<String, String> sync(int level) {
        return scene(SYNC_CODES[Math.max(0, Math.min(2, level))]);
    }

    static Map<String, String> scene(int code) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(MODE, "scene");
        m.put(SCENE, sceneValue(code));
        return m;
    }

    static String sceneValue(int code) {
        return String.format(Locale.ROOT, "%02X", code) + "000000000000000000000000000000";
    }

    /** Just the colour, for the steps of a fade once {@link #colour} has put the box on it. */
    static Map<String, String> colourOnly(int h, int s, int v) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(COLOUR, hsv(h, s, v));
        return m;
    }

    static Map<String, String> calm(int temp) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(SCENE, sceneValue(CALM_CODE));
        m.put(MODE, "white");
        m.put(TEMP, String.valueOf(temp));
        return m;
    }

    /** A colour: hue 0-360, saturation and value 0-1000, as data point 24 writes it (hhhhssssvvvv). */
    static Map<String, String> colour(int h, int s, int v) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(SCENE, sceneValue(COLOUR_CODE));
        m.put(MODE, "colour");
        m.put(COLOUR, hsv(h, s, v));
        return m;
    }

    static String hsv(int h, int s, int v) {
        return String.format(Locale.ROOT, "%04x%04x%04x", clamp(h, 0, 360), clamp(s, 0, 1000), clamp(v, 0, 1000));
    }

    /** [h, s, v] from data point 24, or null. */
    static int[] parseHsv(String v) {
        if (v == null || v.length() != 12) return null;
        try {
            return new int[]{Integer.parseInt(v.substring(0, 4), 16), Integer.parseInt(v.substring(4, 8), 16),
                    Integer.parseInt(v.substring(8, 12), 16)};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** The default look away from the sync input: Calm at its warmest. */
    static Map<String, String> defaultOther() {
        return calm(TEMPS[0]);
    }

    /** What the box shows, read from its data points. */
    static final class State {
        boolean on = true;
        String mode = "";
        int bright = BRIGHT_MAX, temp, sceneCode = -1;
        int[] hsv;

        boolean isSync() {
            return "scene".equals(mode) && sceneCode >= SYNC_CODES[0] && sceneCode <= SYNC_CODES[2];
        }

        int syncLevel() {
            return isSync() ? sceneCode - SYNC_CODES[0] : -1;
        }

        /** Index in {@link #SCENE_CODES}, or -1. */
        int sceneIndex() {
            if (!"scene".equals(mode)) return -1;
            for (int i = 0; i < SCENE_CODES.length; i++) if (SCENE_CODES[i] == sceneCode) return i;
            return -1;
        }

        /** Index in {@link #TEMPS} when on Calm, or -1. */
        int tempIndex() {
            if (!"white".equals(mode)) return -1;
            int best = 0;
            for (int i = 1; i < TEMPS.length; i++) if (Math.abs(TEMPS[i] - temp) < Math.abs(TEMPS[best] - temp)) best = i;
            return best;
        }

        /** Index in {@link #HUES} when on a colour, or -1. */
        int hueIndex() {
            if (!"colour".equals(mode) || hsv == null) return -1;
            for (int i = 0; i < HUES.length; i++) if (Math.abs(HUES[i] - hsv[0]) <= 3) return i;
            return -1;
        }

        /** Merges a report from the box ({"dps":{...}} or {"data":{"dps":{...}}}). */
        void update(String json) {
            try {
                JSONObject o = new JSONObject(json);
                JSONObject d = o.has("dps") ? o.getJSONObject("dps") : o.optJSONObject("data") != null
                        ? o.getJSONObject("data").optJSONObject("dps") : null;
                if (d != null) update(d);
            } catch (JSONException ignored) {
                // a reply without data points
            }
        }

        void update(JSONObject d) {
            for (Iterator<String> it = d.keys(); it.hasNext(); ) {
                String k = it.next();
                Object v = d.opt(k);
                set(k, String.valueOf(v));
            }
        }

        /** Applies what was just sent (the box reports only some of it back). */
        void apply(Map<String, String> dps) {
            for (Map.Entry<String, String> e : dps.entrySet()) set(e.getKey(), e.getValue());
        }

        private void set(String k, String v) {
            try {
                switch (k) {
                    case POWER: on = Boolean.parseBoolean(v); break;
                    case MODE: mode = v; break;
                    case BRIGHT: bright = Integer.parseInt(v); break;
                    case TEMP: temp = Integer.parseInt(v); break;
                    case COLOUR: hsv = parseHsv(v.toLowerCase(Locale.ROOT)); break;
                    case SCENE: sceneCode = Integer.parseInt(v.substring(0, 2), 16); break;
                    default: break;
                }
            } catch (RuntimeException ignored) {
                // a value in a shape this box doesn't use
            }
        }
    }

    /** A map as JSON (for keeping the look used away from the sync input). */
    static String toJson(Map<String, String> m) {
        return new JSONObject(m).toString();
    }

    /** A kept look with its Calm or Color scene in front (looks kept before they carried it). */
    static Map<String, String> fromJson(String json) {
        Map<String, String> m = parse(json);
        if (m.containsKey(SCENE) || !m.containsKey(MODE)) return m;
        int code = "white".equals(m.get(MODE)) ? CALM_CODE : "colour".equals(m.get(MODE)) ? COLOUR_CODE : -1;
        if (code < 0) return m;
        Map<String, String> out = new LinkedHashMap<>();
        out.put(SCENE, sceneValue(code));
        out.putAll(m);
        return out;
    }

    private static Map<String, String> parse(String json) {
        Map<String, String> m = new LinkedHashMap<>();
        if (json == null) return m;
        try {
            JSONObject o = new JSONObject(json);
            // The scene, then the mode, then the rest, whatever order the JSON kept.
            if (o.has(SCENE)) m.put(SCENE, o.getString(SCENE));
            if (o.has(MODE)) m.put(MODE, o.getString(MODE));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                if (!k.equals(MODE) && !k.equals(SCENE)) m.put(k, o.getString(k));
            }
        } catch (JSONException e) {
            m.clear();
        }
        return m;
    }
}
