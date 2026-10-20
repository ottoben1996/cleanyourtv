# Frame mode

A wall of art instead of a black home screen: after five quiet minutes, or when you press **Mode
Frame** next to Wi-Fi, one of twenty public-domain paintings fills the whole screen in 16:9, with a
small clock laid discreetly over the painting. Any key brings the home back.

This is the fork's take on a Samsung Frame in Art mode — same idea (a painting, not a screensaver),
with what a TV box can actually do. It does not control the panel, read a motion sensor or turn the
TV off; it keeps the screen on and draws the art.

## Opening it

- **The pill**: `Mode Frame` in the top bar, right of Wi-Fi (FR: *Mode Frame*, EN: *Art mode*).
- **By itself**: after `frame_idle_min` minutes without a key or tap on the home. The timer restarts
  on every key; a settings prompt or another app pauses it.
- The screen stays awake while the home is in front and `frame` is on, so the painting is not
  replaced by the system screensaver. Opening another app releases the wake lock.

## The keys

| Key | Action |
| --- | --- |
| Left / Right | Previous or next painting (wraps) |
| OK | Show or hide the clock and the date |
| Back | Return home |
| Any other key | Return home; volume keys still change the volume |

The painting you leave is remembered: the next visit starts with the following one.

## The gallery

Twenty 4K masters in `launcher/app/src/main/assets/art`, all public domain:

- Van Gogh, *La Nuit étoilée* (1889)
- Monet, *Impression, soleil levant* (1872)
- Hokusai, *La Grande Vague de Kanagawa* (1831)
- Vermeer, *La Jeune Fille à la perle* (c. 1665)
- Caillebotte, *Rue de Paris, temps de pluie* (1877)
- Friedrich, *Le Voyageur contemplant une mer de nuages* (1818)
- Constable, *La Charrette de foin* (1821)
- Turner, *Le Dernier Voyage du Téméraire* (1839)
- Bruegel, *Chasseurs dans la neige* (1565)
- Manet, *Un bar aux Folies Bergère* (1882)
- Renoir, *Bal du moulin de la Galette* (1876)
- Monet, *Champ de coquelicots* (1881)
- Van Gogh, *La Chambre à Arles* (1889)
- Van Gogh, *Le Café de nuit* (1888)
- Van Gogh, *Champ de blé avec cyprès* (1889)
- Van Gogh, *Amandier en fleurs* (1890)
- Cézanne, *Mont Sainte-Victoire et le viaduc de l'Arc* (1885–87)
- Hokusai, *Vent clair, matin ensoleillé* (c. 1831)
- Aivazovsky, *La Neuvième Vague* (1850)
- Church, *Le Cœur des Andes* (1859)

The exact source files, museums and licences are in `assets/art/CREDITS.md`. The bundled files are
3840×2160: the Commons scans are centre-cropped to 16:9 and re-encoded at high quality (q84–q92).
The originals are 3.4–13K wide (some are larger still on Commons); they are kept untouched, so a
future 21:9 or 4:3 build can re-crop without losing anything.

The bundled JPEGs carry no ICC profile. The six files whose source had one were re-encoded without
it: the box's decoder tiles some 4K JPEGs that embed a profile. They are sRGB, so the colours are
unchanged.

The box outputs 1080p, so `FrameActivity` decodes each painting with a power-of-two downsample that
keeps at least the panel's height: a 4K file never becomes a 45 MB bitmap, and the painting is still
supersampled when it reaches the screen. Nothing is downloaded and nothing is cached on disk.

On the TV, the painting fills the screen edge to edge: no mat, no bezel, no black bands. A portrait
such as the Vermeer was cropped around the face and the pearl; the Friedrich keeps the figure and the
sea of fog.

## Configuration

`config.xml` (or a theme's `config.json`):

| Key | Default | Meaning |
| --- | --- | --- |
| `frame` | `true` | The pill, the idle timer and the wake lock |
| `frame_idle_min` | `5` | Minutes without a key before the art appears (1 to 60) |
| `frame_night` | `true` | Dim the painting between 22:00 and 08:00 |
| `frame_clock` | `true` | Show the clock and date by default; OK toggles them |

A theme can change them without a rebuild:

```json
{"frame": true, "frame_idle_min": 10, "frame_night": false}
```

## How it works

- `FrameActivity` is a plain fullscreen activity with `FLAG_KEEP_SCREEN_ON`; `MainActivity` starts it
  from the pill or its idle `Runnable` (which every key resets). Back returns to the same home
  instance, the shelves intact.
- `FrameView` draws the painting edge to edge with a centre crop to the screen's ratio, then the
  night dim, a soft scrim along the bottom, the clock and the hint. There is no mat and no frame: the
  art is the screen. The fit maths (aspect ratio, centring) lives in `FrameArt` and is tested on the
  JVM; the cover crop is plain arithmetic in `FrameView.crop`.
- `FrameView` formats the date with the locale's own pattern (`EEEEdMMMM`), so it reads *Mardi
  20 octobre* in French and *Tuesday, October 20* in English. The clock is 24-hour, like the home's.
- The painting is decoded on its own thread; a stale result is recycled instead of replacing a newer
  one. Changing the painting does not allocate more than one bitmap.
- The art is part of the fork only; the upstream launcher stays image-free.

## Limits

- **The panel is 1080p.** 4K files are bundled for quality (and for a future 4K device), but the
  screen cannot show more than 1920×1080 pixels. Decoding 8K on a 2 GB box would cost ~100 MB per
  painting for nothing.
- **No HDR.** The app draws SDR (8-bit sRGB) into a 1080p window; the box does not switch the TV to
  HDR for a launcher, and the scans of these paintings have no HDR data. The 4K files are the
  quality ceiling. A TV picture mode (Vivid, HDR+) can add punch to the HDMI input, but that is the
  panel's processing, not the app's.
- **Night dimming is software.** The box has no control over the TV's backlight, so the painting is
  darkened with a translucent layer from 22:00 to 08:00; the clock keeps its contrast.
- **The home only.** The timer runs while the launcher is in front. Opening Netflix or TizenTube
  stops it; their own screensaver takes over. Covering other apps would need a system overlay
  permission and a global input monitor, which this launcher does not ask for.
- **No motion sensor.** The art stays up until a key is pressed, the screen is turned off or another
  app opens. Use the TV's own sleep timer or the power button if you want it to stop.
- On an OLED panel, a static painting and clock for many hours can leave a mark. The night dimming
  helps; the safest is still to let the TV sleep overnight.

## Troubleshooting

- **The pill is missing.** `frame` is off in the theme; set it to `true`.
- **The art never appears.** The idle timer only runs on the home. Check the value of
  `frame_idle_min` and that you are not in a settings prompt.
- **A painting is cut off.** That is the 16:9 crop: the panel is wider than the canvas, so the top
  and bottom of a 4:3 painting go. The files are untouched; the crop is a display choice.
- **The clock is wrong.** It uses the box's timezone and 24-hour format; fix the box time in
  Settings, the painting reads the same clock.
- **A painting shows repeated vertical strips.** Its JPEG embeds an ICC profile, which this box's
  decoder mishandles at 4K. Re-encode it without the profile (the six files that had one are already
  clean); the image is sRGB, so stripping it costs nothing.
