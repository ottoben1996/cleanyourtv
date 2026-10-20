# Windows version of football.sh: store the football-data.org token on the TV and restart Home so the
# football row picks it up. The token is kept in the launcher's external files directory, never in
# this repository. See docs/football.md.
#
# Usage: tools\football.ps1 -TvHost 192.168.1.50 <token>
#        tools\football.ps1 -TvHost 192.168.1.50 -Clear
param(
    [Parameter(Position = 0)][string]$Token,
    [switch]$Clear,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
Set-Location $PSScriptRoot
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'com.gathin.tvhome'
$dir = "/sdcard/Android/data/$pkg/files"

if ($Clear) {
    & $py -I tv.py "rm -f $dir/football-token.txt; am force-stop $pkg; am start -n $pkg/.MainActivity >/dev/null; echo cleared"
    exit 0
}
if (-not $Token) { throw 'usage: football.ps1 <token> | -Clear' }
if ($Token -notmatch '^[A-Za-z0-9._-]+$') { throw 'unexpected character in the token' }
& $py -I tv.py "mkdir -p $dir; echo '$Token' > $dir/football-token.txt; am force-stop $pkg; am start -n $pkg/.MainActivity >/dev/null; echo saved"
Write-Host 'Token saved. The football row comes back with the next Home start.'
