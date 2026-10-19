#!/usr/bin/env python3
"""Coordinates for the weather line in the top-left corner of the launcher.

Looks a town up with the Open-Meteo geocoding API (no account, no key) and prints the
weather_* keys a theme needs. With --theme, writes them straight into that theme's
config.json, keeping the other keys as they are:

    tools/weather.py Grenoble
    tools/weather.py --theme themes/sunset Grenoble
    TV_HOST=192.168.1.50 tools/theme.sh themes/sunset

Then the launcher reads the config.json on its next start (theme.sh restarts Home).
"""
import argparse
import json
import os
import sys
import urllib.parse
import urllib.request

GEOCODE = "https://geocoding-api.open-meteo.com/v1/search"


def geocode(town, language):
    params = urllib.parse.urlencode({"name": town, "count": 1, "language": language, "format": "json"})
    with urllib.request.urlopen(GEOCODE + "?" + params, timeout=10) as resp:
        data = json.load(resp)
    results = data.get("results") or []
    if not results:
        sys.exit("No place named '%s'. Try the spelling used on a map, or a nearby bigger town." % town)
    hit = results[0]
    place = hit.get("name") or town
    # The corner is tight: show the town alone, not "Grenoble (Auvergne-Rhône-Alpes), France".
    # Edit weather_place in config.json if the name needs disambiguating.
    return hit["latitude"], hit["longitude"], place


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("town", help="town to look up, e.g. Grenoble")
    ap.add_argument("--theme", metavar="DIR",
                    help="write the keys into DIR/config.json (the theme folder)")
    ap.add_argument("--language", default="en", help="geocoder language for the place name (default: en)")
    ap.add_argument("--units", choices=["celsius", "fahrenheit"], default="celsius",
                    help="temperature unit to store (default: celsius)")
    args = ap.parse_args()

    lat, lon, label = geocode(args.town, args.language)
    keys = {
        "weather_enabled": True,
        "weather_latitude": lat,
        "weather_longitude": lon,
        "weather_place": label,
        "weather_units": args.units,
    }
    print(json.dumps(keys, indent=2, ensure_ascii=False))

    if not args.theme:
        print("\nAdd these keys to a theme's config.json, or run again with --theme themes/sunset")
        return

    path = os.path.join(args.theme, "config.json")
    config = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            config = json.load(f)
    config.update(keys)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(config, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("\nWrote %s. Push it:  TV_HOST=<tv ip> tools/theme.sh %s" % (path, args.theme))


if __name__ == "__main__":
    main()
