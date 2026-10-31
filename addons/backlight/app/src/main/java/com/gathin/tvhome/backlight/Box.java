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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The sync box, and what it should show. Three things want the light: the input watcher (Sync on the chosen input,
 * the look kept for everywhere else otherwise), the album colours while music plays away from that input, and the
 * controller screen. Commands go out on one worker thread over one connection kept open while they keep coming
 * (the box has few connection slots and leaks them when connections come and go quickly, so one is kept, with
 * a heartbeat, until a minute passes without a command); a burst (the brightness bar, a colour fade)
 * is merged so only the latest values go out.
 */
final class Box {
    static final String PREFS = "backlight";
    static final String AUTO = "auto", ALBUM = "album", OTHER_LOOK = "other_look", SYNC_LEVEL = "sync_level",
            STATUS = "status", INPUT = "input", IP = "ip";
    private static final long DEBOUNCE_MS = 1000, IDLE_CLOSE_MS = 60_000, HEARTBEAT_MS = 10_000;
    private static final long[] RETRY_MS = {10_000, 60_000};
    /** How often the album colours send the next colour: small steps look like the aurora's smooth drift. */
    private static final long STEP_MS = 150;
    private static Box instance;

    interface Listener {
        void changed(Looks.State state);
    }

    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Looks.State state = new Looks.State();
    private final List<Listener> listeners = new ArrayList<>();

    // Main thread.
    private Rule.Mode autoMode;
    private Rule.Mode sentMode;
    private int attempt;
    /** The album's aurora colours, and the ones they fade from (2 s, as on screen); null when nothing plays. */
    private int[] palette, paletteFrom;
    private long paletteSince;
    private boolean firstStep;

    // Worker thread.
    private Tuya.Session session;
    private String sessionHost;
    // Guarded by this.
    private Map<String, String> pending;
    private boolean pendingImportant;

    private final Runnable sendAuto = this::sendAutoNow;
    private final Runnable heartbeat = this::beat;
    private final Runnable albumTick = this::albumTick;
    private final Runnable closeIdle = () -> {
        main.removeCallbacks(heartbeat);
        worker.execute(this::closeSession);
    };

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

    Looks.State state() {
        return state;
    }

    void addListener(Listener l) {
        listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    // ---- What the light should show ----

    int syncLevel() {
        return prefs.getInt(SYNC_LEVEL, levelOf(BuildConfig.SYNC_VALUE));
    }

    private static int levelOf(String syncValue) {
        try {
            return Math.max(0, Math.min(2, Integer.parseInt(syncValue.substring(0, 2), 16) - Looks.SYNC_CODES[0]));
        } catch (RuntimeException e) {
            return 2;
        }
    }

    /** The look away from the sync input: the last scene, Calm or colour picked on the controller. */
    Map<String, String> otherLook() {
        Map<String, String> m = Looks.fromJson(prefs.getString(OTHER_LOOK, null));
        return m.isEmpty() ? Looks.defaultOther() : m;
    }

    boolean albumOn() {
        return prefs.getBoolean(ALBUM, false);
    }

    /** The controller's Album colours switch: the fade starts at once on what plays, or the kept look returns. */
    void setAlbum(boolean on) {
        prefs.edit().putBoolean(ALBUM, on).apply();
        main.post(() -> {
            if (on && albumPlaying() && autoMode != Rule.Mode.SYNC) {
                startAlbum();
            } else if (!on && albumRunning) {
                stopAlbum();
                if (autoMode != Rule.Mode.SYNC) send(otherLook(), true, Rule.Mode.OTHER);
            }
        });
    }

    /** From the input watcher, after each change of the app in front. */
    void want(Rule.Mode mode) {
        main.post(() -> {
            if (mode == autoMode) return;
            autoMode = mode;
            attempt = 0;
            main.removeCallbacks(sendAuto);
            main.postDelayed(sendAuto, DEBOUNCE_MS);
        });
    }

    private void sendAutoNow() {
        if (autoMode == null || !prefs.getBoolean(AUTO, true)) return;
        if (autoMode == Rule.Mode.SYNC) {
            stopAlbum();
            if (sentMode != Rule.Mode.SYNC) send(Looks.sync(syncLevel()), true, Rule.Mode.SYNC);
        } else if (albumPlaying()) {
            startAlbum();
        } else if (sentMode != Rule.Mode.OTHER) {
            send(otherLook(), true, Rule.Mode.OTHER);
        }
    }

    /** From the media listener: the playing cover's four aurora colours, or null when nothing plays. */
    void music(int[] colours) {
        main.post(() -> {
            long now = android.os.SystemClock.uptimeMillis();
            paletteFrom = palette != null ? currentColours(now) : colours;
            paletteSince = now;
            palette = colours;
            recheckAlbum();
        });
    }

    /** What plays and what is in front: album colours only for music (see {@link Rule#isMusic}). */
    private String playingPackage, frontPackage;
    private int playingType;

    /** From the media listener: the playing app and what it says it plays. */
    void playing(String pkg, int contentType) {
        main.post(() -> {
            playingPackage = pkg;
            playingType = contentType;
            recheckAlbum();
        });
    }

    /** From the input watcher: the full-screen app in front (a video app coming to the front stops the colours). */
    void front(String pkg) {
        main.post(() -> {
            if (pkg == null || pkg.equals(frontPackage)) return;
            frontPackage = pkg;
            recheckAlbum();
        });
    }

    private void recheckAlbum() {
        if (albumPlaying()) {
            if (autoMode != Rule.Mode.SYNC) startAlbum();
        } else if (albumRunning) {
            stopAlbum();
            if (autoMode != Rule.Mode.SYNC) send(otherLook(), true, Rule.Mode.OTHER);
        }
    }

    private boolean albumPlaying() {
        return albumOn() && palette != null && Rule.isMusic(playingType, playingPackage, frontPackage);
    }

    private int[] currentColours(long now) {
        if (paletteFrom == null) return palette;
        float t = Math.min(1f, (now - paletteSince) / (float) Aurora.FADE_MS);
        int[] c = new int[4];
        for (int i = 0; i < 4; i++) c[i] = Aurora.mix(paletteFrom[i], palette[i], t);
        return c;
    }

    private boolean albumRunning;

    /** Whether the album colours are on the strip now (for the controller's line under its title). */
    boolean albumRunning() {
        return albumRunning;
    }

    private void startAlbum() {
        if (albumRunning) return;
        albumRunning = true;
        firstStep = true;
        sentMode = null;
        main.removeCallbacks(albumTick);
        main.post(albumTick);
    }

    private void stopAlbum() {
        albumRunning = false;
        main.removeCallbacks(albumTick);
    }

    /** The album colours: the aurora's colour at the middle of the screen, a new one every {@link #STEP_MS}. */
    private void albumTick() {
        if (!albumRunning || palette == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        Aurora.Hue c = Aurora.now(paletteFrom == null ? palette : paletteFrom, palette, paletteSince, now);
        int v = Math.max(100, state.bright);
        send(firstStep ? Looks.colour(c.h, c.s, v) : Looks.colourOnly(c.h, c.s, v), firstStep, null);
        firstStep = false;
        main.postDelayed(albumTick, STEP_MS);
    }

    // ---- The controller ----

    /** A look picked on the controller: shown now and, unless it is Sync, kept for everywhere but the sync input. */
    void pick(Map<String, String> look, boolean isSync) {
        main.post(() -> {
            stopAlbum();
            if (!isSync) prefs.edit().putString(OTHER_LOOK, Looks.toJson(look)).apply();
            sentMode = isSync ? Rule.Mode.SYNC : Rule.Mode.OTHER;
            send(look, true, null);
        });
    }

    void setSyncLevel(int level) {
        prefs.edit().putInt(SYNC_LEVEL, level).apply();
        pick(Looks.sync(level), true);
    }

    void brightness(int value) {
        int v = Looks.clamp(value, Looks.BRIGHT_MIN, Looks.BRIGHT_MAX);
        Map<String, String> m = new LinkedHashMap<>();
        m.put(Looks.BRIGHT, String.valueOf(v));
        // On a colour, its value is the brightness the strip shows.
        if ("colour".equals(state.mode) && state.hsv != null) m.put(Looks.COLOUR, Looks.hsv(state.hsv[0], state.hsv[1], v));
        send(m, false, null);
    }

    void power(boolean on) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(Looks.POWER, String.valueOf(on));
        send(m, true, null);
    }

    /** Reads the box's state now (when the controller opens). */
    void refresh() {
        if (!configured()) return;
        worker.execute(() -> {
            try {
                String json = open().status();
                main.post(() -> {
                    state.update(json);
                    notifyListeners();
                });
                idleLater();
            } catch (IOException e) {
                closeSession();
                status(app.getString(R.string.status_unreachable, String.valueOf(e.getMessage()), time()));
            }
        });
    }

    // ---- Sending ----

    /**
     * Queues values for the box; values already waiting are merged (the later ones win). {@code important}: a look
     * change, retried and looked for on the network when the box doesn't answer; otherwise a step in a burst,
     * dropped on failure. {@code mode}: what the input watcher's command was, to skip sending it twice.
     */
    private void send(Map<String, String> dps, boolean important, Rule.Mode mode) {
        if (!configured()) {
            status(app.getString(R.string.status_no_key));
            return;
        }
        state.apply(dps);
        notifyListeners();
        synchronized (this) {
            if (pending == null) pending = new LinkedHashMap<>();
            // A look (scene, mode, values, in that order) replaces the order of what waits; values join it.
            if (dps.containsKey(Looks.MODE)) {
                Map<String, String> merged = new LinkedHashMap<>(dps);
                for (Map.Entry<String, String> e : pending.entrySet()) merged.putIfAbsent(e.getKey(), e.getValue());
                merged.putAll(dps);
                pending = merged;
            } else {
                pending.putAll(dps);
            }
            pendingImportant |= important;
        }
        final Rule.Mode m = mode;
        worker.execute(() -> drain(m));
    }

    private void drain(Rule.Mode mode) {
        Map<String, String> dps;
        boolean important;
        synchronized (this) {
            dps = pending;
            important = pendingImportant;
            pending = null;
            pendingImportant = false;
        }
        if (dps == null || dps.isEmpty()) return;
        try {
            try {
                open().set(dps);
            } catch (IOException first) {
                // A kept connection the box has dropped, or a box slow to take a new one: once more, fresh.
                closeSession();
                if (first.getMessage() != null && first.getMessage().contains("key")) throw first;
                try {
                    open().set(dps);
                } catch (IOException second) {
                    closeSession();
                    if (!important || !(second instanceof java.net.ConnectException || second instanceof java.net.NoRouteToHostException
                            || second instanceof java.net.SocketTimeoutException)) throw second;
                    // Not answering at its address: it may have a new one.
                    String found = discover(12_000);
                    if (found == null) throw new IOException(app.getString(R.string.error_not_found));
                    prefs.edit().putString(IP, found).apply();
                    open().set(dps);
                }
            }
            idleLater();
            if (important) {
                status(app.getString(R.string.status_sent, describe(dps), time()));
                main.post(() -> {
                    if (mode != null) sentMode = mode;
                    attempt = 0;
                });
            }
        } catch (IOException e) {
            closeSession();
            if (!important) return;
            status(app.getString(R.string.status_failed, describe(dps), String.valueOf(e.getMessage()), time()));
            main.post(() -> {
                if (mode != null && mode == autoMode && attempt < RETRY_MS.length) main.postDelayed(sendAuto, RETRY_MS[attempt++]);
            });
        }
    }

    private Tuya.Session open() throws IOException {
        String ip = prefs.getString(IP, BuildConfig.IP);
        if (ip == null || ip.isEmpty()) {
            ip = discover(12_000);
            if (ip == null) throw new IOException(app.getString(R.string.error_not_found));
            prefs.edit().putString(IP, ip).apply();
        }
        if (session != null && ip.equals(sessionHost)) return session;
        closeSession();
        session = new Tuya.Session(ip, BuildConfig.LOCAL_KEY, 4000);
        sessionHost = ip;
        return session;
    }

    private void closeSession() {
        if (session != null) {
            try {
                session.close();
            } catch (IOException ignored) {
                // already gone
            }
        }
        session = null;
    }

    /** After a command: the connection is kept, with a heartbeat every 10 s, until a minute passes without one. */
    private void idleLater() {
        main.removeCallbacks(closeIdle);
        main.postDelayed(closeIdle, IDLE_CLOSE_MS);
        main.removeCallbacks(heartbeat);
        main.postDelayed(heartbeat, HEARTBEAT_MS);
    }

    private void beat() {
        worker.execute(() -> {
            if (session == null) return;
            try {
                session.heartbeat(BuildConfig.DEVICE_ID);
                main.postDelayed(heartbeat, HEARTBEAT_MS);
            } catch (IOException e) {
                closeSession();
            }
        });
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
        } catch (IOException e) {
            return null;
        } finally {
            if (lock != null) lock.release();
        }
        return null;
    }

    // ---- Status ----

    private String describe(Map<String, String> dps) {
        Looks.State s = new Looks.State();
        s.mode = "";
        s.apply(dps);
        if (dps.containsKey(Looks.POWER)) return app.getString(s.on ? R.string.power_on : R.string.power_off);
        return Ui.lookName(app, s);
    }

    void status(String text) {
        prefs.edit().putString(STATUS, text).apply();
    }

    private void notifyListeners() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::notifyListeners);
            return;
        }
        for (Listener l : new ArrayList<>(listeners)) l.changed(state);
    }

    private static String time() {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
    }
}
