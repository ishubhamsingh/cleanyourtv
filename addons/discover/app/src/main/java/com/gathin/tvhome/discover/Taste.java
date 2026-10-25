package com.gathin.tvhome.discover;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What Discover learns from, all of it kept on the TV: the titles opened from Home or search (weight 1), the ones
 * liked (3), and the ones marked Not interested (-2). A seed counts half as much every three weeks, so it follows what
 * is watched now. Liked and opened titles feed "For you" (TMDB's recommendations for them); all of them, with their
 * genres and original language, make the Profile that re-orders every row.
 */
final class Taste {
    static final double OPEN = 1, LIKE = 3, DISLIKE = -2;
    static final long HALF_LIFE_MS = 21L * 24 * 3600_000L;
    /** How many seeds are asked for recommendations, and how many are kept at all. */
    static final int ASKED = 5, KEPT = 40;

    static final class Seed {
        String key, kind, title;
        int id;
        double weight;
        long at;
        /** TMDB genre ids and original language, or null / "" until known (Catalog fills them in). */
        List<Integer> genres;
        String lang = "";
    }

    /** How much each genre and original language is liked, each scaled to -1..1 by its strongest. */
    static final class Profile {
        final Map<Integer, Double> genres;
        final Map<String, Double> langs;

        Profile(Map<Integer, Double> genres, Map<String, Double> langs) {
            this.genres = genres;
            this.langs = langs;
        }

        boolean isEmpty() {
            return genres.isEmpty() && langs.isEmpty();
        }

        /** -1..1: the mean liking of the title's genres (60 %) and of its language (40 %). */
        double affinity(Title t) {
            double g = 0;
            for (int id : t.genreIds) g += genres.getOrDefault(id, 0d);
            if (!t.genreIds.isEmpty()) g /= t.genreIds.size();
            double l = langs.getOrDefault(t.lang, 0d);
            return Math.max(-1, Math.min(1, 0.6 * g + 0.4 * l));
        }
    }

    /** The profile from every seed whose genres are known, each counted with its current weight. */
    static Profile profile(List<Seed> seeds, long now) {
        Map<Integer, Double> g = new HashMap<>();
        Map<String, Double> l = new HashMap<>();
        for (Seed s : seeds) {
            double w = score(s, now);
            if (s.genres != null) for (int id : s.genres) g.merge(id, w, Double::sum);
            if (s.lang != null && !s.lang.isEmpty()) l.merge(s.lang, w, Double::sum);
        }
        return new Profile(scaled(g), scaled(l));
    }

    private static <K> Map<K, Double> scaled(Map<K, Double> m) {
        double max = 0;
        for (double v : m.values()) max = Math.max(max, Math.abs(v));
        Map<K, Double> out = new HashMap<>();
        if (max == 0) return out;
        for (Map.Entry<K, Double> e : m.entrySet()) out.put(e.getKey(), e.getValue() / max);
        return out;
    }

    private Taste() {
    }

    /** A seed's weight now. */
    static double score(Seed s, long now) {
        return s.weight * Math.pow(0.5, Math.max(0, now - s.at) / (double) HALF_LIFE_MS);
    }

    /** Adds weight to a title (a second open adds up and moves it to now), keeping the KEPT strongest. */
    static List<Seed> add(List<Seed> seeds, Seed s, double weight, long now) {
        List<Seed> out = new ArrayList<>();
        Seed found = null;
        for (Seed x : seeds) {
            if (x.key.equals(s.key)) found = x;
            else out.add(x);
        }
        Seed n = new Seed();
        n.key = s.key;
        n.kind = s.kind;
        n.id = s.id;
        n.title = s.title;
        n.weight = (found == null ? 0 : score(found, now)) + weight;
        n.at = now;
        n.genres = s.genres != null ? s.genres : found == null ? null : found.genres;
        n.lang = s.lang != null && !s.lang.isEmpty() ? s.lang : found == null ? "" : found.lang;
        out.add(0, n);
        // The strongest either way are kept: a firm dislike teaches as much as a like.
        out.sort((a, b) -> Double.compare(Math.abs(score(b, now)), Math.abs(score(a, now))));
        return out.size() > KEPT ? new ArrayList<>(out.subList(0, KEPT)) : out;
    }

    /** The seeds to ask TMDB about: the strongest ASKED, never a hidden one. */
    static List<Seed> asked(List<Seed> seeds, Set<String> hidden, long now) {
        List<Seed> out = new ArrayList<>();
        for (Seed s : seeds) if (s.weight > 0 && !hidden.contains(s.key)) out.add(s);
        out.sort((a, b) -> Double.compare(score(b, now), score(a, now)));
        return out.size() > ASKED ? out.subList(0, ASKED) : out;
    }

    /**
     * Ranks recommendations: each seed's list gives its titles the seed's weight, more near the top of its list, so a
     * title several seeds recommend wins. Seeds themselves and hidden titles are left out.
     *
     * @param lists seed key -> that seed's recommendations, best first
     */
    static List<Title> rank(Map<String, List<Title>> lists, Map<String, Double> seedScores, Set<String> hidden) {
        Map<String, Double> score = new HashMap<>();
        Map<String, Title> byKey = new LinkedHashMap<>();
        for (Map.Entry<String, List<Title>> e : lists.entrySet()) {
            double w = seedScores.getOrDefault(e.getKey(), 0d);
            List<Title> list = e.getValue();
            for (int i = 0; i < list.size(); i++) {
                Title t = list.get(i);
                String k = t.key();
                if (hidden.contains(k) || seedScores.containsKey(k)) continue;
                score.merge(k, w * (1.0 - i / (double) Math.max(list.size(), 1) * 0.5), Double::sum);
                byKey.putIfAbsent(k, t);
            }
        }
        List<Title> out = new ArrayList<>(byKey.values());
        Collections.sort(out, (a, b) -> Double.compare(score.get(b.key()), score.get(a.key())));
        return out;
    }

    static JSONArray toJson(List<Seed> seeds) {
        JSONArray a = new JSONArray();
        try {
            for (Seed s : seeds) {
                JSONObject o = new JSONObject().put("key", s.key).put("kind", s.kind).put("id", s.id).put("title", s.title)
                        .put("weight", s.weight).put("at", s.at).put("lang", s.lang == null ? "" : s.lang);
                if (s.genres != null) {
                    JSONArray g = new JSONArray();
                    for (int id : s.genres) g.put(id);
                    o.put("genres", g);
                }
                a.put(o);
            }
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
        return a;
    }

    static List<Seed> fromJson(JSONArray a) {
        List<Seed> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Seed s = new Seed();
            s.key = o.optString("key");
            s.kind = o.optString("kind");
            s.id = o.optInt("id");
            s.title = o.optString("title");
            s.weight = o.optDouble("weight", 0);
            s.at = o.optLong("at");
            s.lang = o.optString("lang");
            JSONArray g = o.optJSONArray("genres");
            if (g != null) {
                s.genres = new ArrayList<>();
                for (int k = 0; k < g.length(); k++) s.genres.add(g.optInt(k));
            }
            if (!s.key.isEmpty() && s.id != 0) out.add(s);
        }
        return out;
    }
}
