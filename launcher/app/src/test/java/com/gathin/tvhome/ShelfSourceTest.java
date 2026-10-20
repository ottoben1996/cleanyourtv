package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Which dock tiles get a shelf, from where, and how their cards open. */
public class ShelfSourceTest {

    @Test
    public void stremioTileKeepsItsPosterRowsAndOwnLinks() {
        ShelfSource s = ShelfSource.forTile("com.stremio.one", "com.stremio.one", false, "For you");
        assertNotNull(s);
        assertEquals("com.stremio.one", s.provider);
        assertEquals(ShelfSource.POSTER_W_DP, s.cardWidthDp);
        assertEquals(ShelfSource.POSTER_H_DP, s.cardHeightDp);
        assertNull(s.label);
        assertFalse(s.watchNext);
        assertNull(s.rewrite());
    }

    @Test
    public void spotifyTileShowsItsCoversAsForYou() {
        ShelfSource s = ShelfSource.forTile("com.spotify.tv.android", "com.stremio.one", false, "Pour vous");
        assertNotNull(s);
        assertEquals("com.spotify.tv.android", s.provider);
        assertEquals(ShelfSource.COVER_W_DP, s.cardWidthDp);
        assertEquals(ShelfSource.COVER_H_DP, s.cardHeightDp);
        assertEquals("Pour vous", s.label);
        assertFalse(s.watchNext);
        assertTrue(s.artOnly);
        assertNull(s.rewrite());
    }

    @Test
    public void aTileThatPlaysYouTubeGetsThePublishersRowsRewritten() {
        ShelfSource s = ShelfSource.forTile("io.gh.reisxd.tizentube.cobalt", "com.stremio.one", true, "For you");
        assertNotNull(s);
        assertEquals(ShelfSource.YOUTUBE_PUBLISHER, s.provider);
        assertEquals(ShelfSource.VIDEO_W_DP, s.cardWidthDp);
        assertEquals(ShelfSource.VIDEO_H_DP, s.cardHeightDp);
        assertTrue(s.watchNext);
        assertTrue(s.artOnly);
        assertEquals("io.gh.reisxd.tizentube.cobalt", s.openIn);
        assertNotNull(s.rewrite());
        assertEquals("https://www.youtube.com/watch?v=abc123DEF45",
                s.rewrite().rewrite("https://www.youtube.com/watch?v=abc123DEF45&launch=launcher"));
    }

    @Test
    public void aTileWithNothingToReadHasNoSource() {
        // GoldenOTT publishes no channels, no preview programs and no watch next.
        assertNull(ShelfSource.forTile("com.new_Golden_ott_user.app", "com.stremio.one", false, "For you"));
        assertNull(ShelfSource.forTile(null, "com.stremio.one", false, "For you"));
        assertNull(ShelfSource.forTile("", "com.stremio.one", false, "For you"));
    }
}
