package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Artwork for the dock shelves. The apps publish https:// URLs (Stremio's metadata providers,
 * Spotify's CDN, YouTube's thumbnails), so the launcher downloads them itself: one background
 * thread, an 8 MB bitmap cache in memory, and a 40 MB cache on disk so an image is fetched once and
 * decoded down to what the screen shows. No image library: a TV with 1 GB of RAM does not need one
 * for a row of cards.
 */
final class PosterLoader {
    private static final int MEM_BYTES = 8 * 1024 * 1024;
    private static final long DISK_BYTES = 40L * 1024 * 1024;
    private static final int MAX_DOWNLOAD = 8 * 1024 * 1024;
    private static final String DIR = "shelf-posters";

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(MEM_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "shelf-posters");
        t.setPriority(Thread.NORM_PRIORITY - 1);   // never compete with the UI or playback
        return t;
    });

    PosterLoader(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** Drops the caches and stops the worker; the loader is not usable afterwards. */
    void shutdown() {
        io.shutdown();
        memory.evictAll();
    }

    /** Loads uri into the view, fitted to widthPx × heightPx; the view is left alone when it goes wrong. */
    void into(String uri, ImageView view, int widthPx, int heightPx, Runnable loaded) {
        if (uri == null || uri.isEmpty() || widthPx <= 0 || heightPx <= 0) return;
        Bitmap hit = memory.get(uri);
        view.setTag(uri);                          // a recycled view must not end up with another poster
        if (hit != null) {
            view.setImageBitmap(hit);
            if (loaded != null) loaded.run();
            return;
        }
        view.setImageDrawable(null);
        io.execute(() -> {
            Bitmap bmp = fromDisk(uri, widthPx, heightPx);
            if (bmp == null) bmp = download(uri, widthPx, heightPx);
            if (bmp == null) return;
            memory.put(uri, bmp);
            final Bitmap out = bmp;
            main.post(() -> {
                if (uri.equals(view.getTag())) {
                    view.setImageBitmap(out);
                    if (loaded != null) loaded.run();
                }
            });
        });
    }

    private Bitmap fromDisk(String uri, int widthPx, int heightPx) {
        File f = fileFor(uri);
        if (f == null || !f.isFile() || f.length() == 0) return null;
        Bitmap bmp = decode(f.getPath(), widthPx, heightPx);
        if (bmp == null) f.delete();
        return bmp;
    }

    /** Fetches a poster over https (or copies a content:///file:// one), then decodes it. */
    private Bitmap download(String uri, int widthPx, int heightPx) {
        Uri u;
        try {
            u = Uri.parse(uri);
        } catch (Exception e) {
            return null;
        }
        String scheme = u.getScheme();
        if (scheme == null) return null;
        try {
            if ("file".equals(scheme)) {
                return decode(u.getPath(), widthPx, heightPx);
            }
            InputStream in;
            if ("content".equals(scheme)) {
                in = ctx.getContentResolver().openInputStream(u);
            } else if ("http".equals(scheme) || "https".equals(scheme)) {
                in = openHttp(uri);
            } else {
                return null;
            }
            if (in == null) return null;
            byte[] data;
            try {
                data = read(in);
            } finally {
                in.close();
            }
            if (data.length == 0) return null;
            File f = fileFor(uri);
            if (f == null) return null;
            File tmp = new File(f.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
            } catch (Exception e) {
                tmp.delete();
                return null;
            }
            if (!tmp.renameTo(f)) {
                tmp.delete();
                return null;
            }
            trim();
            return decode(f.getPath(), widthPx, heightPx);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private static InputStream openHttp(String uri) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(uri).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "CleanYourTV");
        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            return null;
        }
        return conn.getInputStream();
    }

    private static byte[] read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_DOWNLOAD) return new byte[0];   // not a poster
        }
        return out.toByteArray();
    }

    /** Decodes a file down to about the view's size: a 1080p poster must not cost a 1080p bitmap. */
    private static Bitmap decode(String path, int widthPx, int heightPx) {
        if (path == null) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= widthPx && bounds.outHeight / (sample * 2) >= heightPx) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;   // opaque posters: half the memory of ARGB
        try {
            return BitmapFactory.decodeFile(path, opts);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private File fileFor(String uri) {
        File dir = new File(ctx.getCacheDir(), DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        return new File(dir, sha1(uri));
    }

    private static String sha1(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /** Keeps the poster cache under DISK_BYTES by dropping the oldest files. */
    private void trim() {
        File dir = new File(ctx.getCacheDir(), DIR);
        File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= DISK_BYTES) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : files) {
            if (total <= DISK_BYTES * 9 / 10) break;
            long len = f.length();
            if (f.delete()) total -= len;
        }
    }
}
