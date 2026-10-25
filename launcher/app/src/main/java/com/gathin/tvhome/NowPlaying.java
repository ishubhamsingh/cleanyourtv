package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Now playing (config now_playing): the media session playing in the background (Spotify, YouTube Music, ...) for the
 * top bar's card, with its controls. Android lists other apps' media sessions only to a notification listener, so the
 * launcher declares NowPlayingListener, granted once over ADB; it reads the sessions, never the notifications. Watches
 * only between start() and stop() (while Home is on screen).
 */
final class NowPlaying {

    /** What the card shows; null when nothing is playing or paused. */
    static final class Track {
        final String pkg, title, artist;
        final Bitmap art;
        final boolean playing;

        Track(String pkg, String title, String artist, Bitmap art, boolean playing) {
            this.pkg = pkg;
            this.title = title;
            this.artist = artist;
            this.art = art;
            this.playing = playing;
        }
    }

    interface Listener {
        void onTrack(Track track);
    }

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ComponentName self;
    private MediaSessionManager sessions;
    private final List<MediaController> watched = new ArrayList<>();
    private MediaController current;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = list -> watch(list);

    private final MediaController.Callback changed = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            update();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            update();
        }

        @Override
        public void onSessionDestroyed() {
            update();
        }
    };

    NowPlaying(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
        this.self = new ComponentName(ctx, NowPlayingListener.class);
    }

    /** The launcher may read media sessions (the notification-listener grant). */
    boolean allowed() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            android.app.NotificationManager nm = ctx.getSystemService(android.app.NotificationManager.class);
            return nm != null && nm.isNotificationListenerAccessGranted(self);
        }
        // Android 8.0: the setting, which can lag behind a grant until the listener binds.
        String list = android.provider.Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        return list != null && list.contains(self.flattenToString());
    }

    void start() {
        if (!allowed()) {
            listener.onTrack(null);
            return;
        }
        sessions = ctx.getSystemService(MediaSessionManager.class);
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, self, main);
            watch(sessions.getActiveSessions(self));
        } catch (SecurityException e) {
            sessions = null;
            listener.onTrack(null);
        }
    }

    void stop() {
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        for (MediaController c : watched) c.unregisterCallback(changed);
        watched.clear();
        sessions = null;
        current = null;
    }

    private void watch(List<MediaController> list) {
        for (MediaController c : watched) c.unregisterCallback(changed);
        watched.clear();
        if (list != null) {
            for (MediaController c : list) {
                c.registerCallback(changed, main);
                watched.add(c);
            }
        }
        update();
    }

    private void update() {
        int[] states = new int[watched.size()];
        long[] updated = new long[watched.size()];
        for (int i = 0; i < watched.size(); i++) {
            PlaybackState s = watched.get(i).getPlaybackState();
            states[i] = s == null ? PlaybackState.STATE_NONE : s.getState();
            updated[i] = s == null ? 0 : s.getLastPositionUpdateTime();
        }
        int pick = choose(states, updated);
        current = pick < 0 ? null : watched.get(pick);
        listener.onTrack(current == null ? null : track(current));
    }

    /**
     * The session to show: a playing one (buffering counts) before a paused one, the most recently updated first;
     * -1 when none plays or is paused.
     */
    static int choose(int[] states, long[] updated) {
        int best = -1;
        for (int i = 0; i < states.length; i++) {
            int rank = rank(states[i]);
            if (rank == 0) continue;
            if (best < 0 || rank > rank(states[best]) || rank == rank(states[best]) && updated[i] > updated[best]) best = i;
        }
        return best;
    }

    private static int rank(int state) {
        switch (state) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
                return 2;
            case PlaybackState.STATE_PAUSED:
                return 1;
            default:
                return 0;
        }
    }

    private Track track(MediaController c) {
        MediaMetadata m = c.getMetadata();
        String title = text(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE);
        String artist = text(m, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (title.isEmpty()) title = appLabel(c.getPackageName());
        Bitmap art = null;
        if (m != null) {
            art = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        PlaybackState s = c.getPlaybackState();
        return new Track(c.getPackageName(), title, artist, art, s != null && rank(s.getState()) == 2);
    }

    private static String text(MediaMetadata m, String... keys) {
        if (m == null) return "";
        for (String k : keys) {
            CharSequence v = m.getText(k);
            if (v != null && v.length() > 0) return v.toString();
        }
        return "";
    }

    private String appLabel(String pkg) {
        PackageManager pm = ctx.getPackageManager();
        try {
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    // MARK: - Controls

    void playPause() {
        if (current == null) return;
        PlaybackState s = current.getPlaybackState();
        if (s != null && rank(s.getState()) == 2) current.getTransportControls().pause();
        else current.getTransportControls().play();
    }

    void next() {
        if (current != null) current.getTransportControls().skipToNext();
    }

    void previous() {
        if (current != null) current.getTransportControls().skipToPrevious();
    }

    /** Opens the playing app: the session's own screen when it gives one, else the app. */
    void open(Context from) {
        if (current == null) return;
        PendingIntent session = current.getSessionActivity();
        if (session != null) {
            try {
                session.send();
                return;
            } catch (PendingIntent.CanceledException ignored) {
            }
        }
        PackageManager pm = ctx.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(current.getPackageName());
        if (launch == null) launch = pm.getLaunchIntentForPackage(current.getPackageName());
        if (launch != null) from.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
