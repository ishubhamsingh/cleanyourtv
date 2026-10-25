package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DiscoverItemTest {

    private static Discover.Item item(String key, String pkg, String app, String elsewhere) {
        Discover.Item it = new Discover.Item();
        it.key = key;
        it.title = key;
        it.meta = "Series · Crime · 2024";
        it.pkg = pkg;
        it.app = app;
        it.elsewhere = elsewhere;
        return it;
    }

    @Test
    public void aTitleOnAnInstalledAppNamesThatApp() {
        Discover.Item it = item("tv:1", "com.netflix.ninja", "Netflix", "");
        assertTrue(it.playable());
        assertEquals("Series · Crime · 2024 · Netflix", it.line());
    }

    @Test
    public void aTitleNoAppHasNamesWhereItStreams() {
        Discover.Item it = item("movie:2", "", "", "Sun NXT");
        assertFalse(it.playable());
        assertEquals("Series · Crime · 2024 · Sun NXT", it.line());
    }

    @Test
    public void noMetaAndNoAppLeavesOnlyWhatThereIs() {
        Discover.Item it = item("movie:3", "", "", "");
        it.meta = "";
        assertEquals("", it.line());
    }

    private static Discover.Row row(String id, String... keys) {
        Discover.Item[] items = new Discover.Item[keys.length];
        for (int i = 0; i < keys.length; i++) items[i] = item(keys[i], "p", "a", "");
        return new Discover.Row(id, id, Arrays.asList(items));
    }

    @Test
    public void rowsAreTheSameOnlyWithTheSameTitlesInTheSameOrder() {
        List<Discover.Row> a = Arrays.asList(row("trending", "tv:1", "movie:2"), row("new", "movie:3"));
        assertTrue(Shelf.sameRows(a, Arrays.asList(row("trending", "tv:1", "movie:2"), row("new", "movie:3"))));
        assertFalse(Shelf.sameRows(a, Arrays.asList(row("trending", "movie:2", "tv:1"), row("new", "movie:3"))));
        assertFalse(Shelf.sameRows(a, Collections.singletonList(row("trending", "tv:1", "movie:2"))));
        assertFalse(Shelf.sameRows(a, Arrays.asList(row("trending", "tv:1", "movie:2"), row("for_you", "movie:3"))));
    }
}
