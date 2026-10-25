package com.gathin.tvhome.discover;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Discover's catalogue: the rows the launcher shows under the dock, search, and what the TV's owner opened, liked and
 * hid. Only titles on a streaming app installed on the TV make it into a row. Everything is cached on the TV; the rows
 * are rebuilt in the background when they are older than REBUILD_MS, the installed apps or the most used app changed,
 * or a like or a hide asks for it.
 */
final class Catalog {
    private static final String TAG = "Discover";
    static final long REBUILD_MS = 4 * 3600_000L;
    private static final long WHERE_MS = 3 * 24 * 3600_000L, IDS_MS = 7 * 24 * 3600_000L;
    private static final int ROW_MAX = 20;
    /** Bump when what is saved for the rows changes: the saved rows are thrown away and built again. */
    private static final int VERSION = 1;
    private static final Pattern IMAGE_PATH = Pattern.compile("/[A-Za-z0-9_\\-]+\\.(jpg|png)");
    static final List<String> SIZES = Arrays.asList("w300", "w780", "w1280");

    static final class Row {
        final String id, title;
        final List<Title> items;

        Row(String id, String title, List<Title> items) {
            this.id = id;
            this.title = title;
            this.items = items;
        }
    }

    interface Changed {
        void rowsChanged();
    }

    private static Catalog instance;

    static synchronized Catalog get(Context c, Changed changed) {
        if (instance == null) instance = new Catalog(c.getApplicationContext(), changed);
        return instance;
    }

    private final Context ctx;
    private final Changed changed;
    private final Tmdb tmdb = new Tmdb(BuildConfig.TMDB_KEY, BuildConfig.REGION, BuildConfig.LANGUAGE);
    private final SharedPreferences prefs;
    private final ExecutorService builder = Executors.newSingleThreadExecutor();
    /** Where-to-watch lookups and image downloads, a few at a time. */
    private final ExecutorService lookups = Executors.newFixedThreadPool(4);
    private final AtomicBoolean building = new AtomicBoolean();
    private volatile List<Row> rows = new ArrayList<>();
    private volatile long builtAt;
    private volatile String builtFor = "";
    private volatile String error = "";
    private volatile boolean dirty;
    /** title key -> {at, names of the services that stream it here} */
    private final Map<String, JSONObject> where = new HashMap<>();
    private final Map<Integer, String> genres = new HashMap<>();

    private Catalog(Context c, Changed changed) {
        ctx = c;
        this.changed = changed;
        prefs = c.getSharedPreferences("discover", Context.MODE_PRIVATE);
        loadSaved();
    }

    boolean configured() {
        return tmdb.configured();
    }

    String error() {
        return error;
    }

    long builtAt() {
        return builtAt;
    }

    // MARK: - Rows

    /** The rows as saved; starts a rebuild in the background when they are stale (rowsChanged follows). */
    List<Row> rows() {
        if (configured() && (dirty || System.currentTimeMillis() - builtAt > REBUILD_MS || builtAt > System.currentTimeMillis()
                || !builtFor.equals(signature()))) rebuild();
        return rows;
    }

    Row row(String id) {
        for (Row r : rows()) if (r.id.equals(id)) return r;
        return null;
    }

    void rebuild() {
        if (!configured() || !building.compareAndSet(false, true)) return;
        builder.execute(() -> {
            try {
                dirty = false;
                String sig = signature();
                List<Row> fresh = build();
                rows = fresh;
                builtAt = System.currentTimeMillis();
                builtFor = sig;
                error = "";
                save();
                changed.rowsChanged();
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
                Log.w(TAG, "rows: " + error);
            } finally {
                building.set(false);
            }
        });
    }

    /** What the rows depend on besides time: the installed services, the most used one and the country. */
    private String signature() {
        StringBuilder sb = new StringBuilder(tmdb.region).append('|').append(VERSION);
        for (String[] s : installed()) sb.append('|').append(s[0]);
        String top = topService();
        sb.append("|top=").append(top == null ? "" : top);
        return sb.toString();
    }

    private List<Row> build() throws Exception {
        List<String[]> services = installed();
        if (services.isEmpty()) throw new IOException("none of the TV's streaming apps are known to Discover");
        Map<Integer, String[]> ids = providerIds(services);
        loadGenres();
        Set<String> hidden = hidden();
        Set<String> seen = new HashSet<>(hidden);
        List<Row> out = new ArrayList<>();

        // 1. For you: recommendations from what was opened and liked. Before there is anything, the most used app.
        List<Taste.Seed> seeds = Taste.asked(seeds(), hidden, System.currentTimeMillis());
        if (!seeds.isEmpty()) {
            Map<String, List<Title>> lists = new LinkedHashMap<>();
            Map<String, Double> scores = new HashMap<>();
            long now = System.currentTimeMillis();
            for (Taste.Seed s : seeds) {
                scores.put(s.key, Taste.score(s, now));
                try {
                    lists.put(s.key, list(tmdb.get("/" + s.kind + "/" + s.id + "/recommendations?page=1"), s.kind));
                } catch (IOException e) {
                    Log.w(TAG, "recommendations: " + e.getMessage());
                }
            }
            addRow(out, seen, "for_you", "For you", available(Taste.rank(lists, scores, hidden), 40));
        }

        // 2. Trending this week, on the TV's apps.
        List<Title> movies = new ArrayList<>(), shows = new ArrayList<>();
        for (int page = 1; page <= 2; page++) {
            movies.addAll(list(tmdb.get("/trending/movie/week?page=" + page), "movie"));
            shows.addAll(list(tmdb.get("/trending/tv/week?page=" + page), "tv"));
        }
        addRow(out, seen, "trending", "Trending on your apps", available(alternate(movies, shows), 60));

        // 3. Popular on the most used app (the launcher reports which apps are opened most).
        String top = topService();
        String[] topService = null;
        for (String[] s : services) if (s[0].equals(top)) topService = s;
        if (topService == null && !services.isEmpty()) topService = services.get(0);
        Integer topId = null;
        for (Map.Entry<Integer, String[]> e : ids.entrySet()) if (e.getValue() == topService) topId = e.getKey();
        if (topService != null && topId != null) {
            String common = "&watch_region=" + tmdb.region + "&with_watch_providers=" + topId
                    + "&with_watch_monetization_types=flatrate%7Cfree%7Cads&sort_by=popularity.desc&include_adult=false";
            List<Title> m = list(tmdb.get("/discover/movie?vote_count.gte=50" + common), "movie");
            List<Title> t = list(tmdb.get("/discover/tv?vote_count.gte=30" + common), "tv");
            List<Title> both = alternate(m, t);
            for (Title x : both) {
                x.pkg = packageOf(topService);
                x.app = topService[1];
                describe(x);
            }
            String title = seeds.isEmpty() ? "Popular on " + topService[1] : "Top on " + topService[1];
            addRow(out, seen, "top_app", title, both);
        }

        // 4. New on the TV's apps: films from the last four months, shows with an episode in the last two.
        if (!ids.isEmpty()) {
            StringBuilder all = new StringBuilder();
            for (int id : ids.keySet()) all.append(all.length() == 0 ? "" : "%7C").append(id);
            String common = "&watch_region=" + tmdb.region + "&with_watch_providers=" + all
                    + "&with_watch_monetization_types=flatrate%7Cfree%7Cads&sort_by=popularity.desc&include_adult=false";
            List<Title> m = list(tmdb.get("/discover/movie?vote_count.gte=10&primary_release_date.gte=" + day(-120)
                    + "&primary_release_date.lte=" + day(0) + common), "movie");
            List<Title> t = list(tmdb.get("/discover/tv?vote_count.gte=5&air_date.gte=" + day(-60)
                    + "&air_date.lte=" + day(0) + common), "tv");
            addRow(out, seen, "new", "New on your apps", available(alternate(m, t), 40));
        }
        saveWhere();
        return out;
    }

    private static void addRow(List<Row> out, Set<String> seen, String id, String title, List<Title> items) {
        List<Title> keep = new ArrayList<>();
        for (Title t : items) {
            if (keep.size() >= ROW_MAX) break;
            if (t.pkg != null && !t.backdrop.isEmpty() && seen.add(t.key())) keep.add(t);
        }
        if (keep.size() >= 3) out.add(new Row(id, title, keep));
    }

    private static List<Title> alternate(List<Title> a, List<Title> b) {
        List<Title> out = new ArrayList<>();
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            if (i < a.size()) out.add(a.get(i));
            if (i < b.size()) out.add(b.get(i));
        }
        return out;
    }

    private List<Title> list(JSONObject page, String kind) {
        List<Title> out = new ArrayList<>();
        JSONArray results = page.optJSONArray("results");
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject o = results.optJSONObject(i);
            if (o == null || o.optBoolean("adult")) continue;
            Title t = Title.from(o, kind);
            if (t != null) out.add(t);
        }
        return out;
    }

    /** The first `max` titles, each looked up for which installed app plays it, in order; titles no app has stay in. */
    private List<Title> available(List<Title> titles, int max) throws Exception {
        List<Title> some = titles.size() > max ? titles.subList(0, max) : titles;
        List<Future<?>> jobs = new ArrayList<>();
        for (Title t : some) jobs.add(lookups.submit(() -> {
            whereToWatch(t);
            return null;
        }));
        for (Future<?> f : jobs) {
            try {
                f.get();
            } catch (java.util.concurrent.ExecutionException e) {
                // One title failing leaves it without an app; the rest of the row is fine.
            }
        }
        return some;
    }

    // MARK: - Where to watch

    /** Sets t.pkg / t.app to the installed app that plays it (the most used one when several do), and t.elsewhere. */
    private void whereToWatch(Title t) throws IOException {
        JSONArray names = null;
        synchronized (where) {
            JSONObject cached = where.get(t.key());
            if (cached != null && System.currentTimeMillis() - cached.optLong("at") < WHERE_MS) names = cached.optJSONArray("names");
        }
        if (names == null) {
            JSONObject res = tmdb.get("/" + t.kind + "/" + t.id + "/watch/providers").optJSONObject("results");
            JSONObject here = res == null ? null : res.optJSONObject(tmdb.region);
            names = new JSONArray();
            Set<String> added = new HashSet<>();
            for (String type : new String[]{"flatrate", "free", "ads"}) {
                JSONArray list = here == null ? null : here.optJSONArray(type);
                for (int i = 0; list != null && i < list.length(); i++) {
                    String name = list.optJSONObject(i).optString("provider_name");
                    if (added.add(name)) names.put(name);
                }
            }
            synchronized (where) {
                try {
                    where.put(t.key(), new JSONObject().put("at", System.currentTimeMillis()).put("names", names));
                } catch (org.json.JSONException ignored) {
                }
            }
        }
        Map<String, Double> usage = usage();
        double best = -1;
        t.pkg = null;
        t.app = null;
        t.elsewhere.clear();
        for (int i = 0; i < names.length(); i++) {
            String[] s = Services.find(names.optString(i));
            String pkg = s == null ? null : packageOf(s);
            if (pkg == null) {
                String name = s == null ? names.optString(i) : s[1];
                if (!t.elsewhere.contains(name)) t.elsewhere.add(name);
                continue;
            }
            double u = usage.getOrDefault(pkg, 0d);
            if (u > best) {
                best = u;
                t.pkg = pkg;
                t.app = s[1];
            }
        }
        describe(t);
    }

    /** "Series · Crime · 2024", and the app it's on. */
    private void describe(Title t) {
        List<String> parts = new ArrayList<>();
        parts.add(t.kind.equals("tv") ? "Series" : "Movie");
        if (!t.genreIds.isEmpty()) {
            String g = genres.get(t.genreIds.get(0));
            if (g != null) parts.add(g);
        }
        if (!t.year.isEmpty()) parts.add(t.year);
        t.meta = String.join(" · ", parts);
    }

    /** TMDB's provider ids in this country for the installed services. */
    private Map<Integer, String[]> providerIds(List<String[]> services) throws IOException {
        JSONObject saved = json(prefs.getString("ids", null));
        if (saved == null || System.currentTimeMillis() - saved.optLong("at") > IDS_MS
                || !tmdb.region.equals(saved.optString("region"))) {
            JSONArray all = new JSONArray();
            for (String kind : new String[]{"movie", "tv"}) {
                JSONArray r = tmdb.get("/watch/providers/" + kind + "?watch_region=" + tmdb.region).optJSONArray("results");
                for (int i = 0; r != null && i < r.length(); i++) all.put(r.optJSONObject(i));
            }
            try {
                saved = new JSONObject().put("at", System.currentTimeMillis()).put("region", tmdb.region).put("list", all);
            } catch (org.json.JSONException e) {
                throw new IOException(e);
            }
            prefs.edit().putString("ids", saved.toString()).apply();
        }
        Map<Integer, String[]> out = new HashMap<>();
        JSONArray list = saved.optJSONArray("list");
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            String[] s = o == null ? null : Services.find(o.optString("provider_name"));
            if (s != null && services.contains(s)) out.put(o.optInt("provider_id"), s);
        }
        return out;
    }

    private void loadGenres() {
        if (!genres.isEmpty()) return;
        JSONObject saved = json(prefs.getString("genres", null));
        if (saved == null || System.currentTimeMillis() - saved.optLong("at") > IDS_MS) {
            try {
                JSONArray all = new JSONArray();
                for (String kind : new String[]{"movie", "tv"}) {
                    JSONArray g = tmdb.get("/genre/" + kind + "/list").optJSONArray("genres");
                    for (int i = 0; g != null && i < g.length(); i++) all.put(g.optJSONObject(i));
                }
                saved = new JSONObject().put("at", System.currentTimeMillis()).put("list", all);
                prefs.edit().putString("genres", saved.toString()).apply();
            } catch (Exception e) {
                Log.w(TAG, "genres: " + e.getMessage());
                return;
            }
        }
        JSONArray list = saved.optJSONArray("list");
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o != null) genres.put(o.optInt("id"), shortGenre(o.optString("name")));
        }
    }

    /** TMDB's TV genres come in pairs ("Action & Adventure"): the first word reads better on one line. */
    static String shortGenre(String name) {
        switch (name) {
            case "Action & Adventure": return "Action";
            case "Sci-Fi & Fantasy": return "Sci-Fi";
            case "War & Politics": return "War";
            case "Science Fiction": return "Sci-Fi";
            default: return name;
        }
    }

    // MARK: - Search

    /** Movies and shows for a query, the ones on the TV's apps first. Runs on the caller's thread (network). */
    List<Title> search(String query) throws Exception {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty() || !configured()) return new ArrayList<>();
        loadGenres();
        JSONArray results = tmdb.get("/search/multi?include_adult=false&page=1&query=" + Tmdb.enc(q)).optJSONArray("results");
        List<Title> found = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int i = 0; results != null && i < results.length() && found.size() < 24; i++) {
            JSONObject o = results.optJSONObject(i);
            if (o == null) continue;
            if ("person".equals(o.optString("media_type"))) {
                // An actor or director: what they are known for.
                JSONArray known = o.optJSONArray("known_for");
                for (int k = 0; known != null && k < known.length(); k++) {
                    Title t = Title.from(known.optJSONObject(k), null);
                    if (t != null && keys.add(t.key())) found.add(t);
                }
                continue;
            }
            Title t = Title.from(o, null);
            if (t != null && keys.add(t.key())) found.add(t);
        }
        Set<String> hidden = hidden();
        found.removeIf(t -> hidden.contains(t.key()));
        available(found, 24);
        saveWhere();
        List<Title> out = new ArrayList<>();
        for (Title t : found) if (t.pkg != null) out.add(t);
        for (Title t : found) if (t.pkg == null) out.add(t);
        return out;
    }

    // MARK: - What the owner does

    void opened(String key, String title) {
        learn(key, title, Taste.OPEN);
    }

    void liked(String key, String title) {
        learn(key, title, Taste.LIKE);
        dirty = true;
        rebuild();
    }

    void hide(String key) {
        Set<String> hidden = new HashSet<>(hidden());
        hidden.add(key);
        prefs.edit().putStringSet("hidden", hidden).apply();
        // Gone from the rows right away; For you is rebuilt without it.
        List<Row> kept = new ArrayList<>();
        for (Row r : rows) {
            List<Title> items = new ArrayList<>(r.items);
            items.removeIf(t -> t.key().equals(key));
            kept.add(new Row(r.id, r.title, items));
        }
        rows = kept;
        save();
        changed.rowsChanged();
        dirty = true;
        rebuild();
    }

    boolean isLiked(String key) {
        for (Taste.Seed s : seeds()) if (s.key.equals(key) && s.weight >= Taste.LIKE) return true;
        return false;
    }

    private void learn(String key, String title, double weight) {
        int colon = key == null ? -1 : key.indexOf(':');
        if (colon <= 0) return;
        Taste.Seed s = new Taste.Seed();
        s.key = key;
        s.kind = key.substring(0, colon);
        try {
            s.id = Integer.parseInt(key.substring(colon + 1));
        } catch (NumberFormatException e) {
            return;
        }
        s.title = title == null ? "" : title;
        List<Taste.Seed> seeds = Taste.add(seeds(), s, weight, System.currentTimeMillis());
        prefs.edit().putString("seeds", Taste.toJson(seeds).toString()).apply();
        // A new seed changes For you, but there's no hurry: it is picked up at the next stale rebuild or like.
        if (weight >= Taste.LIKE) dirty = true;
    }

    /** How much each app is used, from the launcher (its grid_order Frecency scores). */
    void setUsage(Map<String, Double> usage) {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, Double> e : usage.entrySet()) o.put(e.getKey(), e.getValue());
        } catch (org.json.JSONException ignored) {
        }
        String before = topService();
        prefs.edit().putString("usage", o.toString()).apply();
        String after = topService();
        if (after != null && !after.equals(before)) rebuild();
    }

    private Map<String, Double> usage() {
        Map<String, Double> out = new HashMap<>();
        JSONObject o = json(prefs.getString("usage", null));
        if (o == null) return out;
        for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            out.put(k, o.optDouble(k, 0));
        }
        return out;
    }

    /** The match key of the installed service the owner opens most, or null before the launcher said. */
    private String topService() {
        Map<String, Double> usage = usage();
        String best = null;
        double bestScore = 0;
        for (String[] s : installed()) {
            String pkg = packageOf(s);
            double u = usage.getOrDefault(pkg, 0d);
            if (u > bestScore) {
                bestScore = u;
                best = s[0];
            }
        }
        return best;
    }

    private List<Taste.Seed> seeds() {
        try {
            return Taste.fromJson(new JSONArray(prefs.getString("seeds", "[]")));
        } catch (org.json.JSONException e) {
            return new ArrayList<>();
        }
    }

    private Set<String> hidden() {
        return new HashSet<>(prefs.getStringSet("hidden", Collections.emptySet()));
    }

    // MARK: - Installed apps

    /** The Services rows with an app on the TV. */
    private List<String[]> installed() {
        List<String[]> out = new ArrayList<>();
        for (String[] s : Services.ALL) if (packageOf(s) != null) out.add(s);
        return out;
    }

    /** The service's first package installed and enabled on the TV, or null. */
    private String packageOf(String[] s) {
        PackageManager pm = ctx.getPackageManager();
        for (int i = 2; i < s.length; i++) {
            if (pm.getLeanbackLaunchIntentForPackage(s[i]) != null || pm.getLaunchIntentForPackage(s[i]) != null) return s[i];
        }
        return null;
    }

    // MARK: - Images

    /** The image on disk, downloaded first if needed. Only TMDB paths in the sizes Discover uses. */
    File image(String size, String path) throws IOException {
        if (!SIZES.contains(size) || path == null || !IMAGE_PATH.matcher(path).matches()) throw new IOException("bad image");
        File dir = new File(ctx.getCacheDir(), "img");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, size + "_" + path.substring(1));
        if (!f.exists()) {
            Tmdb.download(size, path, f);
            prune(dir);
        } else {
            //noinspection ResultOfMethodCallIgnored
            f.setLastModified(System.currentTimeMillis());
        }
        return f;
    }

    private static void prune(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= 240) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.length - 200; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    // MARK: - Saved state

    private void save() {
        try {
            JSONArray a = new JSONArray();
            for (Row r : rows) {
                JSONArray items = new JSONArray();
                for (Title t : r.items) items.put(t.toJson());
                a.put(new JSONObject().put("id", r.id).put("title", r.title).put("items", items));
            }
            JSONObject o = new JSONObject().put("version", VERSION).put("at", builtAt).put("for", builtFor).put("rows", a);
            write(new File(ctx.getFilesDir(), "rows.json"), o.toString());
        } catch (Exception e) {
            Log.w(TAG, "save: " + e.getMessage());
        }
    }

    private void saveWhere() {
        try {
            JSONObject o = new JSONObject();
            long now = System.currentTimeMillis();
            synchronized (where) {
                for (Map.Entry<String, JSONObject> e : where.entrySet()) {
                    if (now - e.getValue().optLong("at") < WHERE_MS) o.put(e.getKey(), e.getValue());
                }
            }
            write(new File(ctx.getFilesDir(), "where.json"), o.toString());
        } catch (Exception e) {
            Log.w(TAG, "save where: " + e.getMessage());
        }
    }

    private void loadSaved() {
        JSONObject o = json(readFile(new File(ctx.getFilesDir(), "rows.json")));
        if (o != null && o.optInt("version") == VERSION) {
            List<Row> saved = new ArrayList<>();
            JSONArray a = o.optJSONArray("rows");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject r = a.optJSONObject(i);
                List<Title> items = new ArrayList<>();
                JSONArray its = r.optJSONArray("items");
                for (int k = 0; its != null && k < its.length(); k++) items.add(Title.fromJson(its.optJSONObject(k)));
                saved.add(new Row(r.optString("id"), r.optString("title"), items));
            }
            rows = saved;
            builtAt = o.optLong("at");
            builtFor = o.optString("for");
        }
        JSONObject w = json(readFile(new File(ctx.getFilesDir(), "where.json")));
        if (w != null) {
            for (java.util.Iterator<String> it = w.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONObject v = w.optJSONObject(k);
                if (v != null) where.put(k, v);
            }
        }
    }

    private static JSONObject json(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return new JSONObject(s);
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    private static String readFile(File f) {
        if (!f.exists()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            return Tmdb.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    private static void write(File f, String text) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(f)) throw new IOException("couldn't save " + f.getName());
    }

    /** The date `offset` days from today, "2026-10-25". */
    static String day(int offset) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, offset);
        return String.format(Locale.US, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
    }
}
