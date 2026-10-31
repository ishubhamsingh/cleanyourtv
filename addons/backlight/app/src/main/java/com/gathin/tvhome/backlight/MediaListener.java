package com.gathin.tvhome.backlight;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;

import java.util.ArrayList;
import java.util.List;

/**
 * Album colours: the cover of whatever plays (Spotify, YouTube Music, ...) handed to the box as the aurora's colours. A
 * notification listener only because Android lets only one see other apps' media sessions; no notification is read.
 */
public class MediaListener extends NotificationListenerService {
    private MediaSessionManager sessions;
    private final List<MediaController> watched = new ArrayList<>();
    private Bitmap lastArt;

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::watch;
    private final MediaController.Callback callback = new MediaController.Callback() {
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

    @Override
    public void onListenerConnected() {
        sessions = getSystemService(MediaSessionManager.class);
        ComponentName me = new ComponentName(this, MediaListener.class);
        try {
            sessions.addOnActiveSessionsChangedListener(changed, me);
            watch(sessions.getActiveSessions(me));
        } catch (SecurityException e) {
            // The listener grant was taken back.
        }
    }

    @Override
    public void onListenerDisconnected() {
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(changed);
        watch(null);
    }

    private void watch(List<MediaController> controllers) {
        for (MediaController c : watched) c.unregisterCallback(callback);
        watched.clear();
        if (controllers != null) {
            for (MediaController c : controllers) {
                c.registerCallback(callback);
                watched.add(c);
            }
        }
        update();
    }

    /** The playing session's cover as colours, or null when nothing plays. */
    private void update() {
        Bitmap art = null;
        boolean playing = false;
        for (MediaController c : watched) {
            PlaybackState s = c.getPlaybackState();
            if (s == null || s.getState() != PlaybackState.STATE_PLAYING) continue;
            playing = true;
            MediaMetadata m = c.getMetadata();
            if (m != null) {
                art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            }
            break;
        }
        Box box = Box.get(this);
        if (!playing) {
            lastArt = null;
            box.music(null);
            return;
        }
        if (art != null && art == lastArt) return;               // same cover: the drift goes on
        lastArt = art;
        int[] colours;
        if (art == null) {
            colours = Aurora.DEFAULT.clone();
        } else {
            // As the aurora does: an 8 x 8 copy, one colour per quarter.
            Bitmap small = Bitmap.createScaledBitmap(art, 8, 8, true);
            int[] px = new int[64];
            small.getPixels(px, 0, 8, 0, 0, 8, 8);
            colours = Aurora.colours(px, 8, 8);
        }
        box.music(colours);
    }
}
