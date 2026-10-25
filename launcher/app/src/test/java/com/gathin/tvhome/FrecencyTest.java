package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FrecencyTest {

    private static final long WEEK = 7L * 24 * 60 * 60 * 1000;

    @Test
    public void scoreHalvesEveryWeek() {
        assertEquals(4.0, Frecency.decayed("4.0@1000", 1000), 1e-9);
        assertEquals(2.0, Frecency.decayed("4.0@1000", 1000 + WEEK), 1e-9);
        assertEquals(1.0, Frecency.decayed("4.0@1000", 1000 + 2 * WEEK), 1e-9);
    }

    @Test
    public void aClockThatWentBackDoesNotGrowTheScore() {
        assertEquals(3.0, Frecency.decayed("3.0@5000", 1000), 1e-9);
    }

    @Test
    public void missingOrBrokenValuesCountAsNeverOpened() {
        assertEquals(0.0, Frecency.decayed(null, 1000), 0);
        assertEquals(0.0, Frecency.decayed("", 1000), 0);
        assertEquals(0.0, Frecency.decayed("@1000", 1000), 0);
        assertEquals(0.0, Frecency.decayed("x@1000", 1000), 0);
        assertEquals(0.0, Frecency.decayed("2.0", 1000), 0);
    }

    @Test
    public void unknownGridOrderFallsBackToAlphabetical() {
        assertEquals(Config.GRID_FREQUENT, Config.gridOrder("frequent"));
        assertEquals(Config.GRID_RECENT, Config.gridOrder("recent"));
        assertEquals(Config.GRID_ALPHABETICAL, Config.gridOrder("Frequent"));
        assertEquals(Config.GRID_ALPHABETICAL, Config.gridOrder(""));
    }
}
