package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import org.json.JSONObject;

/**
 * The Clean Your TV dashboard's Weather card reads the chosen city's weather here (content://...weather.card/now):
 * one row, one column "json" with Forecast.fetchCard's object plus "place" and "updated" (epoch ms), or
 * {"needsPlace": true} before a city is picked. A copy younger than 30 minutes is returned as is; an older one is
 * fetched again first, and kept (with its time) when the fetch fails, so the card can say how old it is.
 * Read only by apps holding com.gathin.tvhome.permission.WEATHER, a signature permission: the launcher built with
 * the same key.
 */
public class CardProvider extends ContentProvider {
    private static final long FRESH_MS = 30 * 60_000L;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        MatrixCursor c = new MatrixCursor(new String[]{"json"});
        c.addRow(new Object[]{read().toString()});
        return c;
    }

    private synchronized JSONObject read() {
        SharedPreferences place = getContext().getSharedPreferences(WeatherActivity.PREFS, android.content.Context.MODE_PRIVATE);
        SharedPreferences cache = getContext().getSharedPreferences("card", android.content.Context.MODE_PRIVATE);
        try {
            if (!place.contains("name")) return new JSONObject().put("needsPlace", true);
            String name = place.getString("name", "");
            long updated = cache.getLong("updated", 0);
            boolean samePlace = name.equals(cache.getString("place", ""));
            // A card cached by an older version has no "days" for the launcher's 3-day panel: fetch again.
            boolean oldFormat = !cache.getString("json", "").contains("\"days\"");
            if (!samePlace || oldFormat || System.currentTimeMillis() - updated > FRESH_MS) {
                try {
                    JSONObject fresh = Forecast.fetchCard(Double.longBitsToDouble(place.getLong("lat", 0)),
                            Double.longBitsToDouble(place.getLong("lon", 0)));
                    updated = System.currentTimeMillis();
                    cache.edit().putString("json", fresh.toString()).putLong("updated", updated).putString("place", name).apply();
                } catch (Exception offline) {
                    if (!samePlace || !cache.contains("json")) return new JSONObject().put("place", name).put("offline", true);
                }
            }
            return new JSONObject(cache.getString("json", "{}")).put("place", name).put("updated", updated);
        } catch (org.json.JSONException e) {
            return new JSONObject();
        }
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
