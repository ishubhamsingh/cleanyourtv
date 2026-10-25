package com.gathin.tvhome.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TasteTest {

    private static Taste.Seed seed(String key) {
        Taste.Seed s = new Taste.Seed();
        s.key = key;
        s.kind = key.substring(0, key.indexOf(':'));
        s.id = Integer.parseInt(key.substring(key.indexOf(':') + 1));
        s.title = key;
        return s;
    }

    private static Title title(String kind, int id) {
        Title t = new Title();
        t.kind = kind;
        t.id = id;
        t.title = kind + id;
        return t;
    }

    @Test
    public void aSeedHalvesEveryThreeWeeks() {
        Taste.Seed s = seed("movie:1");
        s.weight = 4;
        s.at = 0;
        assertEquals(4, Taste.score(s, 0), 1e-9);
        assertEquals(2, Taste.score(s, Taste.HALF_LIFE_MS), 1e-9);
    }

    @Test
    public void openingTwiceAddsUpAndALikeOutweighsAnOpen() {
        List<Taste.Seed> seeds = new ArrayList<>();
        seeds = Taste.add(seeds, seed("movie:1"), Taste.OPEN, 0);
        seeds = Taste.add(seeds, seed("tv:2"), Taste.LIKE, 0);
        seeds = Taste.add(seeds, seed("movie:1"), Taste.OPEN, 0);
        assertEquals(2, seeds.size());
        assertEquals("tv:2", seeds.get(0).key);
        assertEquals(2, seeds.get(1).weight, 1e-9);
    }

    @Test
    public void hiddenSeedsAreNotAsked() {
        List<Taste.Seed> seeds = Taste.add(new ArrayList<>(), seed("movie:1"), Taste.LIKE, 0);
        seeds = Taste.add(seeds, seed("movie:2"), Taste.OPEN, 0);
        List<Taste.Seed> asked = Taste.asked(seeds, new HashSet<>(Collections.singletonList("movie:1")), 0);
        assertEquals(1, asked.size());
        assertEquals("movie:2", asked.get(0).key);
    }

    @Test
    public void aTitleSeveralSeedsRecommendWinsAndSeedsAndHiddenAreLeftOut() {
        Map<String, List<Title>> lists = new LinkedHashMap<>();
        lists.put("movie:1", Arrays.asList(title("movie", 10), title("movie", 20), title("movie", 2)));
        lists.put("movie:2", Arrays.asList(title("movie", 30), title("movie", 20), title("tv", 40)));
        Map<String, Double> scores = new HashMap<>();
        scores.put("movie:1", 1.0);
        scores.put("movie:2", 1.0);
        List<Title> ranked = Taste.rank(lists, scores, new HashSet<>(Collections.singletonList("tv:40")));
        assertEquals("movie:20", ranked.get(0).key());
        List<String> keys = new ArrayList<>();
        for (Title t : ranked) keys.add(t.key());
        assertFalse(keys.contains("movie:2"));
        assertFalse(keys.contains("tv:40"));
        assertTrue(keys.contains("movie:10"));
    }
}
