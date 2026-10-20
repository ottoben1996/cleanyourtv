package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The launcher's settings: the values in res/values/config.xml, with an optional overlay read from
 * {@code config.json} in the app's external files directory
 * ({@code /sdcard/Android/data/com.gathin.tvhome/files/}). Every key in the JSON is optional and an
 * unknown or badly typed key is ignored, so a theme can change the dock and the look without a rebuild:
 * {@code tools/theme.sh themes/<name>} pushes a theme folder there and restarts Home.
 *
 * Read once in {@link MainActivity#onCreate}; nothing here touches the network. The sky_* and
 * weather_* keys are read here as well; the weather request itself lives in {@link Weather}.
 */
final class Config {
    private static final String TAG = "TvHome.Config";
    static final String FILE_NAME = "config.json";
    static final String WALLPAPER_NAME = "wallpaper.jpg";

    final List<String> dockApps;
    final List<String> hiddenApps;
    final String liveTvPackage;
    final String liveTvAction;
    final String sourcePanelService;
    final String sourcePanelAction;
    final String inputAction;
    final int columns;
    final int rowsBelowDock;
    final boolean shamsiDate;
    final boolean moduleCards;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    final boolean reducedMotion;
    final String vpnApp;
    final boolean showHomeSettingsShortcut;
    /** Background colour behind everything, #RRGGBB; black unless a theme says otherwise. */
    final int backgroundColor;
    /** Sunset backdrop, 0 to 100 (config.xml, sky_intensity); 0 keeps the plain background. */
    final int skyIntensity;
    /** Weather line: whether it is wanted at all (config.xml, weather_enabled). */
    final boolean weatherEnabled;
    /** Latitude and longitude for the weather line; null unless set to a valid coordinate. */
    final Double weatherLatitude;
    final Double weatherLongitude;
    /** Optional place name shown before the temperature ("Paris 14° · Nuageux"); may be empty. */
    final String weatherPlace;
    /** "celsius" or "fahrenheit"; anything else reads as celsius. */
    final String weatherUnits;
    /** How long a reading stays fresh, in minutes (15 to 720). */
    final int weatherRefreshMinutes;
    /** Dock shelves: whether the content rows are wanted at all (config.xml, stremio_shelf). */
    final boolean stremioShelf;
    /** Download posters instead of coloured placeholders (config.xml, stremio_shelf_posters). */
    final boolean stremioShelfPosters;
    /** How many channel rows to show (stremio_shelf_rows, 1 to 3). */
    final int stremioShelfRows;
    /** Most posters per row (stremio_shelf_max_items, 1 to 20). */
    final int stremioShelfMaxItems;
    /** The app whose TV channels feed the shelf (stremio_shelf_package). */
    final String stremioShelfPackage;
    /** Full-screen picture behind the launcher, or null for the plain colour. */
    final File wallpaper;
    /** Whether the overlay file was present and parsed. */
    final boolean overlayApplied;

    private Config(List<String> dockApps, List<String> hiddenApps, String liveTvPackage, String liveTvAction,
                   String sourcePanelService, String sourcePanelAction, String inputAction,
                   int columns, int rowsBelowDock,
                   boolean shamsiDate, boolean moduleCards, boolean reducedMotion, String vpnApp,
                   boolean showHomeSettingsShortcut, int backgroundColor,
                   int skyIntensity, boolean weatherEnabled, Double weatherLatitude, Double weatherLongitude,
                   String weatherPlace, String weatherUnits, int weatherRefreshMinutes,
                   boolean stremioShelf, boolean stremioShelfPosters, int stremioShelfRows,
                   int stremioShelfMaxItems, String stremioShelfPackage,
                   File wallpaper, boolean overlayApplied) {
        this.dockApps = Collections.unmodifiableList(dockApps);
        this.hiddenApps = Collections.unmodifiableList(hiddenApps);
        this.liveTvPackage = liveTvPackage;
        this.liveTvAction = liveTvAction;
        this.sourcePanelService = sourcePanelService;
        this.sourcePanelAction = sourcePanelAction;
        this.inputAction = inputAction;
        this.columns = columns;
        this.rowsBelowDock = rowsBelowDock;
        this.shamsiDate = shamsiDate;
        this.moduleCards = moduleCards;
        this.reducedMotion = reducedMotion;
        this.vpnApp = vpnApp;
        this.showHomeSettingsShortcut = showHomeSettingsShortcut;
        this.backgroundColor = backgroundColor;
        this.skyIntensity = skyIntensity;
        this.weatherEnabled = weatherEnabled;
        this.weatherLatitude = weatherLatitude;
        this.weatherLongitude = weatherLongitude;
        this.weatherPlace = weatherPlace;
        this.weatherUnits = weatherUnits;
        this.weatherRefreshMinutes = weatherRefreshMinutes;
        this.stremioShelf = stremioShelf;
        this.stremioShelfPosters = stremioShelfPosters;
        this.stremioShelfRows = stremioShelfRows;
        this.stremioShelfMaxItems = stremioShelfMaxItems;
        this.stremioShelfPackage = stremioShelfPackage;
        this.wallpaper = wallpaper;
        this.overlayApplied = overlayApplied;
    }

    /** The folder the theme tools push into; null when external storage is unavailable. */
    static File dir(Context ctx) {
        return ctx.getExternalFilesDir(null);
    }

    static Config load(Context ctx) {
        Resources res = ctx.getResources();
        List<String> dock = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.dock_apps)));
        List<String> hidden = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.hidden_apps)));
        String liveTv = res.getString(R.string.live_tv_package).trim();
        String liveTvAction = res.getString(R.string.live_tv_action).trim();
        String panelService = res.getString(R.string.source_panel_service).trim();
        String panelAction = res.getString(R.string.source_panel_action).trim();
        String inputAction = res.getString(R.string.input_action).trim();
        int columns = res.getInteger(R.integer.columns);
        int rowsBelow = res.getInteger(R.integer.rows_below_dock);
        boolean shamsi = res.getBoolean(R.bool.shamsi_date);
        boolean moduleCards = res.getBoolean(R.bool.module_cards);
        boolean reducedMotion = res.getBoolean(R.bool.reduced_motion);
        String vpn = res.getString(R.string.vpn_app).trim();
        boolean homeSettings = res.getBoolean(R.bool.show_home_settings_shortcut);
        int background = 0xFF000000;
        int skyIntensity = res.getInteger(R.integer.sky_intensity);
        boolean weatherEnabled = res.getBoolean(R.bool.weather_enabled);
        Double weatherLat = coordinate(res.getString(R.string.weather_latitude));
        Double weatherLon = coordinate(res.getString(R.string.weather_longitude));
        String weatherPlace = res.getString(R.string.weather_place).trim();
        String weatherUnits = Weather.units(res.getString(R.string.weather_units));
        int weatherRefresh = Math.max(15, res.getInteger(R.integer.weather_refresh_minutes));
        boolean shelf = res.getBoolean(R.bool.stremio_shelf);
        boolean shelfPosters = res.getBoolean(R.bool.stremio_shelf_posters);
        int shelfRows = res.getInteger(R.integer.stremio_shelf_rows);
        int shelfItems = res.getInteger(R.integer.stremio_shelf_max_items);
        String shelfPackage = res.getString(R.string.stremio_shelf_package).trim();
        boolean applied = false;

        File dir = dir(ctx);
        File file = dir != null ? new File(dir, FILE_NAME) : null;
        JSONObject json = read(file);
        if (json != null) {
            applied = true;
            dock = stringList(json, "dock_apps", dock);
            hidden = stringList(json, "hidden_apps", hidden);
            liveTv = string(json, "live_tv_package", liveTv);
            liveTvAction = string(json, "live_tv_action", liveTvAction);
            panelService = string(json, "source_panel_service", panelService);
            panelAction = string(json, "source_panel_action", panelAction);
            inputAction = string(json, "input_action", inputAction);
            columns = integer(json, "columns", columns, 1, 8);
            rowsBelow = integer(json, "rows_below_dock", rowsBelow, 0, 3);
            shamsi = bool(json, "shamsi_date", shamsi);
            moduleCards = bool(json, "module_cards", moduleCards);
            reducedMotion = bool(json, "reduced_motion", reducedMotion);
            vpn = string(json, "vpn_app", vpn);
            homeSettings = bool(json, "show_home_settings_shortcut", homeSettings);
            background = color(json, "background_color", background);
            skyIntensity = integer(json, "sky_intensity", skyIntensity, 0, 100);
            weatherEnabled = bool(json, "weather_enabled", weatherEnabled);
            weatherLat = coordinate(json, "weather_latitude", weatherLat);
            weatherLon = coordinate(json, "weather_longitude", weatherLon);
            weatherPlace = string(json, "weather_place", weatherPlace);
            weatherUnits = Weather.units(string(json, "weather_units", weatherUnits));
            weatherRefresh = integer(json, "weather_refresh_minutes", weatherRefresh, 15, 720);
            shelf = bool(json, "stremio_shelf", shelf);
            shelfPosters = bool(json, "stremio_shelf_posters", shelfPosters);
            shelfRows = integer(json, "stremio_shelf_rows", shelfRows, 1, 3);
            shelfItems = integer(json, "stremio_shelf_max_items", shelfItems, 1, 20);
            shelfPackage = string(json, "stremio_shelf_package", shelfPackage);
        }
        // A coordinate outside its range is ignored, keeping the weather line hidden rather than wrong.
        if (weatherLat != null && !Weather.validLatitude(weatherLat)) weatherLat = null;
        if (weatherLon != null && !Weather.validLongitude(weatherLon)) weatherLon = null;
        File wallpaper = dir != null ? new File(dir, WALLPAPER_NAME) : null;
        if (wallpaper != null && !(wallpaper.isFile() && wallpaper.length() > 0)) wallpaper = null;
        // A theme can also switch the wallpaper off while the file is still there.
        if (json != null && json.has("wallpaper") && !bool(json, "wallpaper", true)) wallpaper = null;
        // An empty package would silently disable the shelf; fall back to Stremio's.
        if (shelfPackage.isEmpty()) shelfPackage = TvShelf.DEFAULT_PACKAGE;

        return new Config(dock, hidden, liveTv, liveTvAction, panelService, panelAction, inputAction,
                Math.max(1, columns), Math.max(0, rowsBelow), shamsi, moduleCards, reducedMotion,
                vpn, homeSettings, background,
                Math.max(0, Math.min(100, skyIntensity)), weatherEnabled, weatherLat, weatherLon,
                weatherPlace, weatherUnits, weatherRefresh,
                shelf, shelfPosters, Math.max(1, Math.min(3, shelfRows)), Math.max(1, Math.min(20, shelfItems)),
                shelfPackage, wallpaper, applied);
    }

    /** A coordinate from config.xml: blank or unparsable means "not set". */
    private static Double coordinate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            double v = Double.parseDouble(raw.trim());
            return Double.isNaN(v) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A coordinate from a theme: a number, or a string holding one; anything else keeps the fallback. */
    private static Double coordinate(JSONObject o, String key, Double fallback) {
        Object v = o.opt(key);
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            return Double.isNaN(d) ? fallback : d;
        }
        if (v instanceof String) {
            Double d = coordinate((String) v);
            return d != null ? d : fallback;
        }
        return fallback;
    }

    /** Reads and parses the overlay; any problem means "no overlay", never a crash on the home screen. */
    private static JSONObject read(File file) {
        if (file == null || !file.isFile()) return null;
        if (file.length() > 64 * 1024) {
            Log.w(TAG, "ignoring " + file + ": larger than 64 KB");
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) text = text.substring(1);   // Windows editors add a BOM
            return new JSONObject(text);
        } catch (Exception e) {
            Log.w(TAG, "ignoring " + file + ": " + e.getMessage());
            return null;
        }
    }

    private static String string(JSONObject o, String key, String fallback) {
        Object v = o.opt(key);
        return v instanceof String ? ((String) v).trim() : fallback;
    }

    private static boolean bool(JSONObject o, String key, boolean fallback) {
        Object v = o.opt(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static int integer(JSONObject o, String key, int fallback, int min, int max) {
        Object v = o.opt(key);
        if (!(v instanceof Number)) return fallback;
        int i = ((Number) v).intValue();
        return i < min || i > max ? fallback : i;
    }

    private static int color(JSONObject o, String key, int fallback) {
        Object v = o.opt(key);
        if (!(v instanceof String)) return fallback;
        try {
            int c = Color.parseColor(((String) v).trim());
            return c | 0xFF000000;   // always opaque: the window behind is black anyway
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static List<String> stringList(JSONObject o, String key, List<String> fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (item instanceof String && !((String) item).trim().isEmpty()) out.add(((String) item).trim());
        }
        return out;
    }
}
