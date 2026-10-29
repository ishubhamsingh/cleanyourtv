package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * A dashboard card's details (Screen time's week, Weather's three days): a glass panel in the middle of a dimmed
 * screen. It shows only; OK or Back closes it, and nothing in it takes focus.
 */
final class DashPanel {
    private DashPanel() {
    }

    /** A vertical column to fill, with the panel's padding; pass it to {@link #show}. */
    static LinearLayout column(Activity a) {
        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundResource(R.drawable.glass_panel);
        int p = Math.round(32 * d);
        col.setPadding(p, Math.round(28 * d), p, Math.round(24 * d));
        col.setElevation(12 * d);
        return col;
    }

    static TextView text(Activity a, float sp, int color) {
        TextView t = new TextView(a);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        // The dashboard's fonts: headings and big numbers in the medium weight, the rest in the body face.
        t.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(a, sp >= 18 ? R.font.outfit_medium : R.font.outfit_regular));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    static PopupWindow show(Activity a, LinearLayout column, int widthDp) {
        float d = a.getResources().getDisplayMetrics().density;
        TextView hint = text(a, 12, 0x80FFFFFF);
        hint.setText(R.string.dash_panel_close);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = Math.round(22 * d);
        column.addView(hint, hp);

        FrameLayout scrim = new FrameLayout(a);
        scrim.setBackgroundColor(0x99000000);
        scrim.addView(column, new FrameLayout.LayoutParams(Math.round(widthDp * d), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        PopupWindow popup = new PopupWindow(scrim, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));    // Back dismisses
        popup.setAnimationStyle(0);
        scrim.setFocusable(true);
        scrim.setFocusableInTouchMode(true);
        scrim.setOnKeyListener((v, keyCode, event) -> {
            boolean close = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_BACK;
            if (close && event.getAction() == KeyEvent.ACTION_UP) popup.dismiss();
            return true;      // the arrows have nowhere to go: swallow them so nothing behind moves
        });
        scrim.setOnClickListener(v -> popup.dismiss());
        popup.showAtLocation(a.getWindow().getDecorView(), Gravity.CENTER, 0, 0);
        scrim.requestFocus();
        scrim.setAlpha(0f);
        column.setScaleX(0.96f);
        column.setScaleY(0.96f);
        scrim.animate().alpha(1f).setDuration(180).start();
        column.animate().scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        return popup;
    }

    /** A row of equal cells, one view per cell, for labels under bars or the forecast's days. */
    static LinearLayout cells(Activity a, View... views) {
        LinearLayout row = new LinearLayout(a);
        for (View v : views) row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }
}
