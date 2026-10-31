package com.gathin.tvhome.backlight;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The light on the TV: power, Automatic, album colours, Sync's diffusion, the scenes, Calm's whites and colours, and
 * the brightness, in the dashboard's glass. A glow behind the panel takes the light's colour. Whatever is picked
 * here (except Sync) becomes the look used away from the sync input.
 */
public class BacklightActivity extends Activity implements Box.Listener, SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int FOCUS_TEXT = 0xFF101418, TEXT = 0xFFFFFFFF, DIM = 0x99FFFFFF;

    private Box box;
    private float density;
    private View glow;
    private int glowColor = 0xFF20242A;
    private ValueAnimator glowAnim;
    private TextView now, away, status;
    private TextView power, auto, album;
    private final List<TextView> syncChips = new ArrayList<>(), sceneChips = new ArrayList<>();
    private final List<Swatch> temps = new ArrayList<>(), hues = new ArrayList<>();
    private Bar bar;
    private View firstFocus;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        box = Box.get(this);
        density = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0D10);
        glow = new View(this);
        root.addView(glow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        paintGlow(glowColor);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x2EFFFFFF, 0x14FFFFFF});
        glass.setCornerRadius(dp(28));
        glass.setStroke(dp(1), 0x33FFFFFF);
        panel.setBackground(glass);
        panel.setPadding(dp(36), dp(22), dp(36), dp(18));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        pp.setMargins(dp(56), dp(24), dp(56), dp(24));
        root.addView(panel, pp);

        // Title, what is on now, and the three switches.
        LinearLayout head = row();
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(getString(R.string.app_name), 28, TEXT, true));
        now = text("", 15, DIM, false);
        titles.addView(now);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        power = chip("", v -> box.power(!box.state().on));
        auto = chip("", v -> box.prefs().edit().putBoolean(Box.AUTO, !box.prefs().getBoolean(Box.AUTO, true)).apply());
        album = chip("", v -> box.setAlbum(!box.albumOn()));
        head.addView(power);
        head.addView(auto);
        head.addView(album);
        panel.addView(head);

        // Sync and its diffusion.
        panel.addView(label(getString(R.string.section_sync)));
        LinearLayout sync = row();
        String[] levels = getResources().getStringArray(R.array.sync_levels);
        for (int i = 0; i < levels.length; i++) {
            final int level = i;
            TextView c = chip(levels[i], v -> box.setSyncLevel(level));
            syncChips.add(c);
            sync.addView(c);
        }
        TextView syncHint = text(getString(R.string.sync_hint, BuildConfig.SYNC_INPUTS), 13, DIM, false);
        syncHint.setPadding(dp(12), 0, 0, 0);
        sync.addView(syncHint);
        panel.addView(sync);

        // Scenes: Calm first (the whites below), then the box's own.
        panel.addView(label(getString(R.string.section_scenes)));
        LinearLayout scenes = row();
        TextView calm = chip(getString(R.string.calm), v -> pickCalm(Math.max(0, box.state().tempIndex())));
        sceneChips.add(calm);
        scenes.addView(calm);
        for (int i = 0; i < Looks.SCENE_NAMES.length; i++) {
            final int code = Looks.SCENE_CODES[i];
            TextView c = chip(Looks.SCENE_NAMES[i], v -> box.pick(Looks.scene(code), false));
            sceneChips.add(c);
            scenes.addView(c);
        }
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.addView(scenes);
        panel.addView(scroll);

        // Calm's whites, then colours.
        panel.addView(label(getString(R.string.section_colours)));
        LinearLayout colours = row();
        String[] tempNames = getResources().getStringArray(R.array.temps);
        for (int i = 0; i < Looks.TEMPS.length; i++) {
            final int t = i;
            Swatch s = swatch(Ui.white(Looks.TEMPS[i]), tempNames[i], v -> pickCalm(t));
            temps.add(s);
            colours.addView(s);
        }
        View gap = new View(this);
        colours.addView(gap, new LinearLayout.LayoutParams(dp(20), 1));
        String[] hueNames = getResources().getStringArray(R.array.hues);
        for (int i = 0; i < Looks.HUES.length; i++) {
            final int h = Looks.HUES[i];
            Swatch s = swatch(Ui.hue(h, 1000), hueNames[i], v -> box.pick(Looks.colour(h, 1000, box.state().bright), false));
            hues.add(s);
            colours.addView(s);
        }
        panel.addView(colours);

        // Brightness.
        panel.addView(label(getString(R.string.section_brightness)));
        bar = new Bar();
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        bp.setMargins(0, dp(2), 0, 0);
        panel.addView(bar, bp);

        away = text("", 13, DIM, false);
        away.setPadding(0, dp(12), 0, dp(2));
        panel.addView(away);
        status = text("", 13, 0x66FFFFFF, false);
        panel.addView(status);

        setContentView(root);
        firstFocus = power;
    }

    private void pickCalm(int index) {
        box.pick(Looks.calm(Looks.TEMPS[index]), false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        box.addListener(this);
        box.prefs().registerOnSharedPreferenceChangeListener(this);
        box.refresh();
        changed(box.state());
        focusCurrent();
    }

    @Override
    protected void onPause() {
        box.removeListener(this);
        box.prefs().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        runOnUiThread(() -> changed(box.state()));
    }

    /** The box's state changed (read back, or something was sent): the selections, the texts and the glow. */
    @Override
    public void changed(Looks.State s) {
        SharedPreferences p = box.prefs();
        power.setText(getString(s.on ? R.string.power_chip_on : R.string.power_chip_off));
        power.setSelected(s.on);
        boolean autoOn = p.getBoolean(Box.AUTO, true);
        auto.setText(getString(autoOn ? R.string.auto_on : R.string.auto_off));
        auto.setSelected(autoOn);
        album.setText(getString(box.albumOn() ? R.string.album_on : R.string.album_off));
        album.setSelected(box.albumOn());
        int level = s.syncLevel();
        for (int i = 0; i < syncChips.size(); i++) syncChips.get(i).setSelected(i == level);
        int scene = s.sceneIndex();
        boolean calm = s.tempIndex() >= 0;
        for (int i = 0; i < sceneChips.size(); i++) sceneChips.get(i).setSelected(i == 0 ? calm : i - 1 == scene);
        int t = s.tempIndex(), h = s.hueIndex();
        for (int i = 0; i < temps.size(); i++) temps.get(i).setSelected(i == t);
        for (int i = 0; i < hues.size(); i++) hues.get(i).setSelected(i == h);
        bar.setValue(s.bright);

        now.setText(!s.on ? getString(R.string.power_off) : box.albumRunning() ? getString(R.string.album_now) : Ui.lookName(this, s));
        Looks.State other = new Looks.State();
        other.apply(box.otherLook());
        away.setText(getString(R.string.away_look, BuildConfig.SYNC_INPUTS, Ui.lookName(this, other)));
        status.setText(box.configured() ? p.getString(Box.STATUS, "") : getString(R.string.status_no_key));
        animateGlow(Ui.glow(s));
    }

    private void focusCurrent() {
        View target = firstFocus;
        for (TextView c : syncChips) if (c.isSelected()) target = c;
        for (TextView c : sceneChips) if (c.isSelected()) target = c;
        for (Swatch c : hues) if (c.isSelected()) target = c;
        target.requestFocus();
    }

    // ---- The glow ----

    private void animateGlow(int to) {
        if (to == glowColor) return;
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, to);
        glowAnim.setDuration(600);
        glowAnim.addUpdateListener(a -> {
            glowColor = (int) a.getAnimatedValue();
            paintGlow(glowColor);
        });
        glowAnim.start();
    }

    private void paintGlow(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.75f);
        g.setGradientCenter(0.5f, 1.05f);
        g.setColors(new int[]{(color & 0x00FFFFFF) | 0x66000000, (color & 0x00FFFFFF) | 0x1A000000, 0x00000000});
        glow.setBackground(g);
    }

    // ---- Views ----

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        r.setPadding(0, dp(4), 0, dp(4));
        return r;
    }

    private TextView text(String s, int sp, int color, boolean heading) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Ui.font(this, heading));
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 13, DIM, false);
        t.setPadding(dp(2), dp(12), 0, dp(3));
        return t;
    }

    /** The All apps chip: white while focused, a light pill when it is what's on, plain otherwise. */
    private TextView chip(String label, View.OnClickListener l) {
        TextView c = text(label, 15, TEXT, false);
        c.setFocusable(true);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(16), dp(7), dp(16), dp(7));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, pill(0xFFFFFFFF, 0));
        bg.addState(new int[]{android.R.attr.state_selected}, pill(0x40FFFFFF, 0));
        bg.addState(new int[]{}, pill(0x12FFFFFF, 0x1FFFFFFF));
        c.setBackground(bg);
        c.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{FOCUS_TEXT, TEXT}));
        c.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(10), 0);
        c.setLayoutParams(lp);
        return c;
    }

    private GradientDrawable pill(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(18));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private Swatch swatch(int color, String name, View.OnClickListener l) {
        Swatch s = new Swatch(color);
        s.setContentDescription(name);
        s.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
        lp.setMargins(dp(4), dp(4), dp(10), dp(4));
        s.setLayoutParams(lp);
        return s;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    /** A round colour: a white ring and a little larger while focused, a thin ring when it is what's on. */
    private final class Swatch extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);

        Swatch(int color) {
            super(BacklightActivity.this);
            fill.setColor(color);
            ring.setStyle(Paint.Style.STROKE);
            setFocusable(true);
            setOnFocusChangeListener((v, has) -> animate().scaleX(has ? 1.18f : 1f).scaleY(has ? 1.18f : 1f).setDuration(120).start());
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy);
            boolean focused = isFocused(), on = isSelected();
            float inset = focused || on ? dp(5) : dp(2);
            c.drawCircle(cx, cy, r - inset, fill);
            if (focused || on) {
                ring.setStrokeWidth(focused ? dp(3) : dp(2));
                ring.setColor(focused ? 0xFFFFFFFF : 0xB3FFFFFF);
                c.drawCircle(cx, cy, r - ring.getStrokeWidth() / 2, ring);
            }
        }

        @Override
        public void setSelected(boolean selected) {
            super.setSelected(selected);
            invalidate();
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect previous) {
            super.onFocusChanged(gained, direction, previous);
            invalidate();
        }
    }

    /** The brightness: ◀ ▶ move it 5 % at a time; held, it keeps moving. Sent a moment after the last step. */
    private final class Bar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG),
                knob = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG),
                outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler h = new Handler(Looper.getMainLooper());
        private final RectF r = new RectF();
        private int value = Looks.BRIGHT_MAX;
        private boolean dragging;
        private final Runnable push = () -> box.brightness(value);

        Bar() {
            super(BacklightActivity.this);
            setFocusable(true);
            track.setColor(0x26FFFFFF);
            fillPaint.setColor(0xD9FFFFFF);
            knob.setColor(0xFFFFFFFF);
            label.setColor(0xFFFFFFFF);
            label.setTypeface(Ui.font(BacklightActivity.this, true));
            label.setTextSize(dp(16));
            label.setTextAlign(Paint.Align.RIGHT);
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(dp(2));
            outline.setColor(0xFFFFFFFF);
        }

        void setValue(int v) {
            if (dragging) return;          // the box's report of an older value must not pull the bar back
            value = v;
            invalidate();
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent e) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int step = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 50 : -50;
                value = Looks.clamp(((value + step) / 50) * 50, Looks.BRIGHT_MIN, Looks.BRIGHT_MAX);
                dragging = true;
                invalidate();
                h.removeCallbacks(push);
                h.postDelayed(push, 150);
                h.removeCallbacks(release);
                h.postDelayed(release, 1500);
                return true;
            }
            return super.onKeyDown(keyCode, e);
        }

        private final Runnable release = () -> dragging = false;

        @Override
        protected void onDraw(Canvas c) {
            float textW = dp(64), hgt = dp(10), cy = getHeight() / 2f;
            float left = dp(12), right = getWidth() - textW - dp(16);
            r.set(left, cy - hgt / 2, right, cy + hgt / 2);
            c.drawRoundRect(r, hgt / 2, hgt / 2, track);
            float x = left + (right - left) * (value - Looks.BRIGHT_MIN) / (float) (Looks.BRIGHT_MAX - Looks.BRIGHT_MIN);
            r.set(left, cy - hgt / 2, x, cy + hgt / 2);
            c.drawRoundRect(r, hgt / 2, hgt / 2, fillPaint);
            c.drawCircle(x, cy, isFocused() ? dp(13) : dp(9), knob);
            if (isFocused()) {
                r.set(dp(1), dp(1), getWidth() - textW - dp(4), getHeight() - dp(1));
                c.drawRoundRect(r, getHeight() / 2f, getHeight() / 2f, outline);
            }
            c.drawText(Math.round(value / 10f) + "%", getWidth() - dp(4), cy + dp(6), label);
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect previous) {
            super.onFocusChanged(gained, direction, previous);
            invalidate();
        }
    }
}
