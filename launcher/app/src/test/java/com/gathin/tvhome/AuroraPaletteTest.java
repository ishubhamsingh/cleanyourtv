package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AuroraPaletteTest {

    private static float brightness(int c) {
        return Math.max((c >> 16) & 0xFF, Math.max((c >> 8) & 0xFF, c & 0xFF)) / 255f;
    }

    @Test
    public void eachQuarterGivesItsOwnHue() {
        // 4×4: red top-left, green top-right, blue bottom-left, yellow bottom-right.
        int[] px = new int[16];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                px[y * 4 + x] = y < 2 ? (x < 2 ? 0xFFFF0000 : 0xFF00FF00) : (x < 2 ? 0xFF0000FF : 0xFFFFFF00);
            }
        }
        int[] p = AuroraPalette.fromPixels(px, 4, 4);
        assertTrue(((p[0] >> 16) & 0xFF) > ((p[0] >> 8) & 0xFF));     // red stays red
        assertTrue(((p[1] >> 8) & 0xFF) > ((p[1] >> 16) & 0xFF));     // green stays green
        assertTrue((p[2] & 0xFF) > ((p[2] >> 16) & 0xFF));            // blue stays blue
    }

    @Test
    public void coloursAreDimmedForWhiteTextAndDarkOnesLifted() {
        assertEquals(0.55f, brightness(AuroraPalette.backdrop(255, 40, 40)), 0.01f);
        assertEquals(0.28f, brightness(AuroraPalette.backdrop(10, 2, 2)), 0.01f);
    }

    @Test
    public void greyStaysGrey() {
        int c = AuroraPalette.backdrop(120, 120, 120);
        assertEquals((c >> 16) & 0xFF, c & 0xFF);
    }

    @Test
    public void noArtworkGivesTheDefaults() {
        assertArrayEquals(AuroraPalette.DEFAULT, AuroraPalette.fromPixels(null, 0, 0));
    }

    @Test
    public void mixGoesFromOneColourToTheOther() {
        assertEquals(0xFF000000, AuroraPalette.mix(0xFF000000, 0xFFFFFFFF, 0f));
        assertEquals(0xFF808080, AuroraPalette.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0xFFFFFFFF, AuroraPalette.mix(0xFF000000, 0xFFFFFFFF, 1f));
    }
}
