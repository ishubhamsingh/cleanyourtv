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
 * Radial gradients, so no blur pass; redrawn 12.5 times a second only while started (the drift is a 90 s loop, so that is enough) (Home on screen). The clouds are
 * drawn into an eighth-size bitmap and scaled up with filtering: soft gradients look the same, at a 64th of the fill
 * (21% of a core at idle before, on a BRAVIA AE2). The colours come from the playing track's artwork and cross-fade
 * when it changes; the bottom is shaded so the dock reads.
 */
public final class AuroraView extends View {
    private static final long FRAME_MS = 80, FADE_MS = 2000;
    private static final int SCALE = 8;
    /** Each cloud's path: x and y frequencies over a 90 s loop, and phases, so the drift never quite repeats. */
    private static final float[][] PATH = {{1, 2, 0.0f, 0.3f}, {2, 1, 0.4f, 0.1f}, {1, 3, 0.7f, 0.6f}, {3, 2, 0.2f, 0.85f}};
    private static final long LOOP_MS = 90_000;
    private final Paint paint = new Paint(), shade = new Paint(), scaled = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap buffer;
    private Canvas bufferCanvas;
    private final android.graphics.Rect dst = new android.graphics.Rect();
    /** One gradient per cloud, centred on 0,0 and moved by its matrix; made again only when its colour changes. */
    private final RadialGradient[] clouds = new RadialGradient[4];
    private final int[] cloudColors = new int[4];
    private final android.graphics.Matrix move = new android.graphics.Matrix();
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
        if (buffer != null) buffer.recycle();
        buffer = w > 0 && h > 0 ? Bitmap.createBitmap(Math.max(1, w / SCALE), Math.max(1, h / SCALE), Bitmap.Config.ARGB_8888) : null;
        bufferCanvas = buffer == null ? null : new Canvas(buffer);
        java.util.Arrays.fill(clouds, null);      // their radius follows the size
        dst.set(0, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || buffer == null) return;
        int[] c = current();
        int bw = buffer.getWidth(), bh = buffer.getHeight();
        // A dark base in the first colour, then the clouds over it, all in the small buffer.
        bufferCanvas.drawColor(AuroraPalette.mix(0xFF000000, c[0], 0.18f));
        float phase = still ? 0 : (SystemClock.uptimeMillis() % LOOP_MS) / (float) LOOP_MS;
        float radius = Math.max(bw, bh) * 0.48f;
        for (int i = 0; i < 4; i++) {
            float[] p = PATH[i];
            float x = bw * (0.5f + 0.42f * (float) Math.sin(2 * Math.PI * (p[0] * phase + p[2])));
            float y = bh * (0.45f + 0.40f * (float) Math.cos(2 * Math.PI * (p[1] * phase + p[3])));
            int rgb = c[i] & 0xFFFFFF;
            if (clouds[i] == null || cloudColors[i] != rgb) {
                clouds[i] = new RadialGradient(0, 0, radius, new int[]{0xF0000000 | rgb, 0x70000000 | rgb, rgb},
                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
                cloudColors[i] = rgb;
            }
            move.setTranslate(x, y);
            clouds[i].setLocalMatrix(move);
            paint.setShader(clouds[i]);
            bufferCanvas.drawCircle(x, y, radius, paint);
        }
        canvas.drawBitmap(buffer, null, dst, scaled);
        canvas.drawRect(0, 0, w, h, shade);
    }
}
