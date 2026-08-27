#!/usr/bin/env bash
# Runs the same deterministic checks locally in Codespaces and remotely in GitHub Actions.
set -euo pipefail

: "${ANDROID_HOME:?ANDROID_HOME must point to an Android SDK}"
./gradlew :app:testFossDebugUnitTest --no-daemon --max-workers=1 --stacktrace --console=plain
./gradlew :app:assembleFossDebug --no-daemon --max-workers=1 --stacktrace --console=plain

apk="$(find app/build/outputs/apk/foss/debug -type f -name '*foss*debug.apk' -print -quit)"
test -n "$apk"
build_tools="$ANDROID_HOME/build-tools/37.0.0"
"$build_tools/apksigner" verify --verbose "$apk"
"$build_tools/aapt" dump xmltree "$apk" AndroidManifest.xml > manifest.txt
unzip -Z1 "$apk" > entries.txt
unzip -p "$apk" 'classes*.dex' > classes.dex.concat

if grep -aEq 'L(com/google/android/gms|com/google/firebase|androidx/media3/cast)/' classes.dex.concat; then
  echo 'FOSS validation failed: proprietary DEX descriptors detected.' >&2
  exit 1
fi
if grep -Eqi 'com\.google\.android\.gms|com\.google\.firebase|androidx\.media3\.cast' manifest.txt; then
  echo 'FOSS validation failed: proprietary manifest metadata detected.' >&2
  exit 1
fi
if grep -Eq '(^|/)(com/google/android/gms|com/google/firebase|androidx/media3/cast)(/|$)' entries.txt; then
  echo 'FOSS validation failed: proprietary APK paths detected.' >&2
  exit 1
fi

sha256sum "$apk" | tee "${apk}.sha256"
rm -f manifest.txt entries.txt classes.dex.concat
