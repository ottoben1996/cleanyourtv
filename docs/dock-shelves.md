# Dock shelves

One content row above the dock per dock tile, built from what the apps themselves publish to the
Android TV home screen: Stremio's "Continuer de regarder", Spotify's covers, and the YouTube app's
recommendations for a tile that plays youtube.com links (TizenTube). No account, no API, no token:
the launcher reads the TV provider, and a tile with nothing to show (GoldenOTT) keeps its plain app
name.

```
┌───────────────────────────────────────────────────────┐
│  ⛅ Good evening        20:14                         │
│                                                       │
│  POUR VOUS                                            │
│  ▓▓▓  ▓▓▓  ▓▓▓  ▓▓▓  ▓▓▓  ▓▓▓  ▓▓▓  ▓▓▓      ▓▓▓    │  ← only while a shelf tile has focus
│                                                       │
│                 [ dock: GoldenOTT · Stremio · … ]     │
└───────────────────────────────────────────────────────┘
```

## What each tile shows

| Dock tile | Rows read from | Cards | Row label |
|---|---|---|---|
| Stremio (`stremio_shelf_package`) | Stremio's channels | 2:3 posters, 96×144 dp | the channel name ("Continuer de regarder") |
| Spotify | Spotify's own channel | square covers, 120×120 dp | "Pour vous" (the channel itself is called "Spotify") |
| TizenTube, or any tile that claims youtube.com links | the Google YouTube app's channels and watch-next list | 16:9 thumbnails, 176×99 dp | "Pour vous": watch-next first, then the recommendations |
| GoldenOTT | nothing | — | no shelf, the app name stays |

Two findings from a TvProvider probe on the LEAP-S1 (2026-10-20) shape this design:

- **GoldenOTT publishes nothing**: no channels, no preview programs, no watch-next rows, no deep
  links (its manifest only has `MAIN`). There is no data to read and nothing to open but the app.
- **TizenTube publishes nothing either**, but the Google YouTube app installed next to it publishes
  "Recommandations", "Abonnements", "Tendances" and a watch-next list. TizenTube declares
  `android.intent.action.VIEW` for `https://youtube.com`, `www.youtube.com` and `youtu.be`, so those
  cards are pointed at it: the video plays in the ad-free client rather than in the publisher.

## Why this is a fork feature

`CONTRIBUTING.md` of the reference launcher keeps "recommendation rows and content feeds" out of
upstream — Home is a launcher, not a browse surface. The shelves therefore live in this fork. They
ship enabled by default here (`stremio_shelf` is `true` in `config.xml`), and they never show
anything but what the TV provider already contained.

## How it works

On every resume, Home:

1. asks the provider for the channels published by each tile's app (`stremio_shelf_package`, Spotify,
   the YouTube app), filters client-side — the provider refuses a selection on rows another app owns —
   and sorts the browsable channel first, then by id, which is the order the apps published them in;
2. reads the channels' preview programs: title, artwork, duration, playback position, deep link;
3. for the YouTube tile, also reads the `watch_next_program` rows the same app publishes ("what you
   were watching"). They are **merged into the first channel row**, watched items first, duplicates
   by link dropped: on a 1080p TV the band fits a single row, so stacking "Continue watching" above
   the recommendations would hide the recommendations instead of adding to them;
4. cuts rows to `stremio_shelf_rows` and cards to `stremio_shelf_max_items`. Rows that would run into
   the clock and the greeting are hidden rather than covering them (one row fits above the dock on a
   1080p TV, two on a 720p one);
5. draws one row per tile, labelled with the channel name in small caps, or a source label when the
   app's own name would be redundant ("Pour vous" for Spotify and YouTube). Each card shows the
   artwork, the title underneath, an `S07E09` badge when a Stremio episode link carries an
   IMDb-shaped id, and a thin inset progress bar from the app's playback position;
6. opens the card's own deep link on click (Stremio resumes exactly where its row would, Spotify
   starts the track), except for YouTube: the watch URL is extracted from the publisher's link and
   rebuilt as `https://www.youtube.com/watch?v=…` before being pointed at the tile's package. If the
   tile cannot claim that link, the publisher's own link is used instead.

Spotify and YouTube cards without an `https://` artwork are dropped: Spotify's first card is a promo
banner shipped as an app resource (a vector, not a title), and a placeholder letter in a row of
covers reads as a broken image.

The read happens on a background thread on every resume of Home (a few hundred rows at most) and is
dropped if the permission is missing or an app is not installed.

## Permission

Reading another app's listings needs `READ_TV_LISTINGS`. Most TV builds grant it at install; some
Skyworth/Strong builds (the LEAP-S1 on Android 14 is one) make it a **runtime permission**, denied by
default. There the first Home start asks once, with the standard Allow/Deny dialog:

- Allow: the shelves appear.
- Deny: a toast says they stay hidden, and Home never asks again — no nagging.

On a box where the dialog cannot appear (no on-screen permission dialog), grant it over adb:

```bash
adb shell pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS
```

## Artwork

Artwork is `https://` only (image.tmdb.org and metahub for Stremio, i.scdn.co for Spotify,
i.ytimg.com for YouTube), so the launcher downloads it itself through a small loader (no image
library):

- one background thread at low priority, requests only run while a shelf is on screen;
- an 8 MB bitmap cache in memory and a 40 MB cache in `cacheDir/shelf-posters`, keyed by a SHA-1 of
  the URL, oldest files dropped when it overflows;
- decoding is downsampled to roughly what the screen shows (96×144, 120×120 or 176×99 dp cards) in
  RGB_565;
- anything that fails — offline, dead link, huge file, bad bitmap — leaves the coloured placeholder
  card with the title's first letter, never a spinner or a crash.

`stremio_shelf_posters: false` skips all of that and keeps the placeholders.

## Keys

| Key | Type | Meaning |
|---|---|---|
| `stremio_shelf` | bool | Show the shelves at all. `true` in this fork's built-in config, `false` hides them for good |
| `stremio_shelf_posters` | bool | Download artwork (`true`, default) instead of coloured placeholders |
| `stremio_shelf_rows` | 1 to 3 | How many rows to build per tile (default 1, the first channel row or the merged YouTube row; rows that would cover the clock are hidden) |
| `stremio_shelf_max_items` | 1 to 20 | Most cards per row (default 12) |
| `stremio_shelf_package` | string | The dock app whose cards follow their own deep links and use 2:3 posters (default `com.stremio.one`) |

```json
{
  "stremio_shelf": true,
  "stremio_shelf_rows": 2,
  "stremio_shelf_max_items": 10
}
```

## Focus and the D-pad

A shelf only appears while its own dock tile has focus; the app name above the dock gives way to the
cards. Up from the dock tile enters the shelf, Down from a card returns to that tile, Left and Right
move along a row without wrapping at the ends, and Up from the first row goes to the top bar. With a
shelf hidden, Up and Down behave exactly as before.

## Privacy

Nothing leaves the box except the artwork downloads, and only while a shelf is visible (or its tile
is focused with rows already built). The provider read is local. There is no account, no token, no
analytics, and no request to Stremio's, Spotify's or YouTube's APIs — the launcher reads what the
apps already wrote for the TV's own home screen, and opens their links when a card is picked.

## Troubleshooting

- **No shelf, no error.** The apps publish on their own schedule: Stremio refreshes its channels every
  20 minutes and on boot, Spotify and YouTube update when they run. Open the app once, browse a bit,
  leave it — Home picks the rows up on the next resume.
- **GoldenOTT has no shelf.** By design: it publishes no channels, no programs and no watch next, so
  there is nothing to read.
- **Check what Home will read:**
  ```bash
  adb shell content query --uri content://android.media.tv/channel --projection _id,display_name,package_name,browsable
  adb shell content query --uri content://android.media.tv/preview_program --projection title,channel_id
  ```
  The provider refuses a selection on rows Home does not own, so query everything and filter by
  `package_name` / `channel_id`.
- **YouTube cards open in the wrong app.** The tile must declare `VIEW` for `youtube.com` links:
  ```bash
  adb shell dumpsys package io.gh.reisxd.tizentube.cobalt | grep -A6 'youtube.com'
  ```
  Without it, Home falls back to the publisher's link (the Google YouTube app).
- **Permission:** `adb shell dumpsys package com.gathin.tvhome | grep READ_TV_LISTINGS` shows
  `granted=true/false`; grant it with the `pm grant` line above.
- **A row is missing.** Rows with no programs are skipped, and `stremio_shelf_rows` caps how many are
  built per tile; raise it to 2 or 3 to see the library channels too. YouTube rows need the Google
  YouTube app installed: TizenTube itself publishes nothing.
