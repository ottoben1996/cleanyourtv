package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.io.InputStream;
import java.util.List;

/**
 * What is playing right now, read from the media sessions the apps publish (Spotify, TizenTube, and
 * any other app that exposes one). The launcher shows the title, the artist and the artwork, and
 * takes the glow's colour from the artwork so the room follows the music.
 *
 * Android only lets an app list the sessions of other apps when it has an enabled notification
 * listener: {@link PlaybackListener} is that listener, and the owner grants the access once
 * (docs/now-playing.md). No notification is ever read.
 */
final class NowPlaying {

    interface Listener {
        void onChanged();
    }

    private static final String TAG = "TvHome.NowPlaying";
    /** The card shows 96 dp, so 256 px is already more than the screen can use. */
    private static final int ART_PX = 256;

    private final Context ctx;
    private final Listener onChanged;
    private final MediaSessionManager manager;
    private final ComponentName listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::bindBest;

    private MediaController controller;
    private String title = "";
    private String artist = "";
    private String appLabel = "";
    private String pkg = "";
    private Bitmap art;
    private int color;
    private String artKey = "";
    private int state = PlaybackState.STATE_NONE;
    private boolean running;

    NowPlaying(Context ctx, Listener onChanged) {
        this.ctx = ctx.getApplicationContext();
        this.onChanged = onChanged;
        this.manager = (MediaSessionManager) this.ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        this.listener = new ComponentName(this.ctx, PlaybackListener.class);
    }

    /** Whether the listener is enabled, i.e. whether the launcher may read the sessions at all. */
    static boolean allowed(Context ctx) {
        String flat = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        if (flat == null || flat.isEmpty()) return false;
        String self = ctx.getPackageName();
        for (String part : flat.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(part);
            if (cn != null && self.equals(cn.getPackageName())) return true;
        }
        return false;
    }

    /** Opens the notification access screen; false when this TV has none. */
    static boolean openSettings(Context ctx) {
        try {
            ctx.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            Log.w(TAG, "no notification access screen: " + e);
            return false;
        }
    }

    void start() {
        if (running || manager == null || !allowed(ctx)) return;
        running = true;
        try {
            manager.addOnActiveSessionsChangedListener(sessions, listener, main);
            bindBest(manager.getActiveSessions(listener));
        } catch (SecurityException e) {
            // Access revoked between the check and the call: stay off, quietly.
            running = false;
            Log.w(TAG, "notification access refused: " + e.getMessage());
        }
    }

    void stop() {
        if (!running) return;
        running = false;
        try {
            manager.removeOnActiveSessionsChangedListener(sessions);
        } catch (Exception ignored) {
        }
        bind(null);
    }

    /** The session to show: playing first, then buffering, then paused; sessions without a title lose. */
    private static MediaController best(List<MediaController> list) {
        if (list == null) return null;
        MediaController best = null;
        int bestScore = 0;
        for (MediaController c : list) {
            if (c == null || c.getMetadata() == null || c.getPlaybackState() == null) continue;
            if ("com.android.bluetooth".equals(c.getPackageName())) continue;
            String name = first(c.getMetadata(), MediaMetadata.METADATA_KEY_TITLE,
                    MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            if (name.isEmpty()) continue;
            int score = rank(c.getPlaybackState().getState());
            if (score > bestScore) {
                best = c;
                bestScore = score;
            }
        }
        return best;
    }

    private static int rank(int state) {
        switch (state) {
            case PlaybackState.STATE_PLAYING:
                return 4;
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
                return 3;
            case PlaybackState.STATE_PAUSED:
                return 2;
            default:
                return 0;
        }
    }

    private void bindBest(List<MediaController> controllers) {
        bind(best(controllers));
    }

    private void bind(MediaController c) {
        if (c != null && controller != null
                && c.getSessionToken().equals(controller.getSessionToken())) {
            sync();
            return;
        }
        if (controller != null) {
            try {
                controller.unregisterCallback(callback);
            } catch (Exception ignored) {
            }
        }
        controller = c;
        if (c != null) c.registerCallback(callback, main);
        sync();
    }

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            sync();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState playbackState) {
            sync();
        }

        @Override
        public void onSessionDestroyed() {
            bind(null);
        }
    };

    private void sync() {
        if (controller == null) {
            title = "";
            artist = "";
            pkg = "";
            appLabel = "";
            art = null;
            artKey = "";
            color = 0;
            state = PlaybackState.STATE_NONE;
            notifyChanged();
            return;
        }
        MediaMetadata m = controller.getMetadata();
        PlaybackState s = controller.getPlaybackState();
        title = m == null ? "" : first(m, MediaMetadata.METADATA_KEY_TITLE,
                MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        artist = m == null ? "" : first(m, MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST, MediaMetadata.METADATA_KEY_ALBUM);
        state = s == null ? PlaybackState.STATE_NONE : s.getState();
        String sessionPkg = controller.getPackageName();
        if (!sessionPkg.equals(pkg)) {
            pkg = sessionPkg;
            appLabel = appName(sessionPkg);
        }
        // Decoding artwork is the only expensive step here, so only when the track's art changes.
        String key = m == null ? "" : artKey(m);
        if (!key.equals(artKey)) {
            artKey = key;
            art = m == null ? null : artwork(m);
            color = art == null ? 0 : dominantColor(art);
        }
        notifyChanged();
    }

    private void notifyChanged() {
        if (running) onChanged.onChanged();
    }

    private String appName(String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    /** A stable key for "the artwork of this track": its URI, or the title when there is none. */
    private static String artKey(MediaMetadata m) {
        String uri = first(m, MediaMetadata.METADATA_KEY_ART_URI,
                MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if (!uri.isEmpty()) return uri;
        return first(m, MediaMetadata.METADATA_KEY_TITLE) + "|"
                + first(m, MediaMetadata.METADATA_KEY_ALBUM);
    }

    private Bitmap artwork(MediaMetadata m) {
        Bitmap bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bmp == null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bmp != null) return scaled(bmp);
        String raw = first(m, MediaMetadata.METADATA_KEY_ART_URI,
                MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        if (raw.isEmpty()) return null;
        Uri uri = Uri.parse(raw);
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            return in == null ? null : scaled(BitmapFactory.decodeStream(in));
        } catch (Exception e) {
            // A provider that refuses the read (or an http URI the resolver cannot open) just means
            // no artwork: the card keeps its note placeholder and the glow its app colour.
            Log.i(TAG, "artwork unavailable (" + uri.getScheme() + "): " + e.getMessage());
            return null;
        }
    }

    private static Bitmap scaled(Bitmap bmp) {
        if (bmp == null) return null;
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int max = Math.max(w, h);
        if (max <= ART_PX || w <= 0 || h <= 0) return bmp;
        float scale = (float) ART_PX / max;
        return Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(w * scale)),
                Math.max(1, Math.round(h * scale)), true);
    }

    /**
     * One colour from the cover: 24×24 pixels, dark padding ignored, saturated pixels weighted more
     * (a cover that is mostly white still gives its accent), then saturation and brightness lifted
     * so the glow reads on a dark TV. 0 when nothing usable came out.
     */
    static int dominantColor(Bitmap bmp) {
        if (bmp == null || bmp.getWidth() <= 0 || bmp.getHeight() <= 0) return 0;
        Bitmap small = Bitmap.createScaledBitmap(bmp, 24, 24, true);
        long r = 0, g = 0, b = 0;
        float weight = 0;
        float[] hsv = new float[3];
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                int p = small.getPixel(x, y);
                Color.colorToHSV(p, hsv);
                if (hsv[2] < 0.08f) continue;                    // black border or letterboxing
                float w = 0.15f + hsv[1] * hsv[1];                // greys barely count
                r += (long) (Color.red(p) * w);
                g += (long) (Color.green(p) * w);
                b += (long) (Color.blue(p) * w);
                weight += w;
            }
        }
        if (weight <= 0) return 0;
        int c = Color.rgb((int) (r / weight), (int) (g / weight), (int) (b / weight));
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.max(hsv[1], 0.35f);
        hsv[2] = Math.min(0.95f, Math.max(hsv[2], 0.45f));
        return Color.HSVToColor(hsv);
    }

    private static String first(MediaMetadata m, String... keys) {
        for (String key : keys) {
            String value = m.getString(key);
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    /** Whether there is something to show: a titled session that is playing, buffering or paused. */
    boolean isShowing() {
        return controller != null && !title.isEmpty() && rank(state) >= 2;
    }

    String title() {
        return title;
    }

    String artist() {
        return artist;
    }

    String appLabel() {
        return appLabel;
    }

    String packageName() {
        return pkg;
    }

    /** The cover's colour, or 0 when the app published no usable artwork. */
    int color() {
        return color;
    }

    Bitmap art() {
        return art;
    }

    /** 0-100 from the session's clock, or -1 when the session reports no duration (live, video ad). */
    int progress() {
        MediaController c = controller;
        if (c == null) return -1;
        PlaybackState s = c.getPlaybackState();
        MediaMetadata m = c.getMetadata();
        if (s == null || m == null) return -1;
        long duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (duration <= 0) return -1;
        long position = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            position += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime())
                    * s.getPlaybackSpeed());
        }
        return (int) Math.max(0, Math.min(100, position * 100 / duration));
    }
}
