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

    @Test
    public void youtubeWatchUrlUnwrapsPublisherLinksAndPlainOnes() {
        // What the Google YouTube app publishes: an intent:// wrapper around a watch URL.
        assertEquals("https://www.youtube.com/watch?v=G02QEhmleYA",
                TvShelf.youtubeWatchUrl("intent://www.youtube.com/watch?v=G02QEhmleYA&launch=launcher"
                        + "#Intent;scheme=https;package=com.google.android.youtube.tv;end"));
        // What the watch-next table stores: a plain URL with tracking parameters.
        assertEquals("https://www.youtube.com/watch?v=IBzI77JH-qE",
                TvShelf.youtubeWatchUrl("https://www.youtube.com/watch?v=IBzI77JH-qE&launch=launcher&parentCsn=Z-69"));
        assertEquals("https://www.youtube.com/watch?v=X4klc4KNPbg",
                TvShelf.youtubeWatchUrl("https://youtu.be/X4klc4KNPbg?t=30"));
        assertEquals("https://www.youtube.com/watch?v=abc123DEF45",
                TvShelf.youtubeWatchUrl("https://www.youtube.com/shorts/abc123DEF45"));
    }

    @Test
    public void youtubeWatchUrlLeavesOtherLinksAlone() {
        assertNull(TvShelf.youtubeWatchUrl("stremio:///detail/movie/tt1856101"));
        assertNull(TvShelf.youtubeWatchUrl("https://www.youtube.com/watch"));      // no video id
        assertNull(TvShelf.youtubeWatchUrl("https://example.com/watch?v=abcdefgh")); // another site
        assertNull(TvShelf.youtubeWatchUrl(null));
    }

    @Test
    public void resumeRowLeadsWithWatchedTitlesAndDropsDuplicates() {
        TvShelf.Item watchedA = play(1, "Watched A", 40, "aaa111");
        TvShelf.Item watchedB = play(2, "Watched B", 12, "bbb222");
        TvShelf.Item sameAsB = play(9, "Recommended B", -1, "bbb222");
        TvShelf.Item fresh = play(3, "Fresh", -1, "ccc333");
        List<TvShelf.Row> watched = Collections.singletonList(
                new TvShelf.Row("Continue watching", Arrays.asList(watchedA, watchedB)));
        List<TvShelf.Row> channels = Arrays.asList(
                new TvShelf.Row("Recommandations", Arrays.asList(sameAsB, fresh)),
                new TvShelf.Row("Abonnements", Collections.singletonList(item(4))));

        List<TvShelf.Row> rows = TvShelf.mergeResume(watched, channels, "For you");
        assertEquals(2, rows.size());
        assertEquals("For you", rows.get(0).title);
        assertEquals(3, rows.get(0).items.size());
        assertEquals("Watched A", rows.get(0).items.get(0).title);
        assertEquals("Watched B", rows.get(0).items.get(1).title);   // the resume copy wins over the pick
        assertEquals("Fresh", rows.get(0).items.get(2).title);
        assertEquals("Abonnements", rows.get(1).title);             // later rows keep their own name
    }

    @Test
    public void resumeRowSurvivesWithoutChannelRows() {
        List<TvShelf.Row> watched = Collections.singletonList(
                new TvShelf.Row("Continue watching", Collections.singletonList(item(1))));
        List<TvShelf.Row> rows = TvShelf.mergeResume(watched, Collections.emptyList(), "For you");
        assertEquals(1, rows.size());
        assertEquals("For you", rows.get(0).title);
        assertEquals(1, rows.get(0).items.size());
        assertTrue(TvShelf.mergeResume(Collections.emptyList(), Collections.emptyList(), "For you").isEmpty());
    }

    @Test
    public void cardsWithoutArtworkAreDropped() {
        List<TvShelf.Row> rows = Arrays.asList(
                new TvShelf.Row("For you", Arrays.asList(
                        new TvShelf.Item(1, "Prompt", null, -1, null, "a"),
                        new TvShelf.Item(2, "Blank", null, -1, "  ", "b"),
                        new TvShelf.Item(5, "Banner", null, -1, "android.resource://com.spotify/drawable/banner", "e"),
                        new TvShelf.Item(3, "Cover", null, -1, "https://i.scdn.co/a", "c"))),
                new TvShelf.Row("Prompts only", Collections.singletonList(
                        new TvShelf.Item(4, "Prompt", null, -1, null, "d"))));
        List<TvShelf.Row> kept = TvShelf.withoutMissingArt(rows);
        assertEquals(1, kept.size());
        assertEquals("For you", kept.get(0).title);
        assertEquals(1, kept.get(0).items.size());
        assertEquals("Cover", kept.get(0).items.get(0).title);
    }

    private static TvShelf.Item item(long id) {
        return new TvShelf.Item(id, "Title " + id, null, -1, null, null);
    }

    private static TvShelf.Item play(long id, String title, int progress, String videoId) {
        return new TvShelf.Item(id, title, null, progress, "https://i.ytimg.com/" + videoId + ".jpg",
                "https://www.youtube.com/watch?v=" + videoId);
    }
}
