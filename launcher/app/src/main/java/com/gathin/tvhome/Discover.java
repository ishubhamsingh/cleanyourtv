package com.gathin.tvhome;

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The launcher's side of Discover (config.xml / theme "discover"): rows of movies and shows under the dock, and
 * search. The launcher has no network access; the data comes from the Discover extension (package com.gathin.tvhome.discover), a
 * separate app with no icon that talks to TMDB and serves the results through a content provider only an app signed
 * with the same key may read. Without the extension, or without its TMDB key, Discover simply shows nothing.
 */
final class Discover {
    static final String AUTHORITY = "com.gathin.tvhome.discover";
    static final Uri ROWS = Uri.parse("content://" + AUTHORITY + "/rows");

    /** One movie or show. */
    static final class Item {
        String key, kind, title, meta, overview, app, pkg, backdrop, poster, elsewhere;
        int tmdbId;
        double rating;
        boolean liked;
        /** Search only: an installed app (its banner or icon, and how to open it) instead of a title. */
        android.graphics.drawable.Drawable appArt;
        Intent appLaunch;

        /** On one of the TV's apps (search can also find titles that aren't). */
        boolean playable() {
            return pkg != null && !pkg.isEmpty();
        }

        /** "Series · Crime · 2024 · Netflix", or where it streams when no app on the TV has it. */
        String line() {
            String where = playable() ? app : elsewhere.isEmpty() ? "" : elsewhere;
            if (where.isEmpty()) return meta;
            return meta.isEmpty() ? where : meta + " · " + where;
        }
    }

    static final class Row {
        final String id, title;
        final List<Item> items;

        Row(String id, String title, List<Item> items) {
            this.id = id;
            this.title = title;
            this.items = items;
        }
    }

    interface Callback<T> {
        void done(T result);
    }

    private static Discover instance;

    static synchronized Discover get(Context c) {
        if (instance == null) instance = new Discover(c.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private final ContentResolver resolver;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService images = Executors.newFixedThreadPool(3);
    /** Decoded artwork by URI, up to 24 MB (a 780-wide backdrop is about 1.2 MB). */
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private Discover(Context c) {
        ctx = c;
        resolver = c.getContentResolver();
    }

    /** The extension is installed and this launcher may read it. */
    boolean available() {
        return ctx.getPackageManager().resolveContentProvider(AUTHORITY, 0) != null
                && ctx.checkSelfPermission("com.gathin.tvhome.permission.DISCOVER") == PackageManager.PERMISSION_GRANTED;
    }

    // MARK: - Rows and search

    /** The rows, read in the background; an empty list without the extension or its key. */
    void rows(Callback<List<Row>> done) {
        io.execute(() -> {
            List<Row> out = new ArrayList<>();
            if (available()) {
                try (Cursor c = resolver.query(ROWS, null, null, null, null)) {
                    while (c != null && c.moveToNext()) {
                        String id = c.getString(0), title = c.getString(1);
                        List<Item> items = items(ROWS.buildUpon().appendPath(id).build());
                        if (!items.isEmpty()) out.add(new Row(id, title, items));
                    }
                } catch (RuntimeException e) {
                    android.util.Log.w("TvHome.Discover", "discover rows: " + e.getMessage());
                }
            }
            main.post(() -> done.done(out));
        });
    }

    /** Titles for a query (network, in the extension); only the newest query's answer is delivered. */
    private volatile int searchSeq;

    void search(String query, Callback<List<Item>> done) {
        int seq = ++searchSeq;
        io.execute(() -> {
            if (seq != searchSeq) return;
            List<Item> out = available()
                    ? items(Uri.parse("content://" + AUTHORITY + "/search").buildUpon().appendQueryParameter("q", query).build())
                    : new ArrayList<>();
            main.post(() -> {
                if (seq == searchSeq) done.done(out);
            });
        });
    }

    private List<Item> items(Uri uri) {
        List<Item> out = new ArrayList<>();
        try (Cursor c = resolver.query(uri, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                Item it = new Item();
                it.key = str(c, "key");
                it.kind = str(c, "kind");
                it.tmdbId = c.getInt(c.getColumnIndexOrThrow("tmdb_id"));
                it.title = str(c, "title");
                it.meta = str(c, "meta");
                it.overview = str(c, "overview");
                it.rating = c.getDouble(c.getColumnIndexOrThrow("rating"));
                it.app = str(c, "app");
                it.pkg = str(c, "pkg");
                it.backdrop = str(c, "backdrop");
                it.poster = str(c, "poster");
                it.elsewhere = str(c, "elsewhere");
                it.liked = c.getInt(c.getColumnIndexOrThrow("liked")) == 1;
                out.add(it);
            }
        } catch (RuntimeException e) {
            android.util.Log.w("TvHome.Discover", "discover items: " + e.getMessage());
        }
        return out;
    }

    private static String str(Cursor c, String column) {
        String s = c.getString(c.getColumnIndexOrThrow(column));
        return s == null ? "" : s;
    }

    /** Calls back on the main thread whenever the extension rebuilt its rows. Unregister with stopWatching. */
    ContentObserver watch(Runnable onChange) {
        ContentObserver o = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                onChange.run();
            }
        };
        try {
            resolver.registerContentObserver(ROWS, true, o);
        } catch (SecurityException e) {
            return null;
        }
        return o;
    }

    void stopWatching(ContentObserver o) {
        if (o != null) resolver.unregisterContentObserver(o);
    }

    // MARK: - What the owner does

    void like(Item it) {
        event("like", it);
        it.liked = true;
    }

    void hide(Item it) {
        event("hide", it);
    }

    /** How much each app is used (Frecency), so the extension can favour the apps opened most. */
    void reportUsage(Map<String, Double> usage) {
        io.execute(() -> {
            if (!available()) return;
            Bundle b = new Bundle();
            for (Map.Entry<String, Double> e : usage.entrySet()) b.putDouble(e.getKey(), e.getValue());
            call("usage", null, b);
        });
    }

    private void event(String type, Item it) {
        io.execute(() -> {
            Bundle b = new Bundle();
            b.putString("key", it.key);
            b.putString("title", it.title);
            call("event", type, b);
        });
    }

    private void call(String method, String arg, Bundle extras) {
        try {
            resolver.call(ROWS, method, arg, extras);
        } catch (RuntimeException e) {
            android.util.Log.w("TvHome.Discover", "discover " + method + ": " + e.getMessage());
        }
    }

    /**
     * Opens the title in its app: the app's own search for the title when it answers one (which usually lands on
     * the title itself), else the app. Records the open for "For you". False when no app on the TV plays it.
     */
    boolean open(Context from, Item it) {
        if (!it.playable()) return false;
        PackageManager pm = ctx.getPackageManager();
        Intent search = new Intent(Intent.ACTION_SEARCH).setPackage(it.pkg)
                .putExtra(SearchManager.QUERY, it.title).putExtra("query", it.title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent launch = pm.getLeanbackLaunchIntentForPackage(it.pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(it.pkg);
        Intent target = searchOpens(it.pkg) && pm.resolveActivity(search, 0) != null ? search : launch;
        if (target == null) return false;
        try {
            from.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            if (target == search && launch != null) from.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else return false;
        }
        event("open", it);
        return true;
    }

    /** Apps whose search screen takes a title from another app. Filled in from tests on the TV. */
    private static boolean searchOpens(String pkg) {
        return true;
    }

    // MARK: - Artwork

    /** TMDB image of the given size ("w780"), by its path ("/abc.jpg"), through the extension. */
    static Uri image(String size, String path) {
        if (path == null || path.isEmpty()) return null;
        return Uri.parse("content://" + AUTHORITY + "/img/" + size + path);
    }

    Bitmap cached(Uri uri) {
        return uri == null ? null : cache.get(uri.toString());
    }

    /** Puts the image into the view when it's ready, unless the view was given another image meanwhile. */
    void load(ImageView view, Uri uri, int widthPx) {
        view.setTag(R.id.discoverImage, uri);
        if (uri == null) {
            view.setImageDrawable(null);
            return;
        }
        Bitmap hit = cache.get(uri.toString());
        if (hit != null) {
            view.setImageBitmap(hit);
            return;
        }
        view.setImageDrawable(null);
        load(uri, widthPx, b -> {
            if (b != null && uri.equals(view.getTag(R.id.discoverImage))) {
                view.setAlpha(0f);
                view.setImageBitmap(b);
                view.animate().alpha(1f).setDuration(180).start();
            }
        });
    }

    void load(Uri uri, int widthPx, Callback<Bitmap> done) {
        images.execute(() -> {
            Bitmap b = decode(uri, widthPx);
            if (b != null) cache.put(uri.toString(), b);
            main.post(() -> done.done(b));
        });
    }

    private Bitmap decode(Uri uri, int widthPx) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            while (bounds.outWidth / (opts.inSampleSize * 2) >= widthPx) opts.inSampleSize *= 2;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = resolver.openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    void trim() {
        cache.evictAll();
    }
}
