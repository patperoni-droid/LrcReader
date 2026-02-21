#!/bin/sh

set -eu

HQ_MODE=0

if [ "${1:-}" = "--hq" ]; then
  HQ_MODE=1
elif [ "${1:-}" != "" ]; then
  echo "Usage: ./qa_smoke.sh [--hq]"
  exit 1
fi

step() {
  TITLE="$1"
  shift
  echo
  echo "============================================================"
  echo "$TITLE"
  echo "============================================================"
  "$@"
}

echo "QA Smoke - démarrage"

if [ ! -d ".git" ] || [ ! -f "./gradlew" ]; then
  echo "KO: lance ce script depuis la racine du repo (besoin de .git et ./gradlew)."
  exit 1
fi

step "1/3 Compile Kotlin (LaboDebug)" ./gradlew :app:compileLaboDebugKotlin --console=plain
step "2/3 Assemble APK (LaboDebug)" ./gradlew :app:assembleLaboDebug --console=plain
step "3/3 Assemble APK (ConcertDebug)" ./gradlew :app:assembleConcertDebug --console=plain

if [ "$HQ_MODE" -eq 1 ]; then
  step "4/4 Native HQ build (LaboDebug, enableSoundTouchNative=true)" \
    ./gradlew -PenableSoundTouchNative=true :app:externalNativeBuildLaboDebug --console=plain
else
  echo
  echo "[INFO] Native HQ build non lancé (utilise --hq pour l'activer)."
fi

echo
echo "APK potentiels (si générés):"
L_APK_DIR="app/build/outputs/apk/labo/debug"
C_APK_DIR="app/build/outputs/apk/concert/debug"

if [ -d "$L_APK_DIR" ]; then
  find "$L_APK_DIR" -maxdepth 1 -type f -name "*.apk" | sort || true
else
  echo "- $L_APK_DIR (absent)"
fi

if [ -d "$C_APK_DIR" ]; then
  find "$C_APK_DIR" -maxdepth 1 -type f -name "*.apk" | sort || true
else
  echo "- $C_APK_DIR (absent)"
fi

echo
echo "============================================================"
echo "RÉSUMÉ FINAL: OK"
echo "============================================================"
