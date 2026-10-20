#!/usr/bin/env bash
# Store the football-data.org token on the TV (football-token.txt in the launcher's external files
# directory) and restart Home so the football row picks it up. The token never enters this
# repository; see docs/football.md.
#
# Usage: TV_HOST=192.168.1.50 tools/football.sh <token>
#        TV_HOST=192.168.1.50 tools/football.sh --clear
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 <token>}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.tvhome
DIR=/sdcard/Android/data/$PKG/files
tv() { "$PY" -I tv.py "$@"; }

if [ "${1:-}" = --clear ]; then
  tv "rm -f $DIR/football-token.txt; am force-stop $PKG; am start -n $PKG/.MainActivity >/dev/null; echo cleared" | tail -n 1
  exit 0
fi
TOKEN="${1:?usage: football.sh <token> | --clear}"
# One line, nothing a shell could interpret: a football-data token is hex digits.
case "$TOKEN" in *[!A-Za-z0-9._-]*) echo "unexpected character in the token" >&2; exit 1 ;; esac
tv "mkdir -p $DIR; echo '$TOKEN' > $DIR/football-token.txt; am force-stop $PKG; am start -n $PKG/.MainActivity >/dev/null; echo saved" | tail -n 1
