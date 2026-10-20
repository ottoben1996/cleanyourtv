package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Frame mode (docs/frame.md): one painting from the bundled public-domain gallery, full screen on a
 * dark wall, with a passe-partout and a small clock. Left/right change the painting, OK hides or
 * shows the clock, back returns home; any other key also leaves, like pressing a button on a Frame.
 *
 * <p>The 4K masters are decoded at about the panel's size, so a 1080p box does not keep a 45 MB
 * bitmap for a painting it draws at 900 px high.
 */
public class FrameActivity extends Activity {
    private static final String TAG = "TvHome.Frame";
    private static final String PREFS = "frame";
    private static final String KEY_INDEX = "art_index";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> assets = new ArrayList<>();
    private FrameView view;
    private Bitmap art;
    private int index;
    private int loadSerial;
    private boolean nightOn;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            if (view == null) return;
            view.setNight(nightOn && FrameArt.night(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)));
            view.tick();
            handler.postDelayed(this, msToNextMinute());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Config cfg = Config.load(this);
        nightOn = cfg.frameNight;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        hideSystemUi();

        view = new FrameView(this);
        view.setInfo(cfg.frameClock);
        view.setNight(nightOn && FrameArt.night(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)));
        setContentView(view);
        view.showHint(getString(R.string.frame_hint));

        try {
            assets.addAll(FrameArt.artworks(getAssets().list("art")));
        } catch (IOException e) {
            Log.w(TAG, "gallery unavailable: " + e.getMessage());
        }
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        index = FrameArt.step(prefs.getInt(KEY_INDEX, -1), assets.size(), 1);
        prefs.edit().putInt(KEY_INDEX, index).apply();
        if (assets.isEmpty()) {
            Log.w(TAG, "no artwork in assets/art");
            return;
        }
        view.post(() -> load(index));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view == null) return;
        hideSystemUi();
        handler.removeCallbacks(clockTick);
        clockTick.run();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(clockTick);
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Art mode is a resting screen, not a background task: anything that takes over (assistant,
        // another app, the screen turning off) brings the home back.
        if (!isChangingConfigurations()) finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (art != null) {
            art.recycle();
            art = null;
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                change(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                change(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                view.setInfo(!view.info());
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return super.dispatchKeyEvent(event);
            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;
            default:
                finish();
                return true;
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    private void change(int delta) {
        if (assets.isEmpty()) return;
        view.showHint(getString(R.string.frame_hint));
        if (assets.size() < 2) return;
        load(FrameArt.step(index, assets.size(), delta));
    }

    private void load(int next) {
        index = FrameArt.step(next, assets.size(), 0);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_INDEX, index).apply();
        final int serial = ++loadSerial;
        final String name = assets.get(index);
        final int target = getResources().getDisplayMetrics().heightPixels;
        new Thread(() -> {
            Bitmap bitmap = decode(name, target);
            handler.post(() -> {
                if (isFinishing() || isDestroyed() || serial != loadSerial) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                Bitmap previous = art;
                art = bitmap;
                view.setArt(bitmap);
                if (previous != null) previous.recycle();
            });
        }, "frame-art").start();
    }

    /** Bounds first, then a power-of-two downsample that keeps at least the panel's height. */
    private Bitmap decode(String name, int target) {
        String path = "art/" + name;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getAssets().open(path)) {
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (IOException e) {
            Log.w(TAG, "cannot read " + path + ": " + e.getMessage());
            return null;
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inScaled = false;
        try (InputStream in = getAssets().open(path)) {
            return BitmapFactory.decodeStream(in, null, options);
        } catch (IOException e) {
            Log.w(TAG, "cannot decode " + path + ": " + e.getMessage());
            return null;
        }
    }

    private static long msToNextMinute() {
        return 60_000L - System.currentTimeMillis() % 60_000L + 50L;
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
