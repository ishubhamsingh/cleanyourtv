package com.gathin.tvhome;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** dashboard_cards: which of the dashboard's four cards show, from a theme list or the TV's own choice. */
final class DashboardCards {
    private DashboardCards() {
    }

    static final String NOW_PLAYING = "now_playing", WEATHER = "weather", SYSTEM = "system", SCREEN_TIME = "screen_time";
    /** Every card, in the row's order. */
    static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(NOW_PLAYING, WEATHER, SYSTEM, SCREEN_TIME));

    /** The known names in the list, in the row's order; unknown names (typos) are dropped. */
    static Set<String> of(List<String> names) {
        Set<String> out = new LinkedHashSet<>();
        if (names == null) return out;
        for (String card : ALL) if (names.contains(card)) out.add(card);
        return out;
    }

    /** As stored on the TV: "now_playing,system"; "" for none. */
    static String join(Set<String> cards) {
        StringBuilder b = new StringBuilder();
        for (String card : cards) b.append(b.length() == 0 ? "" : ",").append(card);
        return b.toString();
    }

    static Set<String> split(String stored) {
        return of(stored == null || stored.isEmpty() ? Collections.<String>emptyList() : Arrays.asList(stored.split(",")));
    }
}
