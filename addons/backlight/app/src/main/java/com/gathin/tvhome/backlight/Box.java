package com.gathin.tvhome.backlight;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The sync box: the wanted mode is sent a second after the last change (a quick hop between inputs sends nothing),
 * once per change. When the box doesn't answer, its new address is looked up from its announcements and the
 * command tried again, then again after 10 and 60 seconds.
 */
final class Box {
    static final String PREFS = "backlight";
    static final String AUTO = "auto", STATUS = "status", MODE = "mode", INPUT = "input", IP = "ip";
    private static final long DEBOUNCE_MS = 1000;
    private static final long[] RETRY_MS = {10_000, 60_000};
    private static Box instance;

    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Rule.Mode wanted, sent;
    private int attempt;
    private final Runnable send = this::sendNow;

    static synchronized Box get(Context c) {
        if (instance == null) instance = new Box(c.getApplicationContext());
        return instance;
    }

    private Box(Context app) {
        this.app = app;
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    SharedPreferences prefs() {
        return prefs;
    }

    boolean configured() {
        return !BuildConfig.DEVICE_ID.isEmpty() && BuildConfig.LOCAL_KEY.length() == 16;
    }

    /** From the input watcher: sent after the debounce, only if it differs from what the box was last told. */
    void want(Rule.Mode mode) {
        main.post(() -> {
            if (mode == wanted) return;
            wanted = mode;
            attempt = 0;
            main.removeCallbacks(send);
            main.postDelayed(send, DEBOUNCE_MS);
        });
    }

    /** From the buttons on the add-on's screen: now, even if the box was already told. */
    void force(Rule.Mode mode) {
        main.post(() -> {
            wanted = mode;
            sent = null;
            attempt = 0;
            main.removeCallbacks(send);
            sendNow();
        });
    }

    private void sendNow() {
        final Rule.Mode mode = wanted;
        if (mode == null || mode == sent) return;
        if (!configured()) {
            status(app.getString(R.string.status_no_key));
            return;
        }
        worker.execute(() -> {
            String value = mode == Rule.Mode.SYNC ? BuildConfig.SYNC_VALUE : BuildConfig.OTHER_VALUE;
            String name = app.getString(mode == Rule.Mode.SYNC ? R.string.mode_sync : R.string.mode_other);
            try {
                setAnywhere(value);
                main.post(() -> {
                    sent = mode;
                    prefs.edit().putString(MODE, name).apply();
                });
                status(app.getString(R.string.status_sent, name, time()));
            } catch (IOException e) {
                status(app.getString(R.string.status_failed, name, String.valueOf(e.getMessage()), time()));
                main.post(() -> {
                    if (mode == wanted && attempt < RETRY_MS.length) main.postDelayed(send, RETRY_MS[attempt++]);
                });
            }
        });
    }

    /** At the known address; when that fails, at the address the box announces now. */
    private void setAnywhere(String value) throws IOException {
        String ip = prefs.getString(IP, BuildConfig.IP);
        if (ip != null && !ip.isEmpty()) {
            try {
                Tuya.set(ip, BuildConfig.LOCAL_KEY, BuildConfig.DP, value, 4000);
                return;
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("key")) throw e;   // found it, wrong key
            }
        }
        String found = discover(12_000);
        if (found == null) throw new IOException(app.getString(R.string.error_not_found));
        prefs.edit().putString(IP, found).apply();
        Tuya.set(found, BuildConfig.LOCAL_KEY, BuildConfig.DP, value, 4000);
    }

    /** Listens for the box's own announcement (every few seconds on UDP 6667); its address, or null. */
    private String discover(int timeoutMs) {
        WifiManager wifi = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = wifi == null ? null : wifi.createMulticastLock("backlight");
        if (lock != null) lock.acquire();
        try (DatagramSocket s = new DatagramSocket(null)) {
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(Tuya.BROADCAST_PORT));
            s.setSoTimeout(timeoutMs);
            long end = System.currentTimeMillis() + timeoutMs;
            byte[] buf = new byte[2048];
            while (System.currentTimeMillis() < end) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                s.receive(p);
                String json = Tuya.announcement(Arrays.copyOf(p.getData(), p.getLength()));
                if (json == null) continue;
                try {
                    JSONObject a = new JSONObject(json);
                    if (BuildConfig.DEVICE_ID.equals(a.optString("gwId"))) return a.optString("ip", p.getAddress().getHostAddress());
                } catch (JSONException ignored) {
                    // another device's packet
                }
            }
        } catch (SocketTimeoutException e) {
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            if (lock != null) lock.release();
        }
        return null;
    }

    void status(String text) {
        prefs.edit().putString(STATUS, text).apply();
    }

    private static String time() {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
    }
}
