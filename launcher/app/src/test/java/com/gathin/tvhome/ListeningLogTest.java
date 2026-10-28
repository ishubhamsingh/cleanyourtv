package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class ListeningLogTest {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final long M = 60_000L;

    private static long at(int day, int hour, int minute) {
        return LocalDate.of(2026, 10, day).atTime(hour, minute).atZone(IST).toInstant().toEpochMilli();
    }

    @Test
    public void playbackAddsUpPerDayAndApp() {
        Map<String, Long> log = new HashMap<>();
        ListeningLog.add(log, "spotify", at(29, 1, 0), at(29, 1, 30), IST);
        ListeningLog.add(log, "spotify", at(29, 2, 0), at(29, 2, 10), IST);
        assertEquals(Long.valueOf(40 * M), log.get("2026-10-29|spotify"));
    }

    @Test
    public void listeningOverMidnightIsSplit() {
        Map<String, Long> log = new HashMap<>();
        ListeningLog.add(log, "spotify", at(28, 23, 50), at(29, 0, 20), IST);
        assertEquals(Long.valueOf(10 * M), log.get("2026-10-28|spotify"));
        assertEquals(Long.valueOf(20 * M), log.get("2026-10-29|spotify"));
    }

    @Test
    public void oldDaysAreDropped() {
        Map<String, Long> log = new HashMap<>();
        log.put("2026-10-01|spotify", 5L);
        log.put("2026-10-28|spotify", 5L);
        log.put("garbage", 5L);
        ListeningLog.prune(log, LocalDate.of(2026, 10, 29));
        assertFalse(log.containsKey("2026-10-01|spotify"));
        assertFalse(log.containsKey("garbage"));
        assertTrue(log.containsKey("2026-10-28|spotify"));
    }
}
