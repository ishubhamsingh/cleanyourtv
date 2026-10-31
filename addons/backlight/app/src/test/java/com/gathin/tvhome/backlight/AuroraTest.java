package com.gathin.tvhome.backlight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/** The launcher's aurora, ported: these check it behaves as AuroraPalette and AuroraView do. */
public class AuroraTest {
    @Test
    public void aCoverInOneColourStillGivesFourClouds() {
        int[] amber = new int[64];
        Arrays.fill(amber, 0xFFE0A030);
        int[] c = Aurora.colours(amber, 8, 8);
        Set<Integer> hues = new HashSet<>();
        float[] hsv = new float[3];
        for (int x : c) {
            Aurora.hsv(x, hsv);
            hues.add(Math.round(hsv[0]));
            assertTrue(hsv[2] >= 0.27f && hsv[2] <= 0.56f);      // a backdrop's brightness, as on screen
        }
        assertEquals(4, hues.size());
    }

    @Test
    public void greysStayGrey() {
        int[] grey = new int[64];
        Arrays.fill(grey, 0xFF808080);
        for (int x : Aurora.colours(grey, 8, 8)) assertEquals(0, Aurora.forStrip(x).s);
    }

    @Test
    public void eachQuarterGivesItsColour() {
        int[] px = new int[64];
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) px[y * 8 + x] = x < 4 ? 0xFFD02020 : 0xFF2040D0;
        int[] c = Aurora.colours(px, 8, 8);
        assertTrue(Aurora.forStrip(c[0]).h < 20 || Aurora.forStrip(c[0]).h > 340);     // top left: red
        int blue = Aurora.forStrip(c[1]).h;
        assertTrue(blue > 200 && blue < 250);                                             // top right: blue
    }

    @Test
    public void cloudsAreSolidInTheMiddleAndGoneAtTheEdge() {
        assertEquals(0xF0 / 255f, Aurora.alpha(0), 1e-4);
        assertEquals(0x70 / 255f, Aurora.alpha(0.45f), 1e-4);
        assertEquals(0, Aurora.alpha(1), 1e-6);
        assertEquals(0, Aurora.alpha(2), 1e-6);
    }

    @Test
    public void theMiddleOfTheScreenChangesAsTheCloudsDrift() {
        int[] c = Aurora.DEFAULT;
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 20; i++) seen.add(Aurora.forStrip(Aurora.colourAt(c, i / 20f, 0.5f, 0.45f)).h / 10);
        assertTrue("only " + seen, seen.size() >= 3);
        // The same moment gives the same colour: every app shares the clock.
        assertEquals(Aurora.colourAt(c, 0.3f, 0.5f, 0.45f), Aurora.colourAt(c, 0.3f, 0.5f, 0.45f));
    }

    @Test
    public void theTintFollowsWhatFillsTheScreen() {
        int[] blues = {0xFF203080, 0xFF203080, 0xFF203080, 0xFF802030};      // three blue clouds, one red
        int h = Aurora.tint(blues, 0.25f).h;
        assertTrue("hue " + h, h > 200 && h < 285);      // blue, leaning to the red cloud
        int[] greys = {0xFF606060, 0xFF606060, 0xFF606060, 0xFF606060};
        assertEquals(0, Aurora.tint(greys, 0.5f).s);
    }

    @Test
    public void aNewCoverFadesInOverTwoSeconds() {
        int[] red = {0xFF801010, 0xFF801010, 0xFF801010, 0xFF801010}, blue = {0xFF101080, 0xFF101080, 0xFF101080, 0xFF101080};
        assertEquals(Aurora.now(red, red, 0, 1000).h, Aurora.now(red, blue, 1000, 1000).h);
        assertEquals(Aurora.now(blue, blue, 0, 5000).h, Aurora.now(red, blue, 1000, 5000).h);
        assertNotEquals(Aurora.now(red, red, 0, 2000).h, Aurora.now(red, blue, 1000, 2000).h);
    }

    @Test
    public void theStripGetsAVividColour() {
        Aurora.Hue h = Aurora.forStrip(0xFF5A2A6B);       // a dim plum backdrop
        assertTrue(h.s >= 800);
    }

    @Test
    public void aSlateBlueStaysBlueOnTheStrip() {
        // The screen's hue 220 has some green in it, which a strip shows as cyan: it goes to the blue side.
        int slate = Aurora.backdrop(0x40, 0x80, 0xFF, 0);
        Aurora.Hue h = Aurora.forStrip(slate);
        assertTrue("hue " + h.h, h.h >= 228 && h.h <= 245);
        // Pure colours stay where they are.
        assertEquals(0, Aurora.forStrip(0xFFFF0000).h);
        assertEquals(240, Aurora.forStrip(0xFF0000FF).h);
    }
}
