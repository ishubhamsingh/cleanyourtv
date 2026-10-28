package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * aurora_background: the backdrop as a few large soft clouds of colour drifting slowly, like Apple Music's player.
 * Radial gradients, so no blur pass; redrawn about 30 times a second only while started (Home on screen). The colours
 * come from the playing track's artwork and cross-fade when it changes; the bottom is shaded so the dock reads.
 */
public final class AuroraView extends View {
    private static final long FRAME_MS = 33, FADE_MS = 2000;
    /** Each cloud's path: x and y frequencies over a 90 s loop, and phases, so the drift never quite repeats. */
    private static final float[][] PATH = {{1, 2, 0.0f, 0.3f}, {2, 1, 0.4f, 0.1f}, {1, 3, 0.7f, 0.6f}, {3, 2, 0.2f, 0.85f}};
    private static final long LOOP_MS = 90_000;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint();
    private int[] from = AuroraPalette.DEFAULT.clone(), to = AuroraPalette.DEFAULT.clone();
    private long fadeStart;
    private boolean running, still;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    public AuroraView(Context context, boolean still) {
        super(context);
        this.still = still;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The playing track's artwork, or null for the default colours. */
    void setArt(Bitmap art) {
        int[] next;
        if (art == null) {
            next = AuroraPalette.DEFAULT.clone();
        } else {
            Bitmap small = Bitmap.createScaledBitmap(art, 8, 8, true);
            int[] px = new int[64];
            small.getPixels(px, 0, 8, 0, 0, 8, 8);
            if (small != art) small.recycle();
            next = AuroraPalette.fromPixels(px, 8, 8);
        }
        if (java.util.Arrays.equals(next, to)) return;
        from = current();
        to = next;
        fadeStart = SystemClock.uptimeMillis();
        invalidate();
    }

    void start() {
        if (running || still) {
            invalidate();
            return;
        }
        running = true;
        post(frame);
    }

    void stop() {
        running = false;
        removeCallbacks(frame);
    }

    private int[] current() {
        float t = Math.min(1f, (SystemClock.uptimeMillis() - fadeStart) / (float) FADE_MS);
        int[] c = new int[4];
        for (int i = 0; i < 4; i++) c[i] = AuroraPalette.mix(from[i], to[i], t);
        return c;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        shade.setShader(new LinearGradient(0, h * 0.45f, 0, h, 0x00000000, 0xA6000000, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        int[] c = current();
        // A dark base in the first colour, then the clouds over it.
        canvas.drawColor(AuroraPalette.mix(0xFF000000, c[0], 0.18f));
        float phase = still ? 0 : (SystemClock.uptimeMillis() % LOOP_MS) / (float) LOOP_MS;
        float radius = Math.max(w, h) * 0.48f;
        for (int i = 0; i < 4; i++) {
            float[] p = PATH[i];
            float x = w * (0.5f + 0.42f * (float) Math.sin(2 * Math.PI * (p[0] * phase + p[2])));
            float y = h * (0.45f + 0.40f * (float) Math.cos(2 * Math.PI * (p[1] * phase + p[3])));
            int rgb = c[i] & 0xFFFFFF;
            paint.setShader(new RadialGradient(x, y, radius, new int[]{0xF0000000 | rgb, 0x70000000 | rgb, rgb},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, radius, paint);
        }
        canvas.drawRect(0, 0, w, h, shade);
    }
}
