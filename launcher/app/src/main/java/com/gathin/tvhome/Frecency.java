package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * How much an app is used from Home, for grid_order "frequent": every launch adds one, and the total halves every
 * week, so an app used daily this week ranks above one used a lot last month. Like LastOpened, it only counts
 * launches from Home (no permission, nothing read from other apps).
 */
final class Frecency {
    private static final String PREFS = "frecency";
    private static final double HALF_LIFE_MS = 7d * 24 * 60 * 60 * 1000;

    private Frecency() {
    }

    /** Notes that Home just opened this app. */
    static void record(Context ctx, String pkg) {
        long now = System.currentTimeMillis();
        SharedPreferences p = prefs(ctx);
        double score = decayed(p.getString(pkg, null), now) + 1;
        p.edit().putString(pkg, score + "@" + now).apply();
    }

    /** The app's score now; 0 when Home never opened it. */
    static double get(Context ctx, String pkg) {
        return pkg == null ? 0 : decayed(prefs(ctx).getString(pkg, null), System.currentTimeMillis());
    }

    /** A stored "score@millis", decayed to now, or 0 for none or a value that is not one. */
    static double decayed(String stored, long now) {
        if (stored == null) return 0;
        int at = stored.indexOf('@');
        if (at <= 0) return 0;
        try {
            double score = Double.parseDouble(stored.substring(0, at));
            long when = Long.parseLong(stored.substring(at + 1));
            return score * Math.pow(0.5, Math.max(0, now - when) / HALF_LIFE_MS);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
