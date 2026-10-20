package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * The launcher's notification listener. It reads no notification: on Android 5+ an enabled listener
 * is what lets an app ask {@link android.media.session.MediaSessionManager} for the active media
 * sessions, and this empty service is that listener (the owner grants it once in Settings; see
 * docs/now-playing.md). Nothing is parsed, stored or sent anywhere.
 */
public final class PlaybackListener extends NotificationListenerService {
}
