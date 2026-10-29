package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

import org.junit.Test;

public class ScreenTimeTest {
    private static final long H = 3_600_000L, M = 60_000L;
    private static final Set<String> APPS = new HashSet<>(Arrays.asList("yt", "nf"));
    // Two days: yesterday starts at 0, today at 24h.
    private static final long[] DAYS = {0, 24 * H};

    @Test
    public void anAppCountsFromResumedToPausedAndHomeCountsNothing() {
        ScreenTime.Tally t = ScreenTime.tally(
                new String[]{"yt", "yt", "home", "nf"},
                new int[]{ScreenTime.START, ScreenTime.STOP, ScreenTime.START, ScreenTime.START},
                new long[]{25 * H, 26 * H, 26 * H, 27 * H},
                DAYS, 27 * H + 30 * M, APPS);
        assertEquals(H + 30 * M, t.days[1]);
        assertEquals(Long.valueOf(H), t.today.get("yt"));
        assertEquals(Long.valueOf(30 * M), t.today.get("nf"));       // still in front at "now"
    }

    @Test
    public void aSessionOverMidnightIsSplitBetweenTheDays() {
        ScreenTime.Tally t = ScreenTime.tally(
                new String[]{"nf", "nf"}, new int[]{ScreenTime.START, ScreenTime.STOP},
                new long[]{23 * H, 25 * H}, DAYS, 26 * H, APPS);
        assertEquals(H, t.days[0]);
        assertEquals(H, t.days[1]);
        assertEquals(Long.valueOf(H), t.today.get("nf"));
    }

    @Test
    public void theScreenGoingOffStopsTheClock() {
        ScreenTime.Tally t = ScreenTime.tally(
                new String[]{"yt", null}, new int[]{ScreenTime.START, ScreenTime.ALL_STOP},
                new long[]{25 * H, 25 * H + 10 * M}, DAYS, 30 * H, APPS);
        assertEquals(10 * M, t.days[1]);
    }

    @Test
    public void todayAgainstTheDaysBefore() {
        assertEquals(Integer.valueOf(-18), ScreenTime.versusLastWeek(new long[]{100, 100, 100, 82}));
        assertEquals(Integer.valueOf(50), ScreenTime.versusLastWeek(new long[]{100, 0, 200, 150}));
        assertNull(ScreenTime.versusLastWeek(new long[]{0, 0, 0, 0, 40}));
    }

    @Test
    public void daysBeforeTheHistoryStartsAreNotCompared() {
        // Only two days of history before today: too little to say "vs last week".
        assertNull(ScreenTime.versusLastWeek(new long[]{0, 0, 0, 0, 30, 500, 40}));
        // Three days of history: the empty days before it don't pull the average down.
        assertEquals(Integer.valueOf(-50), ScreenTime.versusLastWeek(new long[]{0, 0, 0, 100, 100, 100, 50}));
    }

    @Test
    public void topAppsMostFirst() {
        Map<String, Long> m = new java.util.HashMap<>();
        m.put("a", 5L);
        m.put("b", 9L);
        m.put("c", 1L);
        m.put("d", 7L);
        List<Map.Entry<String, Long>> top = ScreenTime.top(m, 3);
        assertEquals("b", top.get(0).getKey());
        assertEquals("d", top.get(1).getKey());
        assertEquals("a", top.get(2).getKey());
    }

    @Test
    public void theWeekAddsEveryDayPerApp() {
        ScreenTime.Tally t = ScreenTime.tally(
                new String[]{"nf", "nf", "yt", "yt", "nf"},
                new int[]{ScreenTime.START, ScreenTime.STOP, ScreenTime.START, ScreenTime.STOP, ScreenTime.START},
                new long[]{H, 2 * H, 25 * H, 25 * H + 30 * M, 26 * H}, DAYS, 26 * H + 15 * M, APPS);
        Map<String, Long> week = ScreenTime.week(t);
        assertEquals(Long.valueOf(H + 15 * M), week.get("nf"));       // an hour yesterday, 15 minutes today
        assertEquals(Long.valueOf(30 * M), week.get("yt"));
    }

    @Test
    public void listeningCountsWhenLongerThanTheTimeOnScreen() {
        ScreenTime.Tally t = ScreenTime.tally(
                new String[]{"yt", "yt"}, new int[]{ScreenTime.START, ScreenTime.STOP},
                new long[]{25 * H, 25 * H + 5 * M}, DAYS, 30 * H, new HashSet<>(Arrays.asList("yt", "nf", "sp")));
        Map<String, Long> today = new java.util.HashMap<>();
        today.put("sp", 40 * M);      // Spotify behind Home: never on screen
        today.put("yt", 2 * M);       // less than YouTube's time on screen: no change
        ScreenTime.addListening(t, Arrays.asList(new java.util.HashMap<>(), today));
        assertEquals(45 * M, t.days[1]);
        assertEquals(Long.valueOf(40 * M), t.today.get("sp"));
        assertEquals(Long.valueOf(5 * M), t.today.get("yt"));
    }
}
