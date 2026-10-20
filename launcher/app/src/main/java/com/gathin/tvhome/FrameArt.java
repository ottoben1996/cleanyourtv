package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Frame mode's pure-Java heart: the gallery order, how it wraps, where the print sits on the
 * wall and when the room is dark. Kept away from android.graphics so the maths runs in the JVM tests
 * (FrameArtTest); the view and the activity only draw what comes out of here.
 */
final class FrameArt {

    private FrameArt() {
    }

    /** The bundled paintings, in the order their numbered asset names give them. */
    static List<String> artworks(String[] names) {
        List<String> out = new ArrayList<>();
        if (names == null) return out;
        for (String name : names) {
            if (isImage(name)) out.add(name);
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    static boolean isImage(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    /** The next index, wrapping at both ends; 0 when there is nothing to show. */
    static int step(int index, int count, int delta) {
        if (count <= 0) return 0;
        return ((index + delta) % count + count) % count;
    }

    /** The room is dark from 22:00 to 07:59, so the painting is dimmed then. */
    static boolean night(int hour) {
        return hour >= 22 || hour < 8;
    }

    /** A rectangle in pixels; avoids android.graphics so the fit maths stays testable on the JVM. */
    static final class Box {
        final float left;
        final float top;
        final float right;
        final float bottom;

        Box(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        float width() {
            return right - left;
        }

        float height() {
            return bottom - top;
        }
    }

    /** The largest rectangle with the image's aspect ratio that fits, centred, inside {@code area}. */
    static Box fit(int imageWidth, int imageHeight, Box area) {
        if (imageWidth <= 0 || imageHeight <= 0 || area.width() <= 0 || area.height() <= 0) {
            return area;
        }
        float scale = Math.min(area.width() / imageWidth, area.height() / imageHeight);
        float w = imageWidth * scale;
        float h = imageHeight * scale;
        float left = area.left + (area.width() - w) / 2f;
        float top = area.top + (area.height() - h) / 2f;
        return new Box(left, top, left + w, top + h);
    }
}
