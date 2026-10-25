package com.gathin.tvhome.discover;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The few TMDB calls Discover makes. The key is a read access token (starts with "eyJ", sent as a Bearer header) or a
 * v3 API key (sent as api_key); it never goes to the log or into an error message.
 */
final class Tmdb {
    static final String API = "https://api.themoviedb.org/3";
    static final String IMAGES = "https://image.tmdb.org/t/p/";

    private final String key;
    final String region, language;

    Tmdb(String key, String region, String language) {
        this.key = key == null ? "" : key.trim();
        this.region = region;
        this.language = language;
    }

    boolean configured() {
        return !key.isEmpty();
    }

    JSONObject get(String path) throws IOException {
        if (!configured()) throw new IOException("no TMDB key");
        boolean bearer = key.startsWith("eyJ");
        String sep = path.contains("?") ? "&" : "?";
        // The country goes with every call: region (release dates, search ranking) as well as watch_region where
        // a call asks what streams where.
        String url = API + path + sep + "language=" + enc(language) + "&region=" + enc(region)
                + (bearer ? "" : "&api_key=" + enc(key));
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Accept", "application/json");
        if (bearer) c.setRequestProperty("Authorization", "Bearer " + key);
        try {
            int code = c.getResponseCode();
            if (code == 401) throw new IOException("TMDB rejected the key");
            if (code == 429) throw new IOException("TMDB says too many requests");
            if (code != 200) throw new IOException("TMDB returned HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                return new JSONObject(read(in));
            } catch (JSONException e) {
                throw new IOException("TMDB sent something that isn't JSON");
            }
        } finally {
            c.disconnect();
        }
    }

    /** Downloads one image (size "w780", path "/abc.jpg") to a file, through a .part file so a cut leaves nothing. */
    static void download(String size, String path, File to) throws IOException {
        File part = new File(to.getPath() + ".part");
        HttpURLConnection c = (HttpURLConnection) new URL(IMAGES + size + path).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
            if (c.getResponseCode() != 200) throw new IOException("image HTTP " + c.getResponseCode());
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw e;
        } finally {
            c.disconnect();
        }
        if (!part.renameTo(to)) throw new IOException("couldn't save the image");
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
