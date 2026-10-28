package com.gathin.tvhome;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Map;

/**
 * Time media played in the background, per app and day, for the dashboard's Screen time card: music behind Home is
 * time on the TV too, but usage events only count the app on screen. Kept as "2026-10-29|com.spotify.tv.android" →
 * ms in NowPlayingListener's preferences, written while a session plays.
 */
final class ListeningLog {
    private ListeningLog() {
    }

    static final String PREFS = "listening";
    private static final int KEEP_DAYS = 8;

    static String key(LocalDate day, String pkg) {
        return day + "|" + pkg;
    }

    /** Adds the time from {@code from} to {@code to} (epoch ms) for {@code pkg}, split at local midnights. */
    static void add(Map<String, Long> log, String pkg, long from, long to, ZoneId zone) {
        while (from < to) {
            LocalDate day = Instant.ofEpochMilli(from).atZone(zone).toLocalDate();
            long dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            long end = Math.min(to, dayEnd);
            log.merge(key(day, pkg), end - from, Long::sum);
            from = end;
        }
    }

    /** Drops days older than a week before {@code today}. */
    static void prune(Map<String, ?> log, LocalDate today) {
        LocalDate oldest = today.minusDays(KEEP_DAYS);
        for (Iterator<String> it = log.keySet().iterator(); it.hasNext(); ) {
            String k = it.next();
            int bar = k.indexOf('|');
            try {
                if (bar < 0 || LocalDate.parse(k.substring(0, bar)).isBefore(oldest)) it.remove();
            } catch (java.time.format.DateTimeParseException e) {
                it.remove();
            }
        }
    }
}
