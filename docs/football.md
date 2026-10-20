# Football row

A row of match cards on the football tile: tonight's kick-offs, the live minute and the score, the
channels they are on. One tap opens the football app — the streams live there, the launcher only
tells you what is on.

```
┌─────────────────────────────────────────────────────────────┐
│  CE SOIR FOOT                                               │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────┐ │
│  │ Ligue 1 · Canal+ │ │ Premier League   │ │ Liga · beIN  │ │
│  │            ● 67' │ │         20:00    │ │  Demain 21:00│ │
│  │ Paris SG      2  │ │ Arsenal          │ │ Real Madrid  │ │
│  │ Marseille     1  │ │ Liverpool        │ │ Barcelona    │ │
│  └──────────────────┘ └──────────────────┘ └──────────────┘ │
│                 [ dock: GoldenOTT · Stremio · … ]           │
└─────────────────────────────────────────────────────────────┘
```

## Setup

The data comes from [football-data.org](https://www.football-data.org/client/register): a free
account, one token, ten requests a minute. The launcher asks for at most one request a minute while
the row is on screen.

Keep the token out of the repository: put it in `football-token.txt` in the app's external files
directory, one line, nothing else.

```bash
adb push football-token.txt /sdcard/Android/data/com.gathin.tvhome/files/football-token.txt
```

A `football_token` key in `config.json` also works (it wins over the file), but then a theme or a
configuration that gets shared carries the token with it.

## Keys

| Key | Type | Default | Meaning |
|---|---|---|---|
| `football` | bool | `true` | Show the row at all |
| `football_token` | string | *(empty)* | football-data.org token; empty falls back to `football-token.txt` |
| `football_competitions` | string | `FL1,CL,PL,PD` | API competition codes, comma separated: every match of these is a candidate |
| `football_teams` | string | `PD:Real Madrid,FC Barcelona` | Per-competition club filter: inside the named competition, only the listed clubs (see below) |
| `football_channels` | string | `FL1=Canal+\|CL=beIN Sports 1\|PL=Canal+\|PD=beIN Sports 1` | What the card shows after the competition; display only |
| `football_tile` | string | `com.new_Golden_ott_user.app` | The dock tile that carries the row |

All of them can be set in `themes/<name>/config.json`; `football-token.txt` is not touched by the
theme tools, so a theme push never erases it.

```json
{
  "football_competitions": "FL1,CL,PL,PD",
  "football_teams": "PD:Real Madrid,FC Barcelona",
  "football_channels": "FL1=Canal+|CL=beIN Sports 1|PL=Canal+|PD=beIN Sports 1"
}
```

### Competitions and clubs

`football_competitions` takes the API's own codes: `FL1` (Ligue 1), `CL` (Champions League), `PL`
(Premier League), `PD` (La Liga), `EL` (Europa League), `BL1`, `SA`, `DED`, `PPL`, `BSA`… all of the
free tier. Every match of a listed competition is kept.

`football_teams` adds a rule per competition, `CODE:club one,club two`, separated by `;`. Inside that
competition, only matches where one of the clubs plays are kept. The default follows Real Madrid and
Barcelona in La Liga, while every Ligue 1, Champions League and Premier League match is shown — so a
Clásico or a Barça Champions League night is in the row, but Getafe–Girona is not.

The names are compared without case or accents, and a club name is enough: `Real Madrid` matches
`Real Madrid CF`, `Barcelona` matches `FC Barcelona`.

### Channels

The channel shown after the competition is decorative: there is no per-channel deep link on the
LEAP-S1, and the football apps do not publish their channels to the TV provider. Picking a card opens
`football_tile` (GoldenOTT by default), where the streams are.

## What the row keeps

- **Live now**: `IN_PLAY` and `PAUSED` matches first; the card shows the minute (or `Mi-temps`).
- **Coming**: everything that has not started, by kick-off, today and tomorrow. Tomorrow's matches
  are labelled `Demain 21:00`.
- **Results**: finished matches, the most recent first, kept for four hours after kick-off.
- A match postponed more than three hours past its slot is dropped.
- At most `stremio_shelf_max_items` cards (12 by default), and the row follows the shelf's own
  switches: it needs `stremio_shelf` on, and lives in the band above the dock.

The status line also understands `POSTPONED`, `SUSPENDED` and `CANCELLED`. Kick-off times are in the
box's timezone.

## How it works

- On resume, the shelf thread loads the TV rows and the football matches together; a request less
  than a minute after the previous one is skipped. `FootBall.fetch` returns null on any failure
  (offline, invalid token, rate limit) and the previous list stays on screen; an empty list is a
  successful "no match" and hides the row.
- While the football tile is focused, a one-minute ticker refetches the scores and rebuilds the row
  (not while a card has focus, so the highlight is not lost).
- The row is not a TV channel: `FootBall` parses the API answer into `Match` objects, `FootAdapter`
  draws the cards, and nothing is cached on disk. Plain Java, tested on the JVM in
  `FootBallTest`.
- `docs/dock-shelves.md` covers the shelf mechanics the row uses; `docs/now-playing.md` covers the
  band when something is playing.

## Privacy

One HTTPS request to `api.football-data.org`, carrying the owner's token and a date window, at most
once a minute. No account, no analytics, no other endpoint. The token is only read from
`football-token.txt` or `config.json` and is never written anywhere else.

## Troubleshooting

- **No row.** Check the token file and the tile:
  ```bash
  adb shell cat /sdcard/Android/data/com.gathin.tvhome/files/football-token.txt
  adb shell dumpsys package com.new_Golden_ott_user.app | head -n 3
  ```
  The row needs `football_tile` to be one of the dock apps, `stremio_shelf` on, and at least one
  match in the window above.
- **Check the API by hand** (from the PC):
  ```bash
  curl -s -H "X-Auth-Token: $TOKEN" \
    "https://api.football-data.org/v4/matches?competitions=FL1,CL,PL,PD&dateFrom=2026-10-20&dateTo=2026-10-21"
  ```
  `403` means the token is wrong or the competition is not in the plan, `429` means more than ten
  requests a minute, `200` with `"count": 0` means simply no match in that window.
- **An empty token is not an error to the API**: it answers `200` with no match. The launcher never
  calls it without a token, so an absent row means an absent token, not an empty answer.
- **Wrong channel.** Edit `football_channels`; the codes are the competition codes.
- **A club is missing.** Add it to the rule for its competition (`PD:Real Madrid,FC Barcelona`), or
  remove the rule for that competition to show every match.
