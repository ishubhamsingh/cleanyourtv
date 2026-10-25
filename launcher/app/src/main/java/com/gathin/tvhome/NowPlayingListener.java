package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so Android lists other apps' media sessions to the launcher (NowPlaying): that list is for notification
 * listeners. It overrides nothing, so it reads no notifications. Granted once over ADB:
 * cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.NowPlayingListener
 */
public class NowPlayingListener extends NotificationListenerService {
}
