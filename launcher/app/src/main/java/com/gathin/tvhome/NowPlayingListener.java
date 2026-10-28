package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exists so Android lists other apps' media sessions to the launcher (NowPlaying): that list is for notification
 * listeners. It reads no notifications. Granted once over ADB:
 * cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.NowPlayingListener
 *
 * With the dashboard on (config dashboard) it also logs how long each app's media plays (ListeningLog), as Android
 * keeps the listener running behind other apps: music behind Home then counts in the Screen time card.
 */
public class NowPlayingListener extends NotificationListenerService {
    private static final long FLUSH_MS = 60_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MediaController> watched = new ArrayList<>();
    /** Apps playing now, and since when (epoch ms) their time was last written. */
    private final Map<String, Long> playingSince = new HashMap<>();
    private MediaSessionManager sessions;

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::watch;

    private final MediaController.Callback stateChanged = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            update();
        }

        @Override
        public void onSessionDestroyed() {
            update();
        }
    };

    private final Runnable flush = new Runnable() {
        @Override
        public void run() {
            write(System.currentTimeMillis(), true);
            if (!playingSince.isEmpty()) handler.postDelayed(this, FLUSH_MS);
        }
    };

    @Override
    public void onListenerConnected() {
        if (!Config.load(this).dashboard) return;
        sessions = getSystemService(MediaSessionManager.class);
        ComponentName self = new ComponentName(this, NowPlayingListener.class);
        try {
            sessions.addOnActiveSessionsChangedListener(changed, self, handler);
            watch(sessions.getActiveSessions(self));
        } catch (SecurityException e) {
            sessions = null;
        }
    }

    @Override
    public void onListenerDisconnected() {
        write(System.currentTimeMillis(), false);
        playingSince.clear();
        handler.removeCallbacks(flush);
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(changed);
        for (MediaController c : watched) c.unregisterCallback(stateChanged);
        watched.clear();
        sessions = null;
    }

    private void watch(List<MediaController> list) {
        for (MediaController c : watched) c.unregisterCallback(stateChanged);
        watched.clear();
        if (list != null) {
            for (MediaController c : list) {
                c.registerCallback(stateChanged, handler);
                watched.add(c);
            }
        }
        update();
    }

    /** Starts the clock for apps that began playing, and writes the time of those that stopped. */
    private void update() {
        long now = System.currentTimeMillis();
        Map<String, Boolean> playing = new HashMap<>();
        for (MediaController c : watched) {
            PlaybackState s = c.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) playing.put(c.getPackageName(), true);
        }
        SharedPreferences.Editor edit = getSharedPreferences(ListeningLog.PREFS, MODE_PRIVATE).edit();
        Map<String, Long> log = new HashMap<>();
        for (String pkg : new ArrayList<>(playingSince.keySet())) {
            if (playing.containsKey(pkg)) continue;
            ListeningLog.add(log, pkg, playingSince.remove(pkg), now, ZoneId.systemDefault());
        }
        merge(edit, log);
        for (String pkg : playing.keySet()) playingSince.putIfAbsent(pkg, now);
        handler.removeCallbacks(flush);
        if (!playingSince.isEmpty()) handler.postDelayed(flush, FLUSH_MS);
    }

    /** Writes the time played so far; {@code keepGoing} restarts the clocks at {@code now}. */
    private void write(long now, boolean keepGoing) {
        if (playingSince.isEmpty()) return;
        Map<String, Long> log = new HashMap<>();
        for (Map.Entry<String, Long> e : playingSince.entrySet()) {
            ListeningLog.add(log, e.getKey(), e.getValue(), now, ZoneId.systemDefault());
            if (keepGoing) e.setValue(now);
        }
        merge(getSharedPreferences(ListeningLog.PREFS, MODE_PRIVATE).edit(), log);
    }

    private void merge(SharedPreferences.Editor edit, Map<String, Long> added) {
        if (added.isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences(ListeningLog.PREFS, MODE_PRIVATE);
        Map<String, Object> all = new HashMap<>(prefs.getAll());
        for (Map.Entry<String, Long> e : added.entrySet()) {
            Object old = all.get(e.getKey());
            edit.putLong(e.getKey(), (old instanceof Long ? (Long) old : 0L) + e.getValue());
        }
        Map<String, Object> pruned = new HashMap<>(all);
        ListeningLog.prune(pruned, LocalDate.now());
        for (String k : all.keySet()) if (!pruned.containsKey(k)) edit.remove(k);
        edit.apply();
    }
}
