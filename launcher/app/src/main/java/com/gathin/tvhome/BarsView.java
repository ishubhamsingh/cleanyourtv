package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** The Screen time card's week: one rounded bar a day, scaled to the busiest, today's (the last) in the accent. */
public final class BarsView extends View {
    private long[] values = new long[0];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    /** The week panel's big chart: a bar centred in each equal cell, so labels in a row of cells line up. */
    private boolean wide;

    public BarsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setWide(boolean wide) {
        this.wide = wide;
        invalidate();
    }

    void setValues(long[] values) {
        this.values = values.clone();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = values.length;
        if (n == 0) return;
        long peak = 1;
        for (long v : values) peak = Math.max(peak, v);
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight(), barW = Math.min(7 * d, w / (n * 1.6f)), gap = barW * 0.7f;
        float cell = w / n;
        if (wide) barW = Math.min(cell * 0.42f, 30 * d);
        float r = wide ? 8 * d : barW / 2f, x = w - n * barW - (n - 1) * gap;     // thin bars, at the card's end
        for (int i = 0; i < n; i++) {
            float bh = Math.max(3 * d, h * values[i] / (float) peak);
            paint.setColor(i == n - 1 ? 0xFF9CC3FF : 0x59FFFFFF);
            float left = wide ? cell * i + (cell - barW) / 2f : x;
            bar.set(left, h - bh, left + barW, h);
            canvas.drawRoundRect(bar, r, r, paint);
            x += barW + gap;
        }
    }
}
