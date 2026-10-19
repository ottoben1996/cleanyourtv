package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The optional weather line in the top-left corner (config.xml, weather_*). One small HTTPS GET
 * to open-meteo.com (no API key, no account, no location permission: the latitude and longitude
 * come from the theme) while Home is in front, throttled to roughly one request every half hour.
 * The reading is kept in SharedPreferences, so the line survives a reboot and shows a value
 * immediately while the next request is in flight.
 *
 * This is the only part of the launcher that touches the network; it does nothing unless a theme
 * sets weather_enabled with both coordinates. The reference launcher stays offline.
 */
final class Weather {

    static final String CELSIUS = "celsius";
    static final String FAHRENHEIT = "fahrenheit";

    private static final String PREFS = "weather";
    private static final String KEY_UNITS = "units";
    private static final String KEY_TEMP = "temp";
    private static final String KEY_CODE = "code";
    private static final String KEY_DAY = "day";
    private static final String KEY_AT = "at";
    private static final int READ_LIMIT = 64 * 1024;
    /** Never more than one request a minute, however often the caller asks. */
    private static final long MIN_GAP_MS = 60_000L;
    /** After a failure, wait this long before trying again instead of retrying every minute. */
    private static final long FAILED_GAP_MS = 10 * 60_000L;
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "weather");
        t.setDaemon(true);
        return t;
    });
    /**
     * Created on first use, not as a field: the unit tests load this class on the JVM, where
     * android.os.Handler exists only as a stub.
     */
    private static Handler main;
    /** Process-wide gate shared by the minute tick and the network callback. */
    private static long nextAllowed;

    private Weather() {
    }

    /** One reading: rounded temperature in the configured unit, WMO weather code, day or night. */
    static final class Now {
        final int temperature;
        final int code;
        final boolean day;
        final long fetchedAt;

        Now(int temperature, int code, boolean day, long fetchedAt) {
            this.temperature = temperature;
            this.code = code;
            this.day = day;
            this.fetchedAt = fetchedAt;
        }
    }

    interface Listener {
        void onWeather(Now now);
    }

    /** {@code weather_units} normalised to what the API expects. */
    static String units(String raw) {
        return raw != null && FAHRENHEIT.equalsIgnoreCase(raw.trim()) ? FAHRENHEIT : CELSIUS;
    }

    static boolean validLatitude(double lat) {
        return !Double.isNaN(lat) && lat >= -90 && lat <= 90;
    }

    static boolean validLongitude(double lon) {
        return !Double.isNaN(lon) && lon >= -180 && lon <= 180;
    }

    /** The last stored reading, or null when there is none or it was stored in another unit. */
    static Now cached(Context ctx, String units) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!units.equals(p.getString(KEY_UNITS, ""))) return null;
        long at = p.getLong(KEY_AT, 0L);
        if (at == 0L) return null;
        return new Now(p.getInt(KEY_TEMP, 0), p.getInt(KEY_CODE, -1), p.getBoolean(KEY_DAY, true), at);
    }

    /**
     * Fetches the current weather and delivers it on the main thread. Silently does nothing while
     * another request is too recent, and backs off after a failure; a TV that is offline simply
     * keeps showing the cached reading.
     */
    static void refresh(Context ctx, double lat, double lon, String units, Listener listener) {
        long now = System.currentTimeMillis();
        if (now < nextAllowed) return;
        nextAllowed = now + MIN_GAP_MS;
        Context app = ctx.getApplicationContext();
        POOL.execute(() -> {
            Now now2 = fetch(lat, lon, units);
            if (now2 == null) {
                nextAllowed = System.currentTimeMillis() + FAILED_GAP_MS;
                return;
            }
            save(app, units, now2);
            main().post(() -> listener.onWeather(now2));
        });
    }

    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static Now fetch(double lat, double lon, String units) {
        HttpURLConnection c = null;
        try {
            String url = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,weather_code,is_day&temperature_unit=%s&timezone=auto",
                    lat, lon, units);
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestProperty("Accept", "application/json");
            try (InputStream in = c.getInputStream()) {
                JSONObject current = new JSONObject(read(in)).getJSONObject("current");
                return new Now(
                        Math.round((float) current.getDouble("temperature_2m")),
                        current.getInt("weather_code"),
                        current.optInt("is_day", 1) == 1,
                        System.currentTimeMillis());
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static void save(Context ctx, String units, Now now) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_UNITS, units)
                .putInt(KEY_TEMP, now.temperature)
                .putInt(KEY_CODE, now.code)
                .putBoolean(KEY_DAY, now.day)
                .putLong(KEY_AT, now.fetchedAt)
                .apply();
    }

    private static String read(InputStream in) throws Exception {
        byte[] buf = new byte[8192];
        StringBuilder out = new StringBuilder();
        int n;
        while ((n = in.read(buf)) > 0 && out.length() < READ_LIMIT) {
            out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    // ---- WMO weather codes to an icon and a short label -------------------------------------------------

    /** WMO code to drawable; unknown codes read as "cloudy" rather than showing nothing. */
    static int icon(int code, boolean day) {
        if (code == 0 || code == 1) return day ? R.drawable.ic_wx_clear : R.drawable.ic_wx_clear_night;
        if (code == 2) return R.drawable.ic_wx_partly;
        if (code == 45 || code == 48) return R.drawable.ic_wx_fog;
        if (code >= 51 && code <= 57) return R.drawable.ic_wx_drizzle;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return R.drawable.ic_wx_rain;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.ic_wx_snow;
        if (code >= 95 && code <= 99) return R.drawable.ic_wx_storm;
        return R.drawable.ic_wx_cloudy;
    }

    /** WMO code to a short label; unknown codes read as "cloudy". */
    static int label(int code) {
        if (code == 0 || code == 1) return R.string.wx_clear;
        if (code == 2) return R.string.wx_partly;
        if (code == 45 || code == 48) return R.string.wx_fog;
        if (code >= 51 && code <= 57) return R.string.wx_drizzle;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return R.string.wx_rain;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.string.wx_snow;
        if (code >= 95 && code <= 99) return R.string.wx_storm;
        return R.string.wx_cloudy;
    }
}
