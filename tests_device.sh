#!/usr/bin/env bash
set -euo pipefail

echo "[SPL] Running instrumented tests on connected device(s)..."
./gradlew :app:connectedAndroidTest

echo "[SPL] DEVICE TESTS PASSED"
