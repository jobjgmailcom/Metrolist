#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_HOME:?ANDROID_HOME must be configured by the development container}"
test -x "$ANDROID_HOME/build-tools/37.0.0/aapt"
test -x "$ANDROID_HOME/build-tools/37.0.0/apksigner"
test -d "$ANDROID_HOME/platforms/android-37.0"
java --version
./gradlew --version --no-daemon
printf '%s\n' 'Toolchain ready. Run: ./scripts/verify-foss.sh'
