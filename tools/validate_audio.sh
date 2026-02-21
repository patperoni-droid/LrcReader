#!/usr/bin/env bash
set -euo pipefail

echo "== VALIDATION AUDIO / HQ REAL =="

echo "[1/4] assemble LaboDebug"
./gradlew :app:assembleLaboDebug --console=plain

echo "[2/4] externalNativeBuild LaboDebug (HQ REAL must build)"
./gradlew :app:externalNativeBuildLaboDebug --console=plain

echo "[3/4] assemble ConcertDebug"
./gradlew :app:assembleConcertDebug --console=plain

echo "[4/4] externalNativeBuild ConcertDebug"
./gradlew :app:externalNativeBuildConcertDebug --console=plain

echo "✅ OK: Labo+Concert build green (HQ REAL locked)"
