package com.gathin.tvhome.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MixTest {

    private static Title t(int id, String pkg, String lang, Integer... genres) {
        Title t = new Title();
        t.kind = "movie";
        t.id = id;
        t.title = "t" + id;
        t.pkg = pkg;
        t.lang = lang;
        t.genreIds = new ArrayList<>(Arrays.asList(genres));
        return t;
    }

    private static List<Title> netflixHeavy() {
        List<Title> l = new ArrayList<>();
        for (int i = 1; i <= 8; i++) l.add(t(i, "netflix", "en"));
        l.add(t(9, "hotstar", "hi"));
        l.add(t(10, "prime", "en"));
        return l;
    }

    @Test
    public void noAppRunsMoreThanTwiceWhileOthersAreLeft() {
        List<Title> out = Mix.balance(netflixHeavy(), 10);
        assertEquals(10, out.size());
        assertEquals("netflix", out.get(0).pkg);
        assertEquals("netflix", out.get(1).pkg);
        assertTrue(!out.get(2).pkg.equals("netflix"));
    }

    @Test
    public void oneAppKeepsAtMostItsShareOfTheTopWhileOthersAreLeft() {
        List<Title> out = Mix.balance(netflixHeavy(), 10);
        int netflixInFirstSix = 0;
        for (Title t : out.subList(0, 6)) if (t.pkg.equals("netflix")) netflixInFirstSix++;
        assertTrue(netflixInFirstSix <= 4);
        // Nothing is dropped: the rest follows once the others run out.
        assertEquals(8, out.stream().filter(t -> t.pkg.equals("netflix")).count());
    }

    @Test
    public void titlesThatMatchTheTasteMoveUp() {
        Map<Integer, Double> genres = new HashMap<>();
        genres.put(80, 1.0);            // crime
        Map<String, Double> langs = new HashMap<>();
        langs.put("hi", 1.0);
        Taste.Profile taste = new Taste.Profile(genres, langs);
        List<Title> ranked = Arrays.asList(t(1, "a", "en", 35), t(2, "b", "en", 35), t(3, "c", "hi", 80));
        assertEquals(3, Mix.byTaste(ranked, taste).get(0).id);
        assertEquals(1, Mix.byTaste(ranked, new Taste.Profile(new HashMap<>(), new HashMap<>())).get(0).id);
    }

    @Test
    public void roundRobinTakesEachAppInTurnWithoutRepeats() {
        List<Title> netflix = Arrays.asList(t(1, "netflix", "en"), t(2, "netflix", "en"), t(3, "netflix", "en"));
        List<Title> hotstar = Arrays.asList(t(4, "hotstar", "hi"), t(1, "hotstar", "en"));
        List<Title> prime = Collections.singletonList(t(5, "prime", "en"));
        List<Title> out = Mix.roundRobin(Arrays.asList(netflix, hotstar, prime));
        List<Integer> ids = new ArrayList<>();
        for (Title t : out) ids.add(t.id);
        assertEquals(Arrays.asList(1, 4, 5, 2, 3), ids);
    }
}
