package com.gathin.tvhome.backlight;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Status and the switch: automatic on or off, and Sync or Scene now to test the box. */
public class BacklightActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private Box box;
    private TextView status;
    private Button auto;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        box = Box.get(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundColor(0xFF101418);
        int pad = dp(48);
        col.setPadding(pad, pad, pad, pad);

        TextView title = text(getString(R.string.app_name), 34, Color.WHITE);
        TextView rule = text(getString(R.string.rule, BuildConfig.SYNC_INPUTS), 18, 0xFFB8C2CC);
        status = text("", 18, 0xFFE8EDF2);
        status.setPadding(0, dp(24), 0, dp(24));
        col.addView(title);
        col.addView(rule);
        col.addView(status);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        auto = button("", v -> {
            boolean on = !box.prefs().getBoolean(Box.AUTO, true);
            box.prefs().edit().putBoolean(Box.AUTO, on).apply();
        });
        row.addView(auto);
        row.addView(button(getString(R.string.sync_now), v -> box.force(Rule.Mode.SYNC)));
        row.addView(button(getString(R.string.other_now), v -> box.force(Rule.Mode.OTHER)));
        col.addView(row);
        setContentView(col);
        auto.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        box.prefs().registerOnSharedPreferenceChangeListener(this);
        refresh();
    }

    @Override
    protected void onPause() {
        box.prefs().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        runOnUiThread(this::refresh);
    }

    private void refresh() {
        SharedPreferences p = box.prefs();
        auto.setText(getString(p.getBoolean(Box.AUTO, true) ? R.string.auto_on : R.string.auto_off));
        StringBuilder s = new StringBuilder();
        if (!box.configured()) s.append(getString(R.string.status_no_key));
        else s.append(p.getString(Box.STATUS, getString(R.string.status_idle)));
        String ip = p.getString(Box.IP, BuildConfig.IP);
        if (ip != null && !ip.isEmpty()) s.append('\n').append(getString(R.string.status_ip, ip));
        String input = p.getString(Box.INPUT, null);
        if (input != null) s.append('\n').append(getString(R.string.status_input, input));
        status.setText(s);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private Button button(String label, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setTextColor(Color.WHITE);
        b.setFocusable(true);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, pill(0xFF3A7BD5, Color.WHITE));
        bg.addState(new int[]{}, pill(0xFF26303A, 0x00000000));
        b.setBackground(bg);
        b.setPadding(dp(28), dp(12), dp(28), dp(12));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(10), 0, dp(10), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private GradientDrawable pill(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(24));
        d.setStroke(dp(2), stroke);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
