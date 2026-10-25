package com.gathin.tvhome.discover;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Predicate;

/**
 * Watchmode (api.watchmode.com, optional free key): the web address of a title on a streaming service in a country,
 * which the service's TV app opens on the title itself (Netflix, JioHotstar, Apple TV, YouTube take their own
 * https links). Two requests the first time a title is opened; Catalog remembers the answer.
 */
final class Watchmode {
    static final String API = "https://api.watchmode.com/v1";

    private final String key;
    final String region;

    Watchmode(String key, String region) {
        this.key = key == null ? "" : key.trim();
        this.region = region;
    }

    boolean configured() {
        return !key.isEmpty();
    }

    /** The title's address on the service `isService` accepts (by Watchmode's name), or null when it has none. */
    String link(String kind, int tmdbId, Predicate<String> isService) throws IOException {
        JSONObject found = get("/search/?search_field=" + (kind.equals("tv") ? "tmdb_tv_id" : "tmdb_movie_id")
                + "&search_value=" + tmdbId);
        JSONArray results = found.optJSONArray("title_results");
        if (results == null || results.length() == 0) return null;
        int id = results.optJSONObject(0).optInt("id");
        JSONArray sources = getArray("/title/" + id + "/sources/?regions=" + Tmdb.enc(region));
        return pick(sources, region, isService);
    }

    /**
     * The first subscription or free offer in the country, on the wanted service, with an https address. Rentals and
     * purchases are left out: the poster said the title is on the app.
     */
    static String pick(JSONArray sources, String region, Predicate<String> isService) {
        for (int i = 0; sources != null && i < sources.length(); i++) {
            JSONObject s = sources.optJSONObject(i);
            if (s == null || !region.equalsIgnoreCase(s.optString("region"))) continue;
            String type = s.optString("type");
            if (!type.equals("sub") && !type.equals("free")) continue;
            String url = s.optString("web_url");
            if (url.startsWith("https://") && isService.test(s.optString("name"))) return url;
        }
        return null;
    }

    private JSONObject get(String path) throws IOException {
        try {
            return new JSONObject(fetch(path));
        } catch (org.json.JSONException e) {
            throw new IOException("Watchmode sent something that isn't JSON");
        }
    }

    private JSONArray getArray(String path) throws IOException {
        try {
            return new JSONArray(fetch(path));
        } catch (org.json.JSONException e) {
            throw new IOException("Watchmode sent something that isn't a list");
        }
    }

    /** The key goes in the address (Watchmode's way); it never goes to the log or into an error message. */
    private String fetch(String path) throws IOException {
        if (!configured()) throw new IOException("no Watchmode key");
        HttpURLConnection c = (HttpURLConnection) new URL(API + path + (path.contains("?") ? "&" : "?")
                + "apiKey=" + Tmdb.enc(key)).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(6000);
        try {
            int code = c.getResponseCode();
            if (code == 401) throw new IOException("Watchmode rejected the key");
            if (code == 429) throw new IOException("Watchmode quota or rate limit reached");
            if (code != 200) throw new IOException("Watchmode returned HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                return Tmdb.read(in);
            }
        } finally {
            c.disconnect();
        }
    }
}
