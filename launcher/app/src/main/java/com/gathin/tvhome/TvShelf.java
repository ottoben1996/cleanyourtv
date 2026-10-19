package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the preview channels Stremio publishes for the Android TV home screen ("Continue Watching",
 * "Library – …") and their programs, so the launcher can show a poster row without any Stremio
 * account, token or API: the app already keeps that data in the TV provider.
 *
 * Fork-only feature (see docs/stremio-shelf.md): upstream cleanyourtv has no content rows, so this
 * lives here and the launcher hides the row whenever anything is missing — no Stremio installed, no
 * <em>READ_TV_LISTINGS</em> permission, channels off, empty lists — instead of showing an empty shelf.
 */
final class TvShelf {
    private static final String TAG = "TvHome.Shelf";

    /** Reading other apps' channels needs this. A runtime permission on some TV builds, install-time elsewhere. */
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    static final String DEFAULT_PACKAGE = "com.stremio.one";

    /** One poster: what the launcher needs to draw it and to open the title. */
    static final class Item {
        final long id;
        final String title;
        /** "S07E09", or null for movies and for ids that cannot be read safely. */
        final String badge;
        /** 0-100 for the thin bar, or -1 to hide it. */
        final int progress;
        /** Poster, https from Stremio's metadata providers; may be null. */
        final String posterUri;
        /** Stremio deep link (stremio:///detail/…); may be null. */
        final String intentUri;

        Item(long id, String title, String badge, int progress, String posterUri, String intentUri) {
            this.id = id;
            this.title = title;
            this.badge = badge;
            this.progress = progress;
            this.posterUri = posterUri;
            this.intentUri = intentUri;
        }
    }

    /** A channel as published by the app: its display name is the row's title. */
    static final class Channel {
        final long id;
        final String name;
        /** Browsable channels are the ones the TV is meant to show first ("Continue Watching"). */
        final boolean browsable;

        Channel(long id, String name, boolean browsable) {
            this.id = id;
            this.name = name;
            this.browsable = browsable;
        }
    }

    static final class Row {
        final String title;
        final List<Item> items;

        Row(String title, List<Item> items) {
            this.title = title == null || title.trim().isEmpty() ? "" : title.trim();
            this.items = Collections.unmodifiableList(items);
        }
    }

    /** Whether the launcher may read other apps' channels at all. */
    static boolean allowed(Context ctx) {
        return ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Reads the app's channels and their programs. Runs on a background thread (provider queries and
     * a few hundred rows); returns an empty list on any failure, never throws and never logs noise
     * when the app simply is not there.
     */
    static List<Row> load(Context ctx, String pkg, int maxRows, int maxItems) {
        try {
            List<Channel> channels = readChannels(ctx.getContentResolver(), pkg);
            if (channels.isEmpty()) return Collections.emptyList();
            sortChannels(channels);
            return buildRows(channels, readPrograms(ctx.getContentResolver(), channels), maxRows, maxItems);
        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "could not read the TV provider: " + e);
            return Collections.emptyList();
        }
    }

    private static List<Channel> readChannels(ContentResolver cr, String pkg) {
        List<Channel> out = new ArrayList<>();
        String[] cols = {
                TvContract.Channels._ID,
                TvContract.Channels.COLUMN_PACKAGE_NAME,
                TvContract.Channels.COLUMN_DISPLAY_NAME,
                TvContract.Channels.COLUMN_BROWSABLE,
        };
        // No selection: the provider only allows one on rows this app owns. There are a handful of
        // channels on a TV, so the filter is done here.
        try (Cursor c = cr.query(TvContract.Channels.CONTENT_URI, cols, null, null, null)) {
            if (c == null) return out;
            while (c.moveToNext()) {
                if (!pkg.equals(c.getString(1))) continue;
                String name = c.getString(2);
                boolean browsable = !c.isNull(3) && c.getInt(3) != 0;
                out.add(new Channel(c.getLong(0), name == null ? "" : name, browsable));
            }
        }
        return out;
    }

    private static Map<Long, List<Item>> readPrograms(ContentResolver cr, List<Channel> channels) {
        Map<Long, List<Item>> out = new HashMap<>();
        Set<Long> wanted = new HashSet<>();
        for (Channel ch : channels) wanted.add(ch.id);
        String[] cols = {
                TvContract.PreviewPrograms._ID,
                TvContract.PreviewPrograms.COLUMN_CHANNEL_ID,
                TvContract.PreviewPrograms.COLUMN_TITLE,
                TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
                TvContract.PreviewPrograms.COLUMN_INTENT_URI,
                TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
                TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                // On PreviewPrograms these two have no framework constants; the columns exist in the
                // preview_programs table and a Stremio build that fills them is used when present.
                "season_number",
                "episode_number",
        };
        try (Cursor c = cr.query(TvContract.PreviewPrograms.CONTENT_URI, cols, null, null, null)) {
            if (c == null) return out;
            while (c.moveToNext()) {
                long channelId = c.getLong(1);
                if (!wanted.contains(channelId)) continue;
                String title = c.getString(2);
                if (title == null || title.trim().isEmpty()) continue;
                String intent = c.getString(4);
                Integer season = c.isNull(7) ? null : c.getInt(7);
                Integer episode = c.isNull(8) ? null : c.getInt(8);
                long duration = c.isNull(5) ? 0 : c.getLong(5);
                long position = c.isNull(6) ? 0 : c.getLong(6);
                Item item = new Item(c.getLong(0), title.trim(), badge(intent, season, episode),
                        progress(position, duration), c.getString(3), intent);
                List<Item> list = out.get(channelId);
                if (list == null) {
                    list = new ArrayList<>();
                    out.put(channelId, list);
                }
                list.add(item);
            }
        }
        return out;
    }

    /** Browsable channel first ("Continue Watching"), then by id, which is the order Stremio publishes. */
    static void sortChannels(List<Channel> channels) {
        Collections.sort(channels, (a, b) -> {
            if (a.browsable != b.browsable) return a.browsable ? -1 : 1;
            return Long.compare(a.id, b.id);
        });
    }

    /** One row per channel that actually has programs, up to the limits; empty channels are skipped. */
    static List<Row> buildRows(List<Channel> channels, Map<Long, List<Item>> byChannel, int maxRows, int maxItems) {
        List<Row> rows = new ArrayList<>();
        if (maxRows <= 0 || maxItems <= 0) return rows;
        for (Channel ch : channels) {
            if (rows.size() >= maxRows) break;
            List<Item> items = byChannel.get(ch.id);
            if (items == null || items.isEmpty()) continue;
            rows.add(new Row(ch.name, new ArrayList<>(items.subList(0, Math.min(maxItems, items.size())))));
        }
        return rows;
    }

    /**
     * "S07E09" from the season/episode columns, or from the video id a Stremio episode deep link
     * carries (stremio:///detail/series/&lt;meta&gt;/tt6524350%3A7%3A9 = tt6524350:7:9). Ids that are
     * not IMDb-shaped are left alone: an anime add-on's "fk:12:208" is a series id and an episode
     * number, not a season, and would print wrong numbers. Null hides the badge.
     */
    static String badge(String intentUri, Integer season, Integer episode) {
        if (season != null && season > 0 && episode != null && episode > 0) return episodeBadge(season, episode);
        if (intentUri == null) return null;
        int query = intentUri.indexOf('?');
        String path = query < 0 ? intentUri : intentUri.substring(0, query);
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash == path.length() - 1) return null;
        String[] parts = percentDecode(path.substring(slash + 1)).split(":");
        if (parts.length != 3 || !parts[0].matches("tt\\d+")) return null;
        try {
            int s = Integer.parseInt(parts[1]);
            int e = Integer.parseInt(parts[2]);
            return s > 0 && e > 0 ? episodeBadge(s, e) : null;
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private static String episodeBadge(int season, int episode) {
        return String.format(Locale.ROOT, "S%02dE%02d", season, episode);
    }

    /** 0-100 for the thin progress bar, or -1 to hide it. A position of 1 ms means "never started". */
    static int progress(long positionMs, long durationMs) {
        if (durationMs <= 0 || positionMs <= 1000) return -1;
        int pct = (int) Math.min(100, positionMs * 100 / durationMs);
        return Math.max(1, pct);
    }

    /** The intent for a shelf item, ready to start; null when the deep link is missing or unusable. */
    static Intent launchIntent(String intentUri, String pkg) {
        if (intentUri == null || intentUri.isEmpty()) return null;
        try {
            Intent i = Intent.parseUri(intentUri, intentUri.startsWith("intent:") ? Intent.URI_INTENT_SCHEME : 0);
            if (i.getPackage() == null && i.getComponent() == null) i.setPackage(pkg);
            return i;
        } catch (Exception e) {
            return null;
        }
    }

    /** Decodes %XX (Stremio encodes ':' as %3A in its deep links); plain ASCII, no deprecation traps. */
    static String percentDecode(String s) {
        if (s.indexOf('%') < 0) return s;
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '%' && i + 2 < s.length()) {
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write(hi * 16 + lo);
                    i += 2;
                    continue;
                }
            }
            byte[] bytes = String.valueOf(ch).getBytes(StandardCharsets.UTF_8);
            out.write(bytes, 0, bytes.length);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
