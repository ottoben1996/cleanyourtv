package com.gathin.tvhome;

/**
 * One dock tile that shows a content row: whose data the row is read from, how big its cards are,
 * and whether its cards open in the tile's own app. Everything is read through the TV provider the
 * apps fill themselves (see {@link TvShelf}); no account, token or API is involved.
 *
 * Three sources exist:
 * <ul>
 *   <li>the Stremio tile (or whatever {@code stremio_shelf_package} names): its poster rows, links followed as-is;</li>
 *   <li>Spotify: its own recommendation row, square covers, its own play links;</li>
 *   <li>any tile that claims youtube.com links (TizenTube does): the Google YouTube app's
 *       recommendation rows and watch-next list, its cards rewritten to a plain watch URL so they
 *       play in <em>that</em> tile's app — YouTube's own rows would otherwise open in the publisher.</li>
 * </ul>
 *
 * A tile with no source (GoldenOTT publishes nothing) keeps the plain hero name. Fork-only feature:
 * see docs/dock-shelves.md.
 */
final class ShelfSource {
    /** Spotify publishes its rows itself: square covers and open.spotify.com play links. */
    static final String SPOTIFY = "com.spotify.tv.android";
    /** The app that publishes YouTube's rows; any tile that plays youtube.com URLs shows them. */
    static final String YOUTUBE_PUBLISHER = "com.google.android.youtube.tv";
    /** The link a tile must claim for the YouTube rows to be rewritten into it. */
    static final String YOUTUBE_PROBE = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    /** Card designs (dp), by what the artwork is: posters are 2:3, Spotify covers square, video 16:9. */
    static final int POSTER_W_DP = 96, POSTER_H_DP = 144;
    static final int COVER_W_DP = 120, COVER_H_DP = 120;
    static final int VIDEO_W_DP = 176, VIDEO_H_DP = 99;

    /** The dock tile these rows show under. */
    final String tilePackage;
    /** Whose channels (and watch-next row) the rows are read from. */
    final String provider;
    final int cardWidthDp;
    final int cardHeightDp;
    /** Row label override, or null to keep the channel's own name ("Recommandations", "Tendances"). */
    final String label;
    /** Whether to read the provider's watch-next row ("Continue watching") as well. */
    final boolean watchNext;
    /** Whether a card without artwork is a prompt rather than a title (Spotify, YouTube), and is dropped. */
    final boolean artOnly;
    /** Force cards into this package (a rewritten watch URL); null follows the card's own deep link. */
    final String openIn;

    private ShelfSource(String tilePackage, String provider, int cardWidthDp, int cardHeightDp,
                        String label, boolean watchNext, boolean artOnly, String openIn) {
        this.tilePackage = tilePackage;
        this.provider = provider;
        this.cardWidthDp = cardWidthDp;
        this.cardHeightDp = cardHeightDp;
        this.label = label;
        this.watchNext = watchNext;
        this.artOnly = artOnly;
        this.openIn = openIn;
    }

    /**
     * The source for a dock tile, or null when it has none. {@code tileOpensYouTube} is the tile's
     * answer to {@link #YOUTUBE_PROBE}: only an app that claims such links can play a rewritten URL,
     * so a tile that does not (the launcher's own, a browser) keeps the publisher's link instead.
     */
    static ShelfSource forTile(String tilePackage, String stremioPackage, boolean tileOpensYouTube, String labelForYou) {
        if (tilePackage == null || tilePackage.isEmpty()) return null;
        if (tilePackage.equals(stremioPackage)) {
            return new ShelfSource(tilePackage, stremioPackage, POSTER_W_DP, POSTER_H_DP, null, false, false, null);
        }
        if (SPOTIFY.equals(tilePackage)) {
            return new ShelfSource(tilePackage, tilePackage, COVER_W_DP, COVER_H_DP, labelForYou, false, true, null);
        }
        if (tileOpensYouTube) {
            return new ShelfSource(tilePackage, YOUTUBE_PUBLISHER, VIDEO_W_DP, VIDEO_H_DP, null, true, true, tilePackage);
        }
        return null;
    }

    /** The link rewrite for this source, or null when cards keep their own deep link. */
    TvShelf.Rewrite rewrite() {
        return openIn == null ? null : TvShelf::youtubeWatchUrl;
    }
}
