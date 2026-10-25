package com.gathin.tvhome.discover;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The last step for every row: titles that match the owner's taste move up (Taste.Profile), then the apps are mixed so
 * one service can't fill a row: at most MAX_RUN of an app's titles in a row, and no app above MAX_SHARE of the row,
 * keeping the order otherwise.
 */
final class Mix {
    static final int MAX_RUN = 2;
    static final double MAX_SHARE = 0.4;
    /** How much a perfect taste match counts against list order (a title's base score falls from 1 down the list). */
    static final double TASTE_WEIGHT = 0.6;

    private Mix() {
    }

    /** The list re-ordered by taste: base score 1 / (1 + 0.08 * rank), plus TASTE_WEIGHT * affinity (0 to 1). */
    static List<Title> byTaste(List<Title> ranked, Taste.Profile taste) {
        if (taste == null || taste.isEmpty()) return new ArrayList<>(ranked);
        Map<Title, Double> score = new HashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            Title t = ranked.get(i);
            score.put(t, 1.0 / (1.0 + 0.08 * i) + TASTE_WEIGHT * taste.affinity(t));
        }
        List<Title> out = new ArrayList<>(ranked);
        out.sort((a, b) -> Double.compare(score.get(b), score.get(a)));
        return out;
    }

    /**
     * Picks, place by place, the best remaining title whose app has had fewer than MAX_RUN titles just before it and
     * is under its share of `size`; when every remaining one would break a rule, the best remaining. Titles without an
     * app count as their own.
     */
    static List<Title> balance(List<Title> ranked, int size) {
        List<Title> left = new ArrayList<>(ranked);
        List<Title> out = new ArrayList<>();
        Map<String, Integer> count = new HashMap<>();
        int cap = Math.max(MAX_RUN, (int) Math.ceil(MAX_SHARE * Math.min(size, ranked.size())));
        while (!left.isEmpty()) {
            int pick = 0;
            for (int i = 0; i < left.size(); i++) {
                String app = app(left.get(i));
                if (runOf(out, app) < MAX_RUN && count.getOrDefault(app, 0) < cap) {
                    pick = i;
                    break;
                }
            }
            Title t = left.remove(pick);
            count.merge(app(t), 1, Integer::sum);
            out.add(t);
        }
        return out;
    }

    private static String app(Title t) {
        return t.pkg == null ? "none:" + t.key() : t.pkg;
    }

    /** How many titles of this app end the list. */
    private static int runOf(List<Title> out, String app) {
        int n = 0;
        for (int i = out.size() - 1; i >= 0 && app(out.get(i)).equals(app); i--) n++;
        return n;
    }

    /** Takes one title from each list in turn (each list's own order kept), skipping titles already taken. */
    static List<Title> roundRobin(List<List<Title>> lists) {
        List<Title> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        int longest = 0;
        for (List<Title> l : lists) longest = Math.max(longest, l.size());
        for (int i = 0; i < longest; i++) {
            for (List<Title> l : lists) {
                if (i < l.size() && seen.add(l.get(i).key())) out.add(l.get(i));
            }
        }
        return out;
    }
}
