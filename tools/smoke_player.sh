#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   ./tools/smoke_player.sh labo
#   ./tools/smoke_player.sh concert
#
# Optional:
#   ANDROID_SERIAL=XXXX ./tools/smoke_player.sh labo

MODE="${1:-labo}"

PKG_LABO="com.patrick.lrcreader.exo.labo"
PKG_CONCERT="com.patrick.lrcreader.exo.concert"

PKG="$PKG_LABO"
if [[ "$MODE" == "concert" ]]; then
  PKG="$PKG_CONCERT"
fi

# -------------------------
# Pick device
# -------------------------
pick_serial() {
  # If user already exported ANDROID_SERIAL, use it
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    echo "$ANDROID_SERIAL"
    return 0
  fi

  # Otherwise: pick first *physical* device (not emulator) if present, else first device.
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

# -------------------------
# timeout (macOS proof)
# -------------------------
run_with_timeout() {
  # $1 = seconds, rest = command...
  local secs="$1"; shift

  # If gtimeout exists (coreutils), use it. If timeout exists, use it. Otherwise fallback to perl alarm.
  if command -v gtimeout >/dev/null 2>&1; then
    gtimeout "${secs}" "$@"
    return $?
  fi
  if command -v timeout >/dev/null 2>&1; then
    timeout "${secs}" "$@"
    return $?
  fi

  perl -e '
    my $secs = shift @ARGV;
    $SIG{ALRM} = sub { exit 124 };
    alarm($secs);
    exec @ARGV;
    exit 1;
  ' "$secs" "$@"
}

echo "== PLAYER SMOKE ($MODE) =="
echo "Device: $SERIAL"
echo "Package: $PKG"


echo "[0/6] Force-stop app"
adb_s shell am force-stop "$PKG" || true
sleep 1

echo "[1/6] Clear logcat"
adb_s logcat -c

echo "[2/6] Launch app (monkey)"
adb_s shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true

wait_for_log() {
  local pattern="$1"
  local timeout_secs="$2"
  local start
  local now
  start=$(date +%s)

  while true; do
    if adb_s logcat -d -v brief -s PLAYER_SMOKE | grep -Fq "$pattern"; then
      return 0
    fi

    now=$(date +%s)
    if (( now - start >= timeout_secs )); then
      return 1
    fi

    sleep 0.5
  done
}
echo "[3/6] Wait BOOT (30s)"
if ! wait_for_log "BOOT" 30; then
  echo "❌ BOOT not seen" >&2
  adb_s logcat -d -v brief -s PLAYER_SMOKE | tail -n 80 >&2 || true
  exit 1
fi

echo "[4/6] Send play keyevents (85 then 126)"
adb_s shell input keyevent 85
sleep 0.5
adb_s shell input keyevent 126
sleep 1.0

echo "[5/6] Wait READY (30s)"
if ! wait_for_log "READY" 30; then
  echo "❌ READY not seen" >&2
  adb_s logcat -d -v brief -s PLAYER_SMOKE | tail -n 80 >&2 || true
  exit 1
fi

echo "[6/6] Wait PLAYING (30s)"
if ! wait_for_log "PLAYING" 30; then
  echo "❌ PLAYING not seen" >&2
  adb_s logcat -d -v brief -s PLAYER_SMOKE | tail -n 80 >&2 || true
  exit 1
fi
echo "✅ OK: player smoke passed ($MODE)"
exit 0
