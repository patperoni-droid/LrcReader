#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "== BEFORE PUSH CHECKS =="

echo "[1/3] validate_audio (HQ REAL + builds)"
./tools/validate_audio.sh

echo "[2/3] unit tests (fast)"
./gradlew :app:testLaboDebugUnitTest :app:testConcertDebugUnitTest --console=plain

echo "[3/3] lint (basic)"
./gradlew :app:lintLaboDebug :app:lintConcertDebug --console=plain

echo "OK: safe to push"
