#!/usr/bin/env bash
set -euo pipefail

# Semi-manual test:
# - démarre l'app
# - te laisse faire ton restore backup / lancer un titre
# - vérifie READY puis PLAYING
#
# Usage:
#   ./tools/smoke_player_manual.sh labo
#   ./tools/smoke_player_manual.sh concert

MODE="${1:-labo}"

PKG_LABO="com.patrick.lrcreader.exo.labo"
PKG_CONCERT="com.patrick.lrcreader.exo.concert"

PKG="$PKG_LABO"
if [[ "$MODE" == "concert" ]]; then
  PKG="$PKG_CONCERT"
fi

pick_serial() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    echo "$ANDROID_SERIAL"
    return 0
  fi
  local serials
  serials="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
  if [[ -z "$serials" ]]; then
    echo "ERROR: no adb device online" >&2
    exit 1
  fi
  local physical
  physical="$(echo "$serials" | grep -v '^emulator-' || true)"
  if [[ -n "$physical" ]]; then
    echo "$physical" | head -n 1
  else
    echo "$serials" | head -n 1
  fi
}

SERIAL="$(pick_serial)"
adb_s() { adb -s "$SERIAL" "$@"; }

wait_for_log() {
  local pattern="$1"
  local timeout_secs="$2"
  local start now
  start=$(date +%s)
  while true; do
    if adb_s logcat -d -v brief -s PLAYER_SMOKE | grep -q "$pattern"; then
      return 0
    fi
    now=$(date +%s)
    if (( now - start >= timeout_secs )); then
      return 1
    fi
    sleep 0.5
  done
}

echo "== PLAYER MANUAL SMOKE ($MODE) =="
echo "Device: $SERIAL"
echo "Package: $PKG"

echo "[0/6] Force-stop app"
adb_s shell am force-stop "$PKG" || true
sleep 0.5

echo "[1/6] Clear logcat"
adb_s logcat -c

echo "[2/6] Launch app (monkey)"
adb_s shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true

echo "[3/6] Wait BOOT (30s)"
if ! wait_for_log "BOOT" 30; then
  echo "❌ BOOT not seen" >&2
  adb_s logcat -d -v brief -s PLAYER_SMOKE | tail -n 120 >&2 || true
  exit 1
fi

echo ""
echo "🟡 À TOI : restaure ton backup + lance un morceau dans l'app."
echo "   (Le script attend READY puis PLAYING)"
echo ""

echo "[4/6] Wait READY (120s)"
if ! wait_for_log "READY" 120; then
  echo "❌ READY not seen (tu n'as pas lancé un morceau à temps)" >&2
  adb_s logcat -d -v brief -s PLAYER_SMOKE | tail -n 120 >&2 || true
  exit 1
fi

echo "[5/6] Wait PLAYING (30s)"
if ! wait_for_log "PLAYING" 30; then
  echo "❌ PLAYING not seen" >&2
  adb_s logcat -d -v brief -s PLAYER_SMOKE | tail -n 120 >&2 || true
  exit 1
fi

echo "✅ OK: manual player smoke passed ($MODE)"
exit 0
