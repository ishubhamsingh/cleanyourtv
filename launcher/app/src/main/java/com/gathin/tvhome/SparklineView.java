package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** The System card's network line: the last minute of download speed, one sample a second, scaled to its peak. */
public final class SparklineView extends View {
    private static final int SAMPLES = 60;
    private final float[] values = new float[SAMPLES];
    private int count, next;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), area = new Path();
    private int color = 0xFF9CC3FF;

    public SparklineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    void add(float value) {
        values[next] = Math.max(0, value);
        next = (next + 1) % SAMPLES;
        count = Math.min(SAMPLES, count + 1);
        invalidate();
    }

    void setColor(int color) {
        this.color = color;
        fill.setShader(null);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (count < 2) return;
        float w = getWidth(), h = getHeight(), pad = line.getStrokeWidth();
        float peak = 0;
        for (int i = 0; i < count; i++) peak = Math.max(peak, values[i]);
        if (peak <= 0) peak = 1;
        path.reset();
        float step = w / (SAMPLES - 1);
        int start = count < SAMPLES ? 0 : next;
        float x0 = w - (count - 1) * step, x = x0;
        for (int i = 0; i < count; i++) {
            float y = pad + (h - 2 * pad) * (1 - values[(start + i) % SAMPLES] / peak);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
            x += step;
        }
        area.set(path);
        area.lineTo(w, h);
        area.lineTo(x0, h);
        area.close();
        if (fill.getShader() == null) {
            fill.setShader(new LinearGradient(0, 0, 0, h, (color & 0x00FFFFFF) | 0x55000000, color & 0x00FFFFFF,
                    Shader.TileMode.CLAMP));
        }
        line.setColor(color);
        canvas.drawPath(area, fill);
        canvas.drawPath(path, line);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        fill.setShader(null);
    }
}
