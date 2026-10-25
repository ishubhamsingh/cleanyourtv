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
 * What "For you" learns from, all of it kept on the TV: the titles opened from Home or search (weight 1), the ones
 * liked (3). A seed counts half as much every three weeks, so the row follows what is watched now.
 */
final class Taste {
    static final double OPEN = 1, LIKE = 3;
    static final long HALF_LIFE_MS = 21L * 24 * 3600_000L;
    /** How many seeds are asked for recommendations, and how many are kept at all. */
    static final int ASKED = 5, KEPT = 40;

    static final class Seed {
        String key, kind, title;
        int id;
        double weight;
        long at;
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
        out.add(0, n);
        out.sort((a, b) -> Double.compare(score(b, now), score(a, now)));
        return out.size() > KEPT ? new ArrayList<>(out.subList(0, KEPT)) : out;
    }

    /** The seeds to ask TMDB about: the strongest ASKED, never a hidden one. */
    static List<Seed> asked(List<Seed> seeds, Set<String> hidden, long now) {
        List<Seed> out = new ArrayList<>();
        for (Seed s : seeds) if (!hidden.contains(s.key)) out.add(s);
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
                a.put(new JSONObject().put("key", s.key).put("kind", s.kind).put("id", s.id).put("title", s.title)
                        .put("weight", s.weight).put("at", s.at));
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
            if (!s.key.isEmpty() && s.id != 0) out.add(s);
        }
        return out;
    }
}
