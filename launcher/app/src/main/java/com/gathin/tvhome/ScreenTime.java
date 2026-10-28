package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The dashboard's Screen time card from Android's usage events: time in the foreground per day, and per app today.
 * Pure logic; Dashboard reads the events (UsageStatsManager) and maps their types to START, STOP and ALL_STOP.
 */
final class ScreenTime {
    private ScreenTime() {
    }

    /** An app came to the front (ACTIVITY_RESUMED), left it (ACTIVITY_PAUSED / STOPPED), or the screen went off. */
    static final int START = 1, STOP = 2, ALL_STOP = 3;

    static final class Tally {
        /** Foreground time (ms) per day, oldest first; the last is today. */
        final long[] days;
        /** Today's foreground time (ms) per app. */
        final Map<String, Long> today = new HashMap<>();

        Tally(int dayCount) {
            days = new long[dayCount];
        }
    }

    /**
     * Adds up foreground time from events in time order. {@code dayStarts} are the local midnights, oldest first, the
     * last being today's; time after {@code now} is not counted. Only apps in {@code counted} add up (the TV's apps,
     * not this launcher), so Home on screen counts as nothing.
     */
    static Tally tally(String[] pkgs, int[] kinds, long[] times, long[] dayStarts, long now, Set<String> counted) {
        Tally t = new Tally(dayStarts.length);
        String current = null;
        long since = 0;
        for (int i = 0; i < kinds.length; i++) {
            switch (kinds[i]) {
                case START:
                    add(t, current, since, times[i], dayStarts, now, counted);
                    current = pkgs[i];
                    since = times[i];
                    break;
                case STOP:
                    if (pkgs[i] != null && pkgs[i].equals(current)) {
                        add(t, current, since, times[i], dayStarts, now, counted);
                        current = null;
                    }
                    break;
                case ALL_STOP:
                    add(t, current, since, times[i], dayStarts, now, counted);
                    current = null;
                    break;
                default:
                    break;
            }
        }
        add(t, current, since, now, dayStarts, now, counted);
        return t;
    }

    private static void add(Tally t, String pkg, long from, long to, long[] dayStarts, long now, Set<String> counted) {
        if (pkg == null || !counted.contains(pkg)) return;
        int n = dayStarts.length;
        for (int d = 0; d < n; d++) {
            long start = dayStarts[d], end = d + 1 < n ? dayStarts[d + 1] : now;
            long overlap = Math.min(to, end) - Math.max(from, start);
            if (overlap <= 0) continue;
            t.days[d] += overlap;
            if (d == n - 1) t.today.merge(pkg, overlap, Long::sum);
        }
    }

    /**
     * Today against the daily average of the days before it, in percent (negative: less). Days before the first one
     * with any use are history the TV does not have, not days without TV; null below three days to compare with.
     */
    static Integer versusLastWeek(long[] days) {
        int n = days.length, first = 0;
        while (first < n - 1 && days[first] == 0) first++;
        int count = n - 1 - first;
        if (count < 3) return null;
        long sum = 0;
        for (int i = first; i < n - 1; i++) sum += days[i];
        double avg = sum / (double) count;
        if (avg <= 0) return null;
        return (int) Math.round((days[n - 1] - avg) * 100 / avg);
    }

    /** The {@code k} apps with the most time, most first. */
    static List<Map.Entry<String, Long>> top(Map<String, Long> perApp, int k) {
        List<Map.Entry<String, Long>> list = new ArrayList<>(perApp.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return list.subList(0, Math.min(k, list.size()));
    }
}
