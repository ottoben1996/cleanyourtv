package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextPaint;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The full-screen art wall: the painting edge to edge, and a discreet clock over a soft scrim at the
 * bottom. One view draws everything, so changing the painting costs one bitmap and one invalidate;
 * nothing animates except the note that explains the remote.
 */
final class FrameView extends View {
    private static final int INK = 0xFFF2ECE2;
    private static final int INK_SOFT = 0xFFE0D8CB;
    private static final int INK_HINT = 0xFFD6CEC0;
    /** The room is dimmed this much between 22:00 and 08:00. */
    private static final float NIGHT_DIM = 0.42f;

    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint scrimPaint = new Paint();
    private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint datePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    private final SimpleDateFormat timeFormat;
    private final SimpleDateFormat dateFormat;
    private LinearGradient scrim;

    private Bitmap art;
    private boolean info = true;
    private boolean night;
    private String hint = "";
    private float hintAlpha;

    private final Runnable hideHint = new Runnable() {
        @Override
        public void run() {
            final float from = hintAlpha;
            ValueAnimator fade = ValueAnimator.ofFloat(from, 0f);
            fade.setDuration(700);
            fade.addUpdateListener(a -> {
                hintAlpha = (Float) a.getAnimatedValue();
                invalidate();
            });
            fade.start();
        }
    };

    FrameView(Context context) {
        super(context);
        Locale locale = Locale.getDefault();
        timeFormat = new SimpleDateFormat("HH:mm", locale);
        dateFormat = new SimpleDateFormat(
                android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale);

        float density = getResources().getDisplayMetrics().density;
        float scaled = getResources().getDisplayMetrics().scaledDensity;
        Typeface outfit = getResources().getFont(R.font.outfit);
        Typeface light = Build.VERSION.SDK_INT >= 28
                ? Typeface.create(outfit, 300, false) : Typeface.create(outfit, Typeface.NORMAL);
        Typeface regular = Typeface.create(outfit, Typeface.NORMAL);

        dimPaint.setColor(Color.BLACK);
        dimPaint.setAlpha(Math.round(NIGHT_DIM * 255));

        timePaint.setColor(INK);
        timePaint.setTextSize(30f * scaled);
        timePaint.setTypeface(light);
        timePaint.setShadowLayer(10f * density, 0f, 2f * density, 0xB0000000);

        datePaint.setColor(INK_SOFT);
        datePaint.setTextSize(13f * scaled);
        datePaint.setTypeface(regular);
        datePaint.setShadowLayer(7f * density, 0f, density, 0xA0000000);

        hintPaint.setColor(INK_HINT);
        hintPaint.setTextSize(11f * scaled);
        hintPaint.setTypeface(regular);
        hintPaint.setShadowLayer(6f * density, 0f, density, 0x99000000);

        setBackgroundColor(Color.BLACK);
    }

    void setArt(Bitmap bitmap) {
        art = bitmap;
        crop();
        invalidate();
    }

    /** The painting fills the panel, so keep the centre and cut whatever the screen's ratio cannot hold. */
    private void crop() {
        if (art == null || art.getWidth() <= 0 || art.getHeight() <= 0) {
            srcRect.setEmpty();
            return;
        }
        int width = art.getWidth();
        int height = art.getHeight();
        float ratio = getWidth() > 0 && getHeight() > 0
                ? (float) getWidth() / getHeight() : 16f / 9f;
        if ((float) width / height > ratio) {
            int cropWidth = Math.round(height * ratio);
            int left = (width - cropWidth) / 2;
            srcRect.set(left, 0, left + cropWidth, height);
        } else {
            int cropHeight = Math.round(width / ratio);
            int top = (height - cropHeight) / 2;
            srcRect.set(0, top, width, top + cropHeight);
        }
    }

    void setInfo(boolean visible) {
        info = visible;
        invalidate();
    }

    void setNight(boolean isNight) {
        if (night == isNight) return;
        night = isNight;
        invalidate();
    }

    boolean info() {
        return info;
    }

    /** A short line at the bottom right, gone after a few seconds; a key press brings it back. */
    void showHint(String text) {
        hint = text == null ? "" : text;
        hintAlpha = 1f;
        removeCallbacks(hideHint);
        if (!hint.isEmpty()) postDelayed(hideHint, 6000);
        invalidate();
    }

    void tick() {
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        dstRect.set(0, 0, w, h);
        scrim = new LinearGradient(0, h - dp(210), 0, h,
                new int[]{0x00000000, 0x66000000, 0xB8000000},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP);
        crop();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(hideHint);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (art != null) {
            canvas.drawBitmap(art, srcRect, dstRect, artPaint);
        }
        if (night) canvas.drawRect(0, 0, w, h, dimPaint);
        if (scrim != null) {
            scrimPaint.setShader(scrim);
            canvas.drawRect(0, h - dp(210), w, h, scrimPaint);
        }

        drawClock(canvas);
        drawHint(canvas);
    }

    private void drawClock(Canvas canvas) {
        if (!info) return;
        int h = getHeight();
        Date now = new Date();
        String time = timeFormat.format(now);
        String date = dateFormat.format(now);
        if (!date.isEmpty()) {
            date = Character.toUpperCase(date.charAt(0)) + date.substring(1);
        }
        float x = dp(34);
        canvas.drawText(time, x, h - dp(58), timePaint);
        canvas.drawText(date, x, h - dp(34), datePaint);
    }

    private void drawHint(Canvas canvas) {
        if (hint.isEmpty() || hintAlpha <= 0.01f) return;
        hintPaint.setAlpha(Math.round(210 * hintAlpha));
        float x = getWidth() - dp(34);
        canvas.drawText(hint, x - hintPaint.measureText(hint), getHeight() - dp(34), hintPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
