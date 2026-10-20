# Now playing

One card in the band above the dock: the track or video the focused app is playing, its artwork, and
the ambient glow tinted with the artwork's colour. It works with every app that publishes a media
session — on the LEAP-S1 that means Spotify and TizenTube.

```
┌───────────────────────────────────────────────────────┐
│  ⛅ Orange 16° · Pluie                   [ Wi-Fi ] [⚙] │
│                                                       │
│  ███   PEACE & LOVE                                   │  ← cover, title, artist, progress
│  ███   TIF                                            │
│  ███   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░                       │
│                                                       │
│                 [ dock: GoldenOTT · Stremio · … ]     │
└───────────────────────────────────────────────────────┘
```

## What it shows

- **The session that matters**: playing first, then buffering, then paused; sessions without a title
  are ignored, and the Bluetooth stack's placeholder sessions (state `ERROR`, no metadata) are
  skipped.
- **Where the card goes**: it takes the band when the focused tile is the app that is playing (so the
  Spotify tile shows the current track instead of its "Pour vous" row), and on any tile without a
  shelf (GoldenOTT). A tile with a shelf keeps its shelf — the card comes back when you focus the
  playing app or a tile with no rows.
- **The glow** takes the dominant colour of the artwork whenever a session is shown, on every tile:
  Stremio's posters keep the band, but the room turns the colour of the cover.
- **No artwork** (an app that publishes no bitmap and no readable URI): the card keeps a note
  placeholder and the glow keeps the focused app's colour.
- **No duration** (live TV, a video ad): the progress bar is hidden; the title stays.
- The card is display only: OK still opens the tile's app, exactly as before.

## Permission

Android only lets an app list other apps' media sessions when it has an **enabled notification
listener**. `PlaybackListener` is an empty service declared for exactly that purpose: it receives the
system's notification callbacks but overrides nothing and reads no notification. Only the media
session's title, artist, artwork and position are used.

The first Home start after install shows a toast and opens **Settings › Accès aux notifications**
(Apps › Special app access › Notification access), where **Accueil / Home** is toggled on. Refusing,
or a TV without that screen, simply leaves the card off — Home never asks again.

Over adb:

```bash
adb shell cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.PlaybackListener
adb shell cmd notification disallow_listener com.gathin.tvhome/com.gathin.tvhome.PlaybackListener
```

## How it works

- On every Home resume, `NowPlaying` asks `MediaSessionManager` for the active sessions (the listener
  component is the key), picks the best one, registers an `OnActiveSessionsChangedListener` and a
  `MediaController.Callback` for metadata and state changes, and drops everything on pause.
- The progress bar is computed from the session's own clock (`PlaybackState.getPosition()` plus the
  elapsed time times the speed) once a second, so it stays right without asking the app.
- The artwork comes from the metadata bitmap when the app puts one there, otherwise from its
  `ART_URI` / `ALBUM_ART_URI` through the content resolver. It is scaled to 256 px, the size of the
  card, and refreshed only when the track changes.
- The colour is the average of a 24×24 downsample, dark padding ignored and saturated pixels weighted
  more, then lifted (saturation at least 0.35, value at least 0.45) so it still reads on a dark TV.
- Nothing is downloaded: the artwork either travels in the session or is served by the app's own
  content provider.

## Keys

| Key | Type | Meaning |
|---|---|---|
| `now_playing` | bool | Show the now-playing card and tint the glow (`true` in this fork's built-in config; `false` turns both off) |

```json
{
  "now_playing": false
}
```

## Privacy

The notification listener is a technical requirement of the Android media-session API, not a way to
read notifications: no notification content is parsed, stored or sent anywhere. The card is local,
the artwork is local, and the launcher's only network use remains the weather and the shelf
artwork.

## Troubleshooting

- **No card.** Check the grant:
  ```bash
  adb shell settings get secure enabled_notification_listeners
  ```
  It must contain `com.gathin.tvhome/com.gathin.tvhome.PlaybackListener`; use the `allow_listener`
  line above if it does not.
- **The card is on the wrong tile.** It replaces the shelf of the app that is playing; focus another
  shelf tile (Stremio) to see its rows.
- **No artwork or colour.** The app published no bitmap and no URI the resolver can read (some
  players only set a notification icon). The note placeholder and the app's glow are the fallback.
- **No progress bar.** The session reports no duration; live streams and some video players do that.
- **What the system sees:**
  ```bash
  adb shell dumpsys media_session | grep -E "package=|state=|description="
  ```
