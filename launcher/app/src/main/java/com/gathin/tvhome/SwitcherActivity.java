package com.gathin.tvhome;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The app switcher (the "switcher" app_keys action, e.g. "hold:KEYCODE_BACK:switcher"): the apps last in front, in
 * a glass row over whatever is on screen, the one before the current app focused, so the button and OK go back to
 * it. An app opens through its launch intent, which brings back its task as it was when Android still has it in
 * memory; Android shows no app which others are still running, so one it has closed starts again. Pressing the
 * button again while the row is up moves to the next app. Hold OK on an app to take it off the row, or to open its
 * App info page, where Settings has Force stop (no app may stop another itself).
 */
public final class SwitcherActivity extends Activity {
    /** How many apps the row shows, and how far back the usage events go. */
    private static final int MAX = 5;
    private static final long WINDOW_MS = 3L * 24 * 60 * 60 * 1000;
    /** Apps taken off the row: package to when (System.currentTimeMillis, the events' clock). */
    private static final String REMOVED = "switcher_removed";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout row;
    private TextView hint;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_switcher);
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        row = findViewById(R.id.row);
        hint = findViewById(R.id.hint);
        if (!usageAllowed(this)) {
            hint.setText(R.string.switcher_no_usage);
            hint.setVisibility(View.VISIBLE);
            return;
        }
        Context app = getApplicationContext();
        new Thread(() -> {
            List<Tile> tiles = load(app);
            handler.post(() -> show(tiles));
        }, "switcher").start();
    }

    /** One app in the row. */
    private static final class Tile {
        final String pkg;
        final CharSequence label;
        final Drawable banner, icon;
        final boolean onScreen, focus;

        Tile(String pkg, CharSequence label, Drawable banner, Drawable icon, boolean onScreen, boolean focus) {
            this.pkg = pkg;
            this.label = label;
            this.banner = banner;
            this.icon = icon;
            this.onScreen = onScreen;
            this.focus = focus;
        }
    }

    /** The recent apps with their art; on a background thread. */
    private static List<Tile> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Set<String> launchable = new HashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                launchable.add(ri.activityInfo.packageName);
            }
        }
        // The screensaver comes to the front like an app, but nobody switches back to it.
        launchable.removeAll(screensavers(context));
        List<String> pkgs = new ArrayList<>(), classes = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        UsageStatsManager usm = context.getSystemService(UsageStatsManager.class);
        long now = System.currentTimeMillis();
        UsageEvents events = usm == null ? null : usm.queryEvents(now - WINDOW_MS, now);
        UsageEvents.Event e = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() != UsageEvents.Event.MOVE_TO_FOREGROUND) continue;   // ACTIVITY_RESUMED
            pkgs.add(e.getPackageName());
            classes.add(e.getClassName());
            times.add(e.getTimeStamp());
        }
        long[] t = new long[times.size()];
        for (int i = 0; i < t.length; i++) t[i] = times.get(i);
        Map<String, Long> removed = new HashMap<>();
        for (Map.Entry<String, ?> en : context.getSharedPreferences(REMOVED, MODE_PRIVATE).getAll().entrySet()) {
            if (en.getValue() instanceof Long) removed.put(en.getKey(), (Long) en.getValue());
        }
        Recents.Result r = Recents.from(pkgs.toArray(new String[0]), classes.toArray(new String[0]), t,
                launchable, context.getPackageName(), SwitcherActivity.class.getName(), MAX, removed);
        List<Tile> tiles = new ArrayList<>();
        for (int i = 0; i < r.apps.size(); i++) {
            String pkg = r.apps.get(i);
            Intent open = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (open == null) open = pm.getLaunchIntentForPackage(pkg);
            ResolveInfo ri = open == null ? null : pm.resolveActivity(open, 0);
            if (ri == null) continue;
            Drawable banner = banner(context, pm, ri.activityInfo);
            tiles.add(new Tile(pkg, ri.loadLabel(pm), banner, banner == null ? ri.loadIcon(pm) : null,
                    i == 0 && r.firstOnScreen, i == r.focus()));
        }
        return tiles;
    }

    /** The packages of the screensavers chosen in the TV's settings ("pkg/class" entries, comma-separated). */
    private static Set<String> screensavers(Context context) {
        Set<String> out = new HashSet<>();
        String chosen = android.provider.Settings.Secure.getString(context.getContentResolver(), "screensaver_components");
        if (chosen == null) return out;
        for (String c : chosen.split(",")) {
            int slash = c.indexOf('/');
            if (slash > 0) out.add(c.substring(0, slash).trim());
        }
        return out;
    }

    /** The app's TV banner at xhdpi or better, or null for none (or a square one), as the dock loads it. */
    private static Drawable banner(Context context, PackageManager pm, ActivityInfo ai) {
        int id = ai.banner != 0 ? ai.banner : ai.applicationInfo.banner;
        Drawable banner = null;
        if (id != 0) {
            try {
                Resources res = pm.getResourcesForApplication(ai.applicationInfo);
                int density = Math.max(DisplayMetrics.DENSITY_XHIGH, context.getResources().getDisplayMetrics().densityDpi);
                banner = res.getDrawableForDensity(id, density, null);
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
            }
        }
        if (banner == null) banner = ai.loadBanner(pm);
        return banner != null && AppAdapter.isBannerShaped(banner.getIntrinsicWidth(), banner.getIntrinsicHeight())
                ? banner : null;
    }

    private void show(List<Tile> tiles) {
        if (isFinishing() || isDestroyed()) return;
        if (tiles.isEmpty()) {
            hint.setText(R.string.switcher_empty);
            hint.setVisibility(View.VISIBLE);
            return;
        }
        LayoutInflater inflater = getLayoutInflater();
        View focus = null;
        for (Tile t : tiles) {
            View item = inflater.inflate(R.layout.item_switcher, row, false);
            View card = item.findViewById(R.id.card);
            card.setClipToOutline(true);   // the banner inside the tile's round corners
            ImageView art = item.findViewById(R.id.banner), icon = item.findViewById(R.id.icon);
            if (t.banner != null) {
                art.setImageDrawable(t.banner);
            } else {
                icon.setImageDrawable(t.icon);
                icon.setVisibility(View.VISIBLE);
            }
            ((TextView) item.findViewById(R.id.label)).setText(t.label);
            TextView status = item.findViewById(R.id.status);
            if (t.onScreen) status.setText(R.string.switcher_on_screen);
            else status.setVisibility(View.INVISIBLE);
            card.setContentDescription(t.label);
            card.setOnFocusChangeListener((v, has) ->
                    v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(150).start());
            card.setOnClickListener(v -> open(t));
            card.setOnLongClickListener(v -> {
                menu(t, item, card);
                return true;
            });
            row.addView(item);
            if (t.focus) focus = card;
        }
        if (focus != null) focus.requestFocus();
    }

    private void open(Tile t) {
        // The app on screen is already where the user is: closing the row is going back to it.
        if (!t.onScreen) RemoteKeys.launch(this, t.pkg);
        finish();
        overridePendingTransition(0, 0);
    }

    /** Hold OK on a tile: take it off the row, or its App info page in Settings, where Force stop is. */
    private void menu(Tile t, View item, View card) {
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, row, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        float d = getResources().getDisplayMetrics().density;
        PopupWindow popup = new PopupWindow(panel, Math.round(230 * d), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        menuRow(rows, R.drawable.ic_hide, R.string.switcher_remove, 0, () -> {
            popup.dismiss();
            remove(t, item);
        });
        Intent info = SystemSettings.appInfo(this, t.pkg);
        if (info != null) menuRow(rows, R.drawable.ic_settings, R.string.switcher_force_stop, R.string.switcher_force_stop_hint, () -> {
            popup.dismiss();
            try {
                startActivity(info.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        card.getLocationInWindow(at);
        int x = at[0] + (card.getWidth() - popup.getWidth()) / 2;
        int y = at[1] - panel.getMeasuredHeight() - Math.round(12 * d);   // above the tile: the row sits low
        popup.showAtLocation(row, Gravity.NO_GRAVITY, Math.max(0, x), Math.max(0, y));
        rows.getChildAt(0).requestFocus();
    }

    private void menuRow(LinearLayout rows, int icon, int title, int status, Runnable action) {
        View r = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) r.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) r.findViewById(R.id.title)).setText(title);
        r.findViewById(R.id.dot).setVisibility(View.GONE);
        TextView hintView = r.findViewById(R.id.status);
        if (status != 0) hintView.setText(status);
        else hintView.setVisibility(View.GONE);
        r.setOnClickListener(v -> action.run());
        rows.addView(r);
    }

    /** Off the row until the app is in front again; the focus moves to a neighbour. */
    private void remove(Tile t, View item) {
        getSharedPreferences(REMOVED, MODE_PRIVATE).edit().putLong(t.pkg, System.currentTimeMillis()).apply();
        int at = row.indexOfChild(item);
        row.removeView(item);
        int n = row.getChildCount();
        if (n == 0) {
            hint.setText(R.string.switcher_empty);
            hint.setVisibility(View.VISIBLE);
            return;
        }
        row.getChildAt(Math.min(at, n - 1)).findViewById(R.id.card).requestFocus();
    }

    /** The button pressed again while the row is up: the next app, round to the first. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        int n = row.getChildCount();
        if (n == 0) return;
        int at = 0;
        for (int i = 0; i < n; i++) {
            if (((ViewGroup) row.getChildAt(i)).findViewById(R.id.card).hasFocus()) at = i;
        }
        row.getChildAt((at + 1) % n).findViewById(R.id.card).requestFocus();
    }

    /** Anything else taking the screen closes the row. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    @SuppressWarnings("deprecation")
    static boolean usageAllowed(Context context) {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
                context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }
}
