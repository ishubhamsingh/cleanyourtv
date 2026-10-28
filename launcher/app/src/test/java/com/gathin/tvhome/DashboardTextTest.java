package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DashboardTextTest {
    private static final long GB = 1024L * 1024 * 1024;

    @Test
    public void trackTimes() {
        assertEquals("0:00", DashboardText.clock(0));
        assertEquals("1:32", DashboardText.clock(92_400));
        assertEquals("12:05", DashboardText.clock(725_000));
        assertEquals("1:02:05", DashboardText.clock(3_725_000));
        assertEquals("", DashboardText.clock(-1));
    }

    @Test
    public void networkSpeed() {
        assertEquals("0 KB/s", DashboardText.rate(0));
        assertEquals("820 KB/s", DashboardText.rate(820 * 1024));
        assertEquals("12.4 MB/s", DashboardText.rate(12.4 * 1024 * 1024));
        assertEquals("120 MB/s", DashboardText.rate(120.2 * 1024 * 1024));
        assertEquals("0 KB/s", DashboardText.rate(-5));
    }

    @Test
    public void wifiBand() {
        assertEquals("2.4 GHz", DashboardText.band(2437));
        assertEquals("5 GHz", DashboardText.band(5180));
        assertEquals("6 GHz", DashboardText.band(5955));
        assertEquals("", DashboardText.band(-1));
    }

    @Test
    public void uptimeKeepsTheTwoLargestUnits() {
        assertEquals("12m", DashboardText.uptime(12 * 60_000));
        assertEquals("4h 12m", DashboardText.uptime((4 * 60 + 12) * 60_000L));
        assertEquals("3d 4h", DashboardText.uptime(((3 * 24 + 4) * 60 + 30) * 60_000L));
    }

    @Test
    public void sizesInGigabytes() {
        assertEquals("2.2 / 4.5 GB", DashboardText.usedOfTotal((long) (2.2 * GB), (long) (4.54 * GB)));
        assertEquals("18.4 / 32 GB", DashboardText.usedOfTotal((long) (18.4 * GB), 32 * GB));
        assertEquals(500, DashboardText.permille(1, 2));
        assertEquals(0, DashboardText.permille(5, 0));
        assertEquals(1000, DashboardText.permille(9, 3));
    }
}
