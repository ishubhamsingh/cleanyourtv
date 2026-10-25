package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EqualizerDrawableTest {

    @Test
    public void barsRestAtFixedHeightsWhilePaused() {
        assertEquals(0.45f, EqualizerDrawable.level(0, 0.3f, false), 1e-6);
        assertEquals(0.8f, EqualizerDrawable.level(1, 0.9f, false), 1e-6);
    }

    @Test
    public void barsMoveButNeverGoFlatOrOverflowWhilePlaying() {
        for (int bar = 0; bar < 3; bar++) {
            for (float p = 0; p <= 1f; p += 0.01f) {
                float l = EqualizerDrawable.level(bar, p, true);
                assertTrue(l >= 0.27f && l <= 0.97f);
            }
        }
        assertTrue(EqualizerDrawable.level(0, 0f, true) != EqualizerDrawable.level(0, 0.25f, true));
    }
}
