package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Dashboard (config dashboard): a row of cards between the top bar and the dock (dashboard_cards.xml). Now playing
 * shows the media session NowPlaying reports, or "Nothing playing" with OK opening the last music app; OK on it
 * reaches previous / play-pause / next and Up, Down or Back leaves them. System shows the download speed over the
 * last minute, the Wi-Fi band, uptime, memory and storage. Screen time adds up the TV apps' foreground time from
 * usage events (today, the week, against last week, the top three apps). Ticks once a second only while Home is on
 * screen; Screen time reads its events off the main thread once a minute.
 */
final class Dashboard {

    /** Where focus goes when it leaves the row. */
    interface Host {
        void focusTopBar();

        void focusDock();

        /** The focused view is below the dock (Discover, the app rows): the cards fold away. */
        boolean belowDock(View focused);

        /** The cards finished folding or unfolding: what sits on the grid's tiles must be placed again. */
        void onFolded();
    }

    private final Activity activity;
    private final Host host;
    private final float density;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final View row, now, system, screen, controls, times;
    private final TextView screenTotal, screenTrend;
    private final ViewGroup screenApps;
    private final BarsView bars;
    private final TextView chip;
    private final EqualizerDrawable chipBars;
    private android.animation.ValueAnimator fold;
    private boolean collapsed;
    private final View[] cards;
    private final View[] buttons;
    private final ImageView art;
    private final TextView app, title, artist, pos, dur, play, up, net, rate, mem, disk;
    private final ProgressBar progress, memBar, diskBar;
    private final SparklineView spark;
    private final SharedPreferences prefs;
    private NowPlaying nowPlaying;
    private NowPlaying.Track track;
    private View lastCard;
    private long rxPrev = -1, rxAt;
    private int ticks;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            updateProgress();
            sampleNetwork();
            if (ticks % 5 == 0) updateSystem();
            if (ticks % 60 == 0) loadScreenTime();
            ticks++;
            handler.postDelayed(this, 1000);
        }
    };

    private Dashboard(Activity activity, View row, TextView chip, Host host) {
        this.activity = activity;
        this.row = row;
        this.chip = chip;
        this.host = host;
        density = activity.getResources().getDisplayMetrics().density;
        prefs = activity.getSharedPreferences("dashboard", Context.MODE_PRIVATE);
        now = row.findViewById(R.id.dashNow);
        system = row.findViewById(R.id.dashSystem);
        controls = row.findViewById(R.id.dashControls);
        times = row.findViewById(R.id.dashTimes);
        screen = row.findViewById(R.id.dashScreen);
        screenTotal = row.findViewById(R.id.dashScreenTotal);
        screenTrend = row.findViewById(R.id.dashScreenTrend);
        screenApps = row.findViewById(R.id.dashScreenApps);
        bars = row.findViewById(R.id.dashBars);
        cards = new View[]{now, system, screen};
        art = row.findViewById(R.id.dashArt);
        app = row.findViewById(R.id.dashApp);
        title = row.findViewById(R.id.dashTitle);
        artist = row.findViewById(R.id.dashArtist);
        pos = row.findViewById(R.id.dashPos);
        dur = row.findViewById(R.id.dashDur);
        play = row.findViewById(R.id.dashPlay);
        up = row.findViewById(R.id.dashUp);
        net = row.findViewById(R.id.dashNet);
        rate = row.findViewById(R.id.dashRate);
        mem = row.findViewById(R.id.dashMem);
        disk = row.findViewById(R.id.dashDisk);
        progress = row.findViewById(R.id.dashProgress);
        memBar = row.findViewById(R.id.dashMemBar);
        diskBar = row.findViewById(R.id.dashDiskBar);
        spark = row.findViewById(R.id.dashSpark);
        buttons = new View[]{row.findViewById(R.id.dashPrev), play, row.findViewById(R.id.dashNext)};
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 14 * density);
            }
        });
        art.setClipToOutline(true);
        chipBars = new EqualizerDrawable(Math.round(18 * density));
        chip.setCompoundDrawablesRelativeWithIntrinsicBounds(chipBars, null, null, null);
        setUpKeys();
        activity.getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus != null) setCollapsed(host.belowDock(newFocus));
        });
        showTrack(null, null);
    }

    /** Puts the row above the grid (between the top bar and the dock). */
    static Dashboard attach(Activity activity, View grid, Host host) {
        View gridFrame = (View) grid.getParent();
        ViewGroup column = (ViewGroup) gridFrame.getParent();
        View row = LayoutInflater.from(activity).inflate(R.layout.dashboard_cards, column, false);
        column.addView(row, column.indexOfChild(gridFrame));
        // The top bar's chip for the playing track while the cards are folded away: not focusable, it only shows.
        ViewGroup buttons = activity.findViewById(R.id.topButtons);
        TextView chip = new TextView(activity, null, 0, R.style.TopButtonBase);
        float d = activity.getResources().getDisplayMetrics().density;
        chip.setFocusable(false);
        chip.setClickable(false);
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setMaxWidth(Math.round(250 * d));
        chip.setVisibility(View.GONE);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(42 * d));
        lp.setMarginStart(Math.round(10 * d));
        buttons.addView(chip, 0, lp);
        return new Dashboard(activity, row, chip, host);
    }

    /** Focus on the card last used (Now playing the first time). False if the row cannot take it. */
    boolean focus() {
        View target = lastCard != null ? lastCard : now;
        return target.requestFocus();
    }

    void start() {
        if (running) return;
        running = true;
        ticks = 0;
        rxPrev = -1;
        handler.post(tick);
    }

    void stop() {
        running = false;
        chipBars.stop();
        handler.removeCallbacks(tick);
        leaveControls();
    }

    // ---- Now playing -----------------------------------------------------------------------------

    void showTrack(NowPlaying.Track t, NowPlaying source) {
        track = t;
        nowPlaying = source;
        boolean has = t != null;
        times.setVisibility(has ? View.VISIBLE : View.GONE);
        progress.setVisibility(has ? View.VISIBLE : View.GONE);
        controls.setVisibility(has ? View.VISIBLE : View.GONE);
        if (!has) {
            leaveControls();
            showChip();
            String last = prefs.getString("app", "com.spotify.tv.android");
            app.setText(label(last));
            title.setText(R.string.dash_nothing);
            artist.setText(activity.getString(R.string.dash_ok_opens, label(last)));
            art.setImageDrawable(icon(last));
            return;
        }
        prefs.edit().putString("app", t.pkg).apply();
        chip.setText(t.artist.isEmpty() ? t.title : t.title + "  ·  " + t.artist);
        app.setText(t.app);
        title.setText(t.title);
        artist.setText(t.artist);
        if (t.art != null) art.setImageBitmap(t.art);
        else art.setImageDrawable(icon(t.pkg));
        play.setCompoundDrawablesRelativeWithIntrinsicBounds(t.playing ? R.drawable.ic_pause : R.drawable.ic_play, 0, 0, 0);
        updateProgress();
        showChip();
    }

    /**
     * Focus below the dock folds the cards away (the grid below gets their room) and shows the playing track as a
     * chip in the top bar; focus back on the dock, the cards or the top bar unfolds them.
     */
    private void setCollapsed(boolean collapse) {
        if (collapse == collapsed) return;
        collapsed = collapse;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) row.getLayoutParams();
        int full = Math.round(166 * density), margin = Math.round(14 * density);
        if (fold != null) fold.cancel();
        float from = lp.height / (float) full, to = collapse ? 0f : 1f;
        fold = android.animation.ValueAnimator.ofFloat(from, to);
        fold.setDuration(220);
        fold.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            lp.height = Math.round(full * f);
            lp.topMargin = Math.round(margin * f);
            row.setAlpha(f);
            row.setLayoutParams(lp);
        });
        fold.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                host.onFolded();
            }
        });
        fold.start();
        showChip();
    }

    private void showChip() {
        boolean show = collapsed && track != null;
        chip.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && track.playing) chipBars.start();
        else chipBars.stop();
    }

    private void updateProgress() {
        if (track == null) return;
        long nowMs = SystemClock.elapsedRealtime();
        float f = track.progress(nowMs);
        progress.setVisibility(f < 0 ? View.INVISIBLE : View.VISIBLE);
        if (f >= 0) progress.setProgress(Math.round(f * 1000));
        pos.setText(f < 0 ? "" : DashboardText.clock((long) (f * track.duration)));
        dur.setText(track.duration > 0 ? DashboardText.clock(track.duration) : "");
    }

    private void openMusic() {
        if (track != null && nowPlaying != null) {
            nowPlaying.open(activity);
            return;
        }
        PackageManager pm = activity.getPackageManager();
        String pkg = prefs.getString("app", "com.spotify.tv.android");
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch != null) activity.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private CharSequence label(String pkg) {
        PackageManager pm = activity.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private android.graphics.drawable.Drawable icon(String pkg) {
        try {
            return activity.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    // ---- System ----------------------------------------------------------------------------------

    private void sampleNetwork() {
        long rx = TrafficStats.getTotalRxBytes(), at = SystemClock.elapsedRealtime();
        if (rx == TrafficStats.UNSUPPORTED) return;
        if (rxPrev >= 0 && at > rxAt) {
            double perSecond = (rx - rxPrev) * 1000.0 / (at - rxAt);
            rate.setText(DashboardText.rate(perSecond));
            spark.add((float) perSecond);
        }
        rxPrev = rx;
        rxAt = at;
    }

    private void updateSystem() {
        up.setText(activity.getString(R.string.dash_up, DashboardText.uptime(SystemClock.elapsedRealtime())));
        net.setText(networkLabel());
        ActivityManager am = activity.getSystemService(ActivityManager.class);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long used = mi.totalMem - mi.availMem;
            mem.setText(DashboardText.usedOfTotal(used, mi.totalMem));
            memBar.setProgress(DashboardText.permille(used, mi.totalMem));
        }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes(), used = total - fs.getAvailableBytes();
            disk.setText(DashboardText.usedOfTotal(used, total));
            diskBar.setProgress(DashboardText.permille(used, total));
        } catch (IllegalArgumentException ignored) {
        }
    }

    /** "Download · 5 GHz", "Download · Ethernet", or plain "Download". */
    @SuppressWarnings("deprecation")
    private String networkLabel() {
        String label = activity.getString(R.string.dash_download);
        String kind = "";
        ConnectivityManager cm = activity.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            kind = activity.getString(R.string.dash_ethernet);
        } else if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiManager wm = activity.getApplicationContext().getSystemService(WifiManager.class);
            if (wm != null && wm.getConnectionInfo() != null) kind = DashboardText.band(wm.getConnectionInfo().getFrequency());
        }
        return kind.isEmpty() ? label : label + " · " + kind;
    }

    // ---- Screen time ----------------------------------------------------------------------------

    private static final int DAYS = 7;
    private static final int[] DOTS = {0xFFF87171, 0xFFFBBF24, 0xFF60A5FA};

    @SuppressWarnings("deprecation")
    private boolean usageAllowed() {
        android.app.AppOpsManager ops = activity.getSystemService(android.app.AppOpsManager.class);
        return ops != null && ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), activity.getPackageName()) == android.app.AppOpsManager.MODE_ALLOWED;
    }

    private void loadScreenTime() {
        if (!usageAllowed()) {
            screenTotal.setText("–");
            screenTrend.setText(R.string.dash_usage_off);
            screenApps.removeAllViews();
            TextView hint = new TextView(activity, null, 0, R.style.DashSmall);
            hint.setTextSize(12);
            hint.setText(R.string.dash_usage_hint);
            screenApps.addView(hint);
            return;
        }
        Context app = activity.getApplicationContext();
        new Thread(() -> {
            ScreenTime.Tally tally = readUsage(app);
            if (tally != null) handler.post(() -> showScreenTime(tally));
        }, "screen-time").start();
    }

    /** The usage events of the last seven days, added up; on a background thread. */
    private static ScreenTime.Tally readUsage(Context context) {
        android.app.usage.UsageStatsManager usm = context.getSystemService(android.app.usage.UsageStatsManager.class);
        if (usm == null) return null;
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalDate today = java.time.LocalDate.now(zone);
        long[] dayStarts = new long[DAYS];
        for (int i = 0; i < DAYS; i++) {
            dayStarts[i] = today.minusDays(DAYS - 1 - i).atStartOfDay(zone).toInstant().toEpochMilli();
        }
        long now = System.currentTimeMillis();
        // The TV's apps: anything with a TV (or phone) launcher entry, but not this launcher.
        java.util.Set<String> counted = new java.util.HashSet<>();
        PackageManager pm = context.getPackageManager();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(probe, 0)) counted.add(ri.activityInfo.packageName);
        }
        counted.remove(context.getPackageName());
        java.util.List<String> pkgs = new java.util.ArrayList<>();
        java.util.List<Integer> kinds = new java.util.ArrayList<>();
        java.util.List<Long> times = new java.util.ArrayList<>();
        android.app.usage.UsageEvents events = usm.queryEvents(dayStarts[0], now);
        android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(e);
            int kind;
            switch (e.getEventType()) {
                case 1:           // ACTIVITY_RESUMED (MOVE_TO_FOREGROUND)
                    kind = ScreenTime.START;
                    break;
                case 2:           // ACTIVITY_PAUSED
                case 23:          // ACTIVITY_STOPPED (Android 10)
                    kind = ScreenTime.STOP;
                    break;
                case 16:          // SCREEN_NON_INTERACTIVE
                case 26:          // DEVICE_SHUTDOWN
                    kind = ScreenTime.ALL_STOP;
                    break;
                default:
                    continue;
            }
            pkgs.add(e.getPackageName());
            kinds.add(kind);
            times.add(e.getTimeStamp());
        }
        int n = kinds.size();
        int[] k = new int[n];
        long[] t = new long[n];
        for (int i = 0; i < n; i++) {
            k[i] = kinds.get(i);
            t[i] = times.get(i);
        }
        return ScreenTime.tally(pkgs.toArray(new String[0]), k, t, dayStarts, now, counted);
    }

    private void showScreenTime(ScreenTime.Tally tally) {
        screenTotal.setText(DashboardText.uptime(tally.days[DAYS - 1]));
        bars.setValues(tally.days);
        Integer change = ScreenTime.versusLastWeek(tally.days);
        screenTrend.setText(change == null ? "" : activity.getString(R.string.dash_vs_week,
                (change <= 0 ? "↓ " : "↑ ") + Math.abs(change) + "%"));
        screenTrend.setTextColor(change != null && change <= 0 ? 0xFF86EFAC : 0xFFFCD34D);
        screenApps.removeAllViews();
        int i = 0;
        for (java.util.Map.Entry<String, Long> entry : ScreenTime.top(tally.today, 3)) {
            if (entry.getValue() < 60_000) break;      // "0m" says nothing
            android.widget.LinearLayout line = new android.widget.LinearLayout(activity);
            line.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView name = new TextView(activity, null, 0, R.style.DashSmall);
            name.setTextSize(12);
            name.setText(label(entry.getKey()));
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(DOTS[i++ % DOTS.length]);
            int size = Math.round(6 * density);
            dot.setSize(size, size);
            name.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
            name.setCompoundDrawablePadding(Math.round(7 * density));
            TextView minutes = new TextView(activity, null, 0, R.style.DashMono);
            minutes.setText(DashboardText.uptime(entry.getValue()));
            line.addView(name, new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            line.addView(minutes);
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(3 * density);
            screenApps.addView(line, lp);
        }
    }

    // ---- keys ------------------------------------------------------------------------------------

    private void setUpKeys() {
        now.setOnClickListener(v -> {
            if (track == null) openMusic();
            else enterControls();
        });
        now.setOnLongClickListener(v -> {
            openMusic();
            return true;
        });
        system.setOnClickListener(v -> {
            Intent storage = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                activity.startActivity(storage);
            } catch (android.content.ActivityNotFoundException e) {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });
        screen.setOnClickListener(v -> {
            if (!usageAllowed()) loadScreenTime();
        });
        boolean rtl = activity.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        for (int i = 0; i < cards.length; i++) {
            final int index = i;
            cards[i].setOnFocusChangeListener((v, has) -> {
                if (has) lastCard = v;
                v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(150).start();
            });
            cards[i].setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        host.focusTopBar();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        host.focusDock();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        int step = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != rtl ? 1 : -1;
                        int to = index + step;
                        if (to >= 0 && to < cards.length) cards[to].requestFocus();
                        return true;     // the row's ends: stay
                    default:
                        return false;
                }
            });
        }
        buttons[0].setOnClickListener(v -> {
            if (nowPlaying != null) nowPlaying.previous();
        });
        play.setOnClickListener(v -> {
            if (nowPlaying != null) nowPlaying.playPause();
        });
        buttons[2].setOnClickListener(v -> {
            if (nowPlaying != null) nowPlaying.next();
        });
        for (int i = 0; i < buttons.length; i++) {
            final int index = i;
            buttons[i].setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                    case KeyEvent.KEYCODE_BACK:
                        now.requestFocus();   // before the buttons stop taking focus, so it lands on the card
                        leaveControls();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        int step = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != rtl ? 1 : -1;
                        int to = index + step;
                        if (to >= 0 && to < buttons.length) buttons[to].requestFocus();
                        return true;
                    default:
                        return false;
                }
            });
        }
    }

    /** OK on the Now playing card: its buttons take focus, starting on play/pause. */
    private void enterControls() {
        for (View b : buttons) b.setFocusable(true);
        play.requestFocus();
    }

    private void leaveControls() {
        for (View b : buttons) b.setFocusable(false);
    }
}
