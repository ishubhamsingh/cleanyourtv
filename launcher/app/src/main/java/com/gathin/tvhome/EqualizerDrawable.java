package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

/**
 * The now-playing button's icon: three rounded bars that rise and fall while music plays (start) and rest at
 * different heights while paused (stop). Drawn in the text colour the pill gives it (setTint).
 */
final class EqualizerDrawable extends Drawable implements Animatable {
    private static final float[] REST = {0.45f, 0.8f, 0.6f};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final int size;
    private final ValueAnimator clock = ValueAnimator.ofFloat(0f, 1f);
    private float phase;

    EqualizerDrawable(int sizePx) {
        size = sizePx;
        paint.setColor(0xFFFFFFFF);
        clock.setDuration(1400);
        clock.setRepeatCount(ValueAnimator.INFINITE);
        clock.setInterpolator(new LinearInterpolator());
        clock.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidateSelf();
        });
    }

    /** A bar's height (0..1) at a point in the cycle: each bar on its own beat, never flat. */
    static float level(int bar, float phase, boolean running) {
        if (!running) return REST[bar];
        double t = 2 * Math.PI * (phase * (bar == 1 ? 2 : bar == 2 ? 3 : 1) + bar * 0.31);
        return (float) (0.62 + 0.34 * Math.sin(t));
    }

    @Override
    public void draw(Canvas c) {
        float w = getBounds().width(), h = getBounds().height();
        float barW = w / 5f, gap = barW;
        float left = getBounds().left + (w - (3 * barW + 2 * gap)) / 2f;
        for (int i = 0; i < 3; i++) {
            float bh = h * 0.8f * level(i, phase, clock.isRunning());
            float top = getBounds().top + (h - bh) / 2f;
            bar.set(left + i * (barW + gap), top, left + i * (barW + gap) + barW, top + bh);
            c.drawRoundRect(bar, barW / 2f, barW / 2f, paint);
        }
    }

    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); }
    @Override public void setTint(int color) { setTintList(android.content.res.ColorStateList.valueOf(color)); }

    /** The pill's text colours (white, dark while focused), by the button's state. */
    private android.content.res.ColorStateList tint;

    @Override
    public void setTintList(android.content.res.ColorStateList tint) {
        this.tint = tint;
        onStateChange(getState());
    }

    @Override
    public boolean isStateful() {
        return tint != null && tint.isStateful();
    }

    @Override
    protected boolean onStateChange(int[] state) {
        if (tint == null) return false;
        int color = tint.getColorForState(state, tint.getDefaultColor());
        if (color == paint.getColor()) return false;
        paint.setColor(color);
        invalidateSelf();
        return true;
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    @Override
    public void start() {
        if (!clock.isRunning()) clock.start();
    }

    @Override
    public void stop() {
        clock.cancel();
        invalidateSelf();
    }

    @Override
    public boolean isRunning() {
        return clock.isRunning();
    }
}
