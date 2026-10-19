# Stremio shelf

Poster rows above the dock, built from what Stremio already publishes to the Android TV home
screen: "Continuer de regarder" and the library channels. No Stremio account, no API, no token —
the shelf reads the TV provider, and it hides itself the moment anything is missing.

```
┌──────────────────────────────────────────────┐
│  ⛅ Good evening        20:14                │
│                                              │
│                                        ▓▓▓▓  │
│  ▓▓▓▓  ▓▓▓▓  ▓▓▓▓  ▓▓▓▓  ▓▓▓▓  ▓▓▓▓   ▓▓▓▓  │  ← shelf, only while Stremio's tile is focused
│  ▓▓▓▓  ▓▓▓▓  ▓▓▓▓  ▓▓▓▓  ▓▓▓▓  ▓▓▓▓   ▓▓▓▓  │
│                                              │
│                [ dock: Stremio ]             │
└──────────────────────────────────────────────┘
```

## Why this is a fork feature

`CONTRIBUTING.md` of the reference launcher keeps "recommendation rows and content feeds" out of
upstream — Home is a launcher, not a browse surface. The shelf therefore lives in this fork. It
ships enabled by default here (`stremio_shelf` is `true` in `config.xml`), and it never shows
anything but what the TV provider already contained.

## How it works

Stremio's Android TV build registers a worker that refreshes its channels every 20 minutes and on
boot. Each channel ("Continuer de regarder", "Bibliothèque – Séries", …) carries preview programs:
title, poster, duration, playback position and a `stremio:///detail/…` deep link. The launcher:

1. asks the provider for the channels published by one package (`stremio_shelf_package`), filters
   client-side — the provider refuses a selection on rows another app owns — and sorts the browsable
   channel first, then by id, which is the order Stremio published them in;
2. asks for the preview programs, keeps the channels' rows, skips empty ones and cuts them to
   `stremio_shelf_rows` and `stremio_shelf_max_items` — rows that would run into the clock and the
   greeting are dropped rather than covering them (one 96×144 dp row fits above the dock on a
   1080p TV, two fit on a 720p one);
3. draws one poster row per channel. Each card shows the poster, the title underneath, an `S07E09`
   badge when the episode link carries an IMDb-shaped id, and a thin progress bar from Stremio's
   playback position;
4. opens the deep link on click, so Stremio resumes exactly where its own row would.

The read happens on a background thread on every resume of Home (a few hundred rows at most) and
is dropped if the permission is missing or Stremio is not installed.

## Permission

Reading another app's listings needs `READ_TV_LISTINGS`. Most TV builds grant it at install; some
Skyworth/Strong builds (the LEAP-S1 on Android 14 is one) make it a **runtime permission**, denied
by default. There the first Home start asks once, with the standard Allow/Deny dialog:

- Allow: the shelf appears.
- Deny: a toast says it stays hidden, and Home never asks again — no nagging.

On a box where the dialog cannot appear (no on-screen permission dialog), grant it over adb:

```bash
adb shell pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS
```

## Postering

Posters are `https://` URLs from Stremio's metadata providers (image.tmdb.org, metahub…), so the
launcher downloads them itself through a three-line loader (no image library):

- one background thread at low priority, requests only run while the shelf is on screen;
- an 8 MB bitmap cache in memory and a 40 MB cache in `cacheDir/shelf-posters`, keyed by a SHA-1 of
  the URL, oldest files dropped when it overflows;
- decoding is downsampled to roughly what the screen shows (96×144 dp cards) in RGB_565;
- anything that fails — offline, dead link, huge file, bad bitmap — leaves the coloured placeholder
  card with the title's first letter, never a spinner or a crash.

`stremio_shelf_posters: false` skips all of that and keeps the placeholders.

## Keys

| Key | Type | Meaning |
|---|---|---|
| `stremio_shelf` | bool | Show the shelf at all. `true` in this fork's built-in config, `false` hides it for good |
| `stremio_shelf_posters` | bool | Download posters (`true`, default) instead of coloured placeholders |
| `stremio_shelf_rows` | 1 to 3 | How many channel rows (default 1, "Continuer de regarder" only; rows that would cover the clock are dropped) |
| `stremio_shelf_max_items` | 1 to 20 | Most posters per row (default 12) |
| `stremio_shelf_package` | string | The app whose channels are read (default `com.stremio.one`) |

```json
{
  "stremio_shelf": true,
  "stremio_shelf_rows": 2,
  "stremio_shelf_max_items": 10
}
```

## Focus and the D-pad

The shelf only appears while the packaged app's own dock tile has focus; the app name above the
dock gives way to the posters. Up from the dock tile enters the shelf, Down from a poster returns to
that tile, Left and Right move along a row without wrapping at the ends, and Up from the first row
goes to the top bar. With the shelf hidden, Up and Down behave exactly as before.

## Privacy

Nothing leaves the box except the poster downloads, and only while the shelf is visible (or the app
is on the dock tile with rows already built). The provider read is local. There is no Stremio login,
no token, no analytics, and no request to Stremio's API — the launcher reads what the app already
wrote for the TV's own home screen.

## Troubleshooting

- **No shelf, no error.** Stremio only publishes after its worker runs: open Stremio, browse a bit,
  then leave it — the channels appear within a few minutes (they survive until removed). Home picks
  them up on the next resume.
- **Check what Home will read:**
  ```bash
  adb shell content query --uri content://android.media.tv/channel --projection _id,display_name,package_name,browsable
  adb shell content query --uri content://android.media.tv/preview_program --projection title,channel_id
  ```
- **Permission:** `adb shell dumpsys package com.gathin.tvhome | grep READ_TV_LISTINGS` shows
  `granted=true/false`; grant it with the `pm grant` line above.
- **A row is missing.** Rows with no programs are skipped, and `stremio_shelf_rows` caps how many
  show; raise it to 2 or 3 to see the library channels too.
