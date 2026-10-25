package com.gathin.tvhome.discover;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One movie or show, as the launcher shows it. Movies and shows are numbered separately on TMDB: kind + id is the key. */
final class Title {
    String kind;            // "movie" or "tv"
    int id;
    String title = "", overview = "", year = "", backdrop = "", poster = "";
    double rating, popularity;
    List<Integer> genreIds = new ArrayList<>();
    /** The installed app that plays it (package and label), or null when none of the TV's apps has it. */
    String pkg, app;
    /** Services that have it but whose app isn't on the TV ("Sun NXT"), for search results. */
    List<String> elsewhere = new ArrayList<>();
    String meta = "";

    String key() {
        return kind + ":" + id;
    }

    /** A TMDB list entry (trending, discover, search, recommendations); kind is given for lists of one kind. */
    static Title from(JSONObject o, String kind) {
        String k = kind != null ? kind : o.optString("media_type");
        if (!"movie".equals(k) && !"tv".equals(k)) return null;
        Title t = new Title();
        t.kind = k;
        t.id = o.optInt("id");
        t.title = o.optString(k.equals("movie") ? "title" : "name");
        t.overview = o.optString("overview");
        String date = o.optString(k.equals("movie") ? "release_date" : "first_air_date");
        t.year = date.length() >= 4 ? date.substring(0, 4) : "";
        t.backdrop = path(o.optString("backdrop_path"));
        t.poster = path(o.optString("poster_path"));
        t.rating = o.optDouble("vote_average", 0);
        t.popularity = o.optDouble("popularity", 0);
        JSONArray g = o.optJSONArray("genre_ids");
        for (int i = 0; g != null && i < g.length(); i++) t.genreIds.add(g.optInt(i));
        if (t.id == 0 || t.title.isEmpty()) return null;
        return t;
    }

    private static String path(String p) {
        return p == null || p.isEmpty() || "null".equals(p) ? "" : p;
    }

    JSONObject toJson() {
        try {
            JSONArray g = new JSONArray();
            for (int id : genreIds) g.put(id);
            JSONArray e = new JSONArray();
            for (String s : elsewhere) e.put(s);
            return new JSONObject().put("kind", kind).put("id", id).put("title", title).put("overview", overview)
                    .put("year", year).put("backdrop", backdrop).put("poster", poster).put("rating", rating)
                    .put("popularity", popularity).put("genres", g).put("pkg", pkg == null ? "" : pkg)
                    .put("app", app == null ? "" : app).put("elsewhere", e).put("meta", meta);
        } catch (org.json.JSONException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static Title fromJson(JSONObject o) {
        Title t = new Title();
        t.kind = o.optString("kind");
        t.id = o.optInt("id");
        t.title = o.optString("title");
        t.overview = o.optString("overview");
        t.year = o.optString("year");
        t.backdrop = o.optString("backdrop");
        t.poster = o.optString("poster");
        t.rating = o.optDouble("rating", 0);
        t.popularity = o.optDouble("popularity", 0);
        JSONArray g = o.optJSONArray("genres");
        for (int i = 0; g != null && i < g.length(); i++) t.genreIds.add(g.optInt(i));
        t.pkg = o.optString("pkg").isEmpty() ? null : o.optString("pkg");
        t.app = o.optString("app").isEmpty() ? null : o.optString("app");
        JSONArray e = o.optJSONArray("elsewhere");
        for (int i = 0; e != null && i < e.length(); i++) t.elsewhere.add(e.optString(i));
        t.meta = o.optString("meta");
        return t;
    }
}
