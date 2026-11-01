package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class RecentsTest {
    private static final String SELF = "com.gathin.tvhome", HOME = "com.gathin.tvhome.MainActivity",
            SWITCHER = "com.gathin.tvhome.SwitcherActivity";
    private static final Set<String> APPS = new HashSet<>(Arrays.asList(
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.spotify.tv.android"));

    @Test public void latestFirstEachOnce() {
        Recents.Result r = Recents.from(
                new String[]{"com.netflix.ninja", SELF, "com.spotify.tv.android", SELF, "com.netflix.ninja",
                        "com.google.android.youtube.tv"},
                new String[]{"a", HOME, "b", HOME, "a", "c"}, APPS, SELF, SWITCHER, 6);
        assertEquals(Arrays.asList("com.google.android.youtube.tv", "com.netflix.ninja", "com.spotify.tv.android"), r.apps);
        assertTrue(r.firstOnScreen);
        assertEquals(1, r.focus());   // the app before the one on screen
    }

    @Test public void fromHomeTheLatestIsFocused() {
        Recents.Result r = Recents.from(new String[]{"com.netflix.ninja", "com.spotify.tv.android", SELF},
                new String[]{"a", "b", HOME}, APPS, SELF, SWITCHER, 6);
        assertEquals(Arrays.asList("com.spotify.tv.android", "com.netflix.ninja"), r.apps);
        assertFalse(r.firstOnScreen);
        assertEquals(0, r.focus());
    }

    @Test public void theSwitcherItselfIsNotOnScreen() {
        Recents.Result r = Recents.from(new String[]{"com.netflix.ninja", "com.spotify.tv.android", SELF},
                new String[]{"a", "b", SWITCHER}, APPS, SELF, SWITCHER, 6);
        assertTrue(r.firstOnScreen);
        assertEquals(1, r.focus());
    }

    @Test public void systemPanelsAndOtherPackagesAreSkipped() {
        Recents.Result r = Recents.from(new String[]{"com.netflix.ninja", "com.android.systemui", "com.android.tv.settings"},
                new String[]{"a", "x", "y"}, APPS, SELF, SWITCHER, 6);
        assertEquals(Arrays.asList("com.netflix.ninja"), r.apps);
        assertFalse(r.firstOnScreen);   // a settings panel is in front, not an app the switcher shows
    }

    @Test public void atMostMax() {
        Recents.Result r = Recents.from(new String[]{"com.netflix.ninja", "com.spotify.tv.android", "com.google.android.youtube.tv"},
                new String[]{"a", "b", "c"}, APPS, SELF, SWITCHER, 2);
        assertEquals(Arrays.asList("com.google.android.youtube.tv", "com.spotify.tv.android"), r.apps);
    }

    @Test public void nothingYet() {
        Recents.Result r = Recents.from(new String[0], new String[0], APPS, SELF, SWITCHER, 6);
        assertTrue(r.apps.isEmpty());
        assertEquals(0, r.focus());
    }
}
