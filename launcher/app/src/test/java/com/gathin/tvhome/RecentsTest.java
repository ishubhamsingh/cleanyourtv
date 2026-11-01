package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class RecentsTest {
    private static final String SELF = "com.gathin.tvhome", HOME = "com.gathin.tvhome.MainActivity",
            SWITCHER = "com.gathin.tvhome.SwitcherActivity";
    private static final Set<String> APPS = new HashSet<>(Arrays.asList(
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.spotify.tv.android"));

    /** The events one second apart, nothing removed. */
    private static Recents.Result from(String[] pkgs, String[] classes, Set<String> apps, String self, String sw, int max) {
        return from(pkgs, classes, Collections.emptyMap(), max);
    }

    private static Recents.Result from(String[] pkgs, String[] classes, Map<String, Long> removed, int max) {
        long[] times = new long[pkgs.length];
        for (int i = 0; i < times.length; i++) times[i] = (i + 1) * 1000L;
        return Recents.from(pkgs, classes, times, APPS, SELF, SWITCHER, max, removed);
    }

    @Test public void removedStaysGoneUntilOpenedAgain() {
        Map<String, Long> removed = new HashMap<>();
        removed.put("com.netflix.ninja", 2500L);   // removed after its resume at 1 s (and the one at 2 s)
        String[] pkgs = {"com.netflix.ninja", "com.netflix.ninja", "com.spotify.tv.android", SELF};
        String[] classes = {"a", "a", "b", HOME};
        assertEquals(Arrays.asList("com.spotify.tv.android"), from(pkgs, classes, removed, 6).apps);
        String[] again = {"com.netflix.ninja", "com.spotify.tv.android", "com.netflix.ninja"};   // opened at 3 s
        assertEquals(Arrays.asList("com.netflix.ninja", "com.spotify.tv.android"),
                from(again, new String[]{"a", "b", "a"}, removed, 6).apps);
    }

    @Test public void removingTheAppOnScreenLeavesNothingOnScreen() {
        Map<String, Long> removed = new HashMap<>();
        removed.put("com.netflix.ninja", 9000L);
        Recents.Result r = from(new String[]{"com.spotify.tv.android", "com.netflix.ninja"}, new String[]{"b", "a"}, removed, 6);
        assertEquals(Arrays.asList("com.spotify.tv.android"), r.apps);
        assertFalse(r.firstOnScreen);
    }

    @Test public void latestFirstEachOnce() {
        Recents.Result r = from(
                new String[]{"com.netflix.ninja", SELF, "com.spotify.tv.android", SELF, "com.netflix.ninja",
                        "com.google.android.youtube.tv"},
                new String[]{"a", HOME, "b", HOME, "a", "c"}, APPS, SELF, SWITCHER, 6);
        assertEquals(Arrays.asList("com.google.android.youtube.tv", "com.netflix.ninja", "com.spotify.tv.android"), r.apps);
        assertTrue(r.firstOnScreen);
        assertEquals(1, r.focus());   // the app before the one on screen
    }

    @Test public void fromHomeTheLatestIsFocused() {
        Recents.Result r = from(new String[]{"com.netflix.ninja", "com.spotify.tv.android", SELF},
                new String[]{"a", "b", HOME}, APPS, SELF, SWITCHER, 6);
        assertEquals(Arrays.asList("com.spotify.tv.android", "com.netflix.ninja"), r.apps);
        assertFalse(r.firstOnScreen);
        assertEquals(0, r.focus());
    }

    @Test public void theSwitcherItselfIsNotOnScreen() {
        Recents.Result r = from(new String[]{"com.netflix.ninja", "com.spotify.tv.android", SELF},
                new String[]{"a", "b", SWITCHER}, APPS, SELF, SWITCHER, 6);
        assertTrue(r.firstOnScreen);
        assertEquals(1, r.focus());
    }

    @Test public void systemPanelsAndOtherPackagesAreSkipped() {
        Recents.Result r = from(new String[]{"com.netflix.ninja", "com.android.systemui", "com.android.tv.settings"},
                new String[]{"a", "x", "y"}, APPS, SELF, SWITCHER, 6);
        assertEquals(Arrays.asList("com.netflix.ninja"), r.apps);
        assertFalse(r.firstOnScreen);   // a settings panel is in front, not an app the switcher shows
    }

    @Test public void atMostMax() {
        Recents.Result r = from(new String[]{"com.netflix.ninja", "com.spotify.tv.android", "com.google.android.youtube.tv"},
                new String[]{"a", "b", "c"}, APPS, SELF, SWITCHER, 2);
        assertEquals(Arrays.asList("com.google.android.youtube.tv", "com.spotify.tv.android"), r.apps);
    }

    @Test public void nothingYet() {
        Recents.Result r = from(new String[0], new String[0], APPS, SELF, SWITCHER, 6);
        assertTrue(r.apps.isEmpty());
        assertEquals(0, r.focus());
    }
}
