#!/usr/bin/env bash
set -euo pipefail

echo "[SPL] Running Labo unit tests..."
./gradlew :app:testLaboDebugUnitTest

echo "[SPL] Running Concert unit tests..."
./gradlew :app:testConcertDebugUnitTest

echo "[SPL] Building debug APKs..."
./gradlew assembleDebug

echo "[SPL] ALL TESTS PASSED"
