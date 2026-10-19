# Sky and weather

Two optional additions to the home screen: a backdrop that follows the clock, and the current
weather in the top-left corner. Both are off in the built-in configuration, so a launcher built
from this repository behaves exactly like before until a theme turns them on.

```json
{
  "sky_intensity": 60,
  "weather_enabled": true,
  "weather_latitude": 48.8566,
  "weather_longitude": 2.3522,
  "weather_place": "Paris"
}
```

Push that as `themes/sunset/config.json` (the `sunset/` folder is the ready-made version) and
restart Home:

```bash
TV_HOST=192.168.1.50 tools/theme.sh themes/sunset
```

## Sky

`sky_intensity` is the opacity of a dark gradient drawn behind everything, over the wallpaper when
there is one. `Sky` interpolates between anchor colours through the day — night blues until dawn,
a soft blue day, then a warm horizon that peaks just before eight in the evening — and the glow
near the bottom edge travels from left to right with the sun. The palette was chosen to stay dark:
the white clock, the greeting, the hero name and the dock band keep their contrast at every hour.

How it renders: the backdrop is one 480×270 bitmap stretched over the screen, redrawn once a
minute and never animated, the same trick the ambient glow already uses. A whole-screen gradient
repainted at 1080p every frame is what made the highlight stutter on weak GPUs; once a minute it
is nothing.

Suggestions: `40` to `60` is a quiet backdrop, `70` to `100` makes the sky the wallpaper. Combine
with a dark wallpaper (the `aurora/` theme already is one) or leave the background black.

## Weather

The line reads `Paris 14° · Nuageux` with an icon that matches the conditions and day or night.
It needs `weather_enabled` and both coordinates:

- `tools/weather.py Grenoble` looks a town up and prints the keys.
  `tools/weather.py --theme themes/sunset Grenoble` writes them into the theme directly.
- `weather_place` is whatever you want shown before the temperature; empty shows only the icon and
  the reading.
- `weather_units` is `celsius` (default) or `fahrenheit`.
- `weather_refresh_minutes` (15 to 720, default 30) is how long a reading stays fresh.

What happens on the TV: while Home is in front, the launcher asks
[open-meteo.com](https://open-meteo.com) for the current temperature, weather code and day/night
flag of the configured coordinates. No API key, no account, no telemetry sent anywhere else. The
launcher does **not** request the location permission: the coordinates come from the theme file,
which is also what keeps the feature predictable on a box without a GPS. One request every half
hour at most, five-second timeouts, and the last reading is cached in SharedPreferences so a box
that is offline (or has the network down) still shows the last value.

Weather data by [Open-Meteo](https://open-meteo.com) (CC BY 4.0). Icons are simple vector shapes
drawn for this launcher.

## Why this is a fork feature

The reference launcher is deliberately offline — `CONTRIBUTING.md` keeps anything with network
access in a fork or the showcase. The sky could go upstream on its own (it is opt-in, local and
free), but the weather line cannot: it stays in this fork, off by default, one small request to one
host, and nothing else in the launcher changes.
