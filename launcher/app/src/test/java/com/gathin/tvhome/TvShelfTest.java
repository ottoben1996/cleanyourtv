package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the shelf's parsing: episode badges, progress bars and row building. */
public class TvShelfTest {

    @Test
    public void episodeBadgeComesFromTheVideoId() {
        assertEquals("S07E09", TvShelf.badge("stremio:///detail/series/tmdb%3A74204/tt6524350%3A7%3A9?autoPlay=true", null, null));
        assertEquals("S01E01", TvShelf.badge("stremio:///detail/series/tt1234567/tt1234567%3A1%3A1", null, null));
    }

    @Test
    public void badgeSkipsIdsThatWouldPrintWrongNumbers() {
        // An anime add-on's "fk:12:208" is a series id (fk:12) plus an episode, not a season.
        assertNull(TvShelf.badge("stremio:///detail/anime/fk%3A12/fk%3A12%3A208", null, null));
        assertNull(TvShelf.badge("stremio:///detail/movie/tt1856101", null, null));
        assertNull(TvShelf.badge("stremio:///detail/series/tt1234567/tt1234567%3A%3A", null, null));
        assertNull(TvShelf.badge(null, null, null));
    }

    @Test
    public void badgePrefersTheColumnsWhenStremioFillsThem() {
        assertEquals("S02E05", TvShelf.badge(null, 2, 5));
        assertEquals("S02E05", TvShelf.badge("stremio:///detail/series/x/y", 2, 5));
        assertNull(TvShelf.badge(null, 0, 5));
    }

    @Test
    public void progressHidesForUnstartedOrUnknownItems() {
        assertEquals(-1, TvShelf.progress(0, 9_759_134));
        assertEquals(-1, TvShelf.progress(1, 9_759_134));      // Stremio's "never started" marker
        assertEquals(-1, TvShelf.progress(5_000, 0));
        assertEquals(18, TvShelf.progress(1_841_448, 9_759_134));
        assertEquals(1, TvShelf.progress(2_000, 9_759_134));   // barely started: a sliver, not nothing
        assertEquals(100, TvShelf.progress(9_759_134, 9_759_134));
    }

    @Test
    public void browsableChannelSortsFirstThenById() {
        List<TvShelf.Channel> channels = new ArrayList<>(Arrays.asList(
                new TvShelf.Channel(55, "Library - Movies", false),
                new TvShelf.Channel(39, "Continue watching", true),
                new TvShelf.Channel(41, "Library - Series", false)));
        TvShelf.sortChannels(channels);
        assertEquals(39, channels.get(0).id);
        assertEquals(41, channels.get(1).id);
        assertEquals(55, channels.get(2).id);
    }

    @Test
    public void rowsSkipEmptyChannelsAndRespectTheLimits() {
        List<TvShelf.Channel> channels = new ArrayList<>(Arrays.asList(
                new TvShelf.Channel(39, "Continue watching", true),
                new TvShelf.Channel(41, "Library - Series", false),
                new TvShelf.Channel(55, "Library - Movies", false)));
        Map<Long, List<TvShelf.Item>> byChannel = new HashMap<>();
        byChannel.put(39L, Arrays.asList(item(1), item(2), item(3)));
        byChannel.put(55L, Collections.singletonList(item(4)));   // 41 has nothing to show
        List<TvShelf.Row> rows = TvShelf.buildRows(channels, byChannel, 2, 2);
        assertEquals(2, rows.size());
        assertEquals("Continue watching", rows.get(0).title);
        assertEquals(2, rows.get(0).items.size());
        assertEquals("Library - Movies", rows.get(1).title);
        assertEquals(1, rows.get(1).items.size());
        assertTrue(TvShelf.buildRows(channels, byChannel, 0, 5).isEmpty());
        assertTrue(TvShelf.buildRows(channels, byChannel, 2, 0).isEmpty());
    }

    private static TvShelf.Item item(long id) {
        return new TvShelf.Item(id, "Title " + id, null, -1, null, null);
    }
}
