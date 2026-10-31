package com.gathin.tvhome.backlight;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

/** The values here are what the Smart Life app sent the LDD11 MAX, watched on the network. */
public class LooksTest {
    @Test
    public void syncAndScenesNameTheirMode() {
        Map<String, String> sync = Looks.sync(2);
        assertEquals(Arrays.asList("21", "25"), new ArrayList<>(sync.keySet()));
        assertEquals("scene", sync.get("21"));
        assertEquals("82000000000000000000000000000000", sync.get("25"));
        assertEquals("80000000000000000000000000000000", Looks.sync(0).get("25"));
        assertEquals("96000000000000000000000000000000", Looks.scene(0x96).get("25"));
    }

    @Test
    public void calmIsTheWhiteAtAWarmth() {
        Map<String, String> calm = Looks.calm(0);
        assertEquals(Arrays.asList("25", "21", "23"), new ArrayList<>(calm.keySet()));     // Calm's scene first
        assertEquals("88000000000000000000000000000000", calm.get("25"));
        assertEquals("white", calm.get("21"));
        assertEquals("0", calm.get("23"));
        assertEquals(calm, Looks.defaultOther());
    }

    @Test
    public void coloursAreWrittenAsTheAppWritesThem() {
        Map<String, String> red = Looks.colour(0, 1000, 1000);
        assertEquals(Arrays.asList("25", "21", "24"), new ArrayList<>(red.keySet()));      // the Color scene first
        assertEquals("97000000000000000000000000000000", red.get("25"));
        assertEquals("000003e803e8", red.get("24"));                                        // the app's red
        assertEquals(Arrays.asList("24"), new ArrayList<>(Looks.colourOnly(0, 1000, 1000).keySet()));
        assertEquals("00f003e803e8", Looks.colour(240, 1000, 1000).get("24"));    // and blue
        assertEquals("016803e80000", Looks.hsv(400, 2000, -5));                    // out of range: clamped
        assertArrayEquals(new int[]{240, 1000, 1000}, Looks.parseHsv("00F003E803E8".toLowerCase()));
    }

    @Test
    public void theBoxsReportGivesWhatIsOn() {
        Looks.State s = new Looks.State();
        s.update("{\"dps\":{\"20\":true,\"21\":\"scene\",\"22\":480,\"25\":\"82000000000000000000000000000000\"}}");
        assertTrue(s.isSync());
        assertEquals(2, s.syncLevel());
        assertEquals(480, s.bright);
        s.update("{\"protocol\":4,\"data\":{\"dps\":{\"21\":\"white\",\"23\":250}}}");
        assertFalse(s.isSync());
        assertEquals(1, s.tempIndex());
        s.apply(Looks.colour(240, 1000, 1000));
        assertEquals(5, s.hueIndex());
        s.apply(Looks.scene(0x87));
        assertEquals(1, s.sceneIndex());
        assertEquals(-1, s.tempIndex());
    }

    @Test
    public void theLookAwayFromSyncSurvivesAsJsonSceneFirst() {
        Map<String, String> m = Looks.fromJson("{\"23\":\"500\",\"21\":\"white\",\"25\":\"88000000000000000000000000000000\"}");
        assertEquals(Arrays.asList("25", "21", "23"), new ArrayList<>(m.keySet()));
        assertEquals(Looks.calm(500), Looks.fromJson(Looks.toJson(Looks.calm(500))));
        assertTrue(Looks.fromJson("not json").isEmpty());
        // Kept before looks carried their scene: Calm's and Color's are put in front.
        assertEquals(Looks.calm(0), Looks.fromJson("{\"21\":\"white\",\"23\":\"0\"}"));
        assertEquals(Looks.colour(320, 1000, 800), Looks.fromJson("{\"21\":\"colour\",\"24\":\"014003e80320\"}"));
    }
}
