package com.gathin.tvhome.discover;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the launcher reads, over Android's content provider (the launcher itself has no network access):
 *
 * <pre>
 * content://com.gathin.tvhome.discover/rows            row_id, title
 * content://com.gathin.tvhome.discover/rows/&lt;row_id&gt;   the row's titles (ITEM_COLUMNS)
 * content://com.gathin.tvhome.discover/search?q=...    titles for a query, the ones on the TV's apps first
 * content://com.gathin.tvhome.discover/img/&lt;size&gt;/&lt;file&gt;  an image (openFile)
 * call("status")                                      configured, error, built_at
 * call("event", type, {key, title})                   type: open, like, hide
 * call("usage", null, {package: score})               how much each app is used, from the launcher
 * call("link", null, {key, pkg})                      the title's address on that app (Watchmode), or none
 * call("refresh")                                     rebuild the rows now
 * </pre>
 *
 * Every entry point needs com.gathin.tvhome.permission.DISCOVER (signature): only the launcher signed with the same
 * key gets in. When the rows change, a change is notified on .../rows.
 */
public final class DiscoverProvider extends ContentProvider {
    static final String AUTHORITY = "com.gathin.tvhome.discover";
    static final String PERMISSION = "com.gathin.tvhome.permission.DISCOVER";
    static final Uri ROWS = Uri.parse("content://" + AUTHORITY + "/rows");
    static final String[] ROW_COLUMNS = {"row_id", "title"};
    static final String[] ITEM_COLUMNS = {"key", "kind", "tmdb_id", "title", "meta", "overview", "rating",
            "app", "pkg", "backdrop", "poster", "elsewhere", "liked"};

    private Catalog catalog;

    @Override
    public boolean onCreate() {
        return true;
    }

    private Catalog catalog() {
        if (catalog == null) {
            catalog = Catalog.get(getContext(), () -> getContext().getContentResolver().notifyChange(ROWS, null));
        }
        return catalog;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        List<String> seg = uri.getPathSegments();
        if (seg.isEmpty()) return null;
        switch (seg.get(0)) {
            case "rows": {
                if (seg.size() == 1) {
                    MatrixCursor c = new MatrixCursor(ROW_COLUMNS);
                    for (Catalog.Row r : catalog().rows()) c.addRow(new Object[]{r.id, r.title});
                    c.setNotificationUri(getContext().getContentResolver(), ROWS);
                    return c;
                }
                Catalog.Row row = catalog().row(seg.get(1));
                return items(row == null ? java.util.Collections.emptyList() : row.items);
            }
            case "search":
                try {
                    return items(catalog().search(uri.getQueryParameter("q")));
                } catch (Exception e) {
                    Log.w("Discover", "search: " + e.getMessage());
                    MatrixCursor c = new MatrixCursor(ITEM_COLUMNS);
                    Bundle extras = new Bundle();
                    extras.putString("error", e.getMessage());
                    c.setExtras(extras);
                    return c;
                }
            default:
                return null;
        }
    }

    private Cursor items(List<Title> titles) {
        MatrixCursor c = new MatrixCursor(ITEM_COLUMNS);
        for (Title t : titles) {
            c.addRow(new Object[]{t.key(), t.kind, t.id, t.title, t.meta, t.overview, t.rating,
                    t.app == null ? "" : t.app, t.pkg == null ? "" : t.pkg, t.backdrop, t.poster,
                    String.join(", ", t.elsewhere), catalog().isLiked(t.key()) ? 1 : 0});
        }
        return c;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        List<String> seg = uri.getPathSegments();
        if (seg.size() != 3 || !"img".equals(seg.get(0)) || !"r".equals(mode)) throw new FileNotFoundException(uri.toString());
        try {
            File f = catalog().image(seg.get(1), "/" + seg.get(2));
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        // Unlike query and openFile, call() is not covered by the manifest's read/write permissions.
        getContext().enforceCallingOrSelfPermission(PERMISSION, "Discover");
        Catalog c = catalog();
        Bundle out = new Bundle();
        switch (method) {
            case "status":
                out.putBoolean("configured", c.configured());
                out.putString("error", c.error());
                out.putLong("built_at", c.builtAt());
                return out;
            case "event": {
                String key = extras == null ? null : extras.getString("key");
                String title = extras == null ? null : extras.getString("title");
                if (key == null) return out;
                if ("open".equals(arg)) c.opened(key, title);
                else if ("like".equals(arg)) c.liked(key, title);
                else if ("hide".equals(arg)) c.hide(key);
                return out;
            }
            case "usage": {
                Map<String, Double> usage = new HashMap<>();
                if (extras != null) {
                    for (String k : extras.keySet()) usage.put(k, extras.getDouble(k, 0));
                }
                c.setUsage(usage);
                return out;
            }
            case "link":
                out.putString("url", extras == null ? null : c.link(extras.getString("key"), extras.getString("pkg")));
                return out;
            case "refresh":
                c.rebuild();
                return out;
            default:
                return out;
        }
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
