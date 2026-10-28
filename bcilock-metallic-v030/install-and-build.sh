#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# BCILock Metallic UI Installer v0.3.0
# Developer Seal: CJB-DEV-2026
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT="${1:-$HOME/BCI/BCILock/android}"
MAIN="$PROJECT/app/src/main/java/org/beaconcomputing/bcilock/MainActivity.kt"
ASSETS="$PROJECT/app/src/main/assets"
test -f "$PROJECT/settings.gradle.kts" || { echo '[BCI] Missing Android project'; exit 1; }
test -f "$PROJECT/app/build.gradle.kts" || { echo '[BCI] Missing Android module'; exit 1; }
mkdir -p "$(dirname "$MAIN")" "$ASSETS/branding" "$PROJECT/backup"
if [[ -f "$MAIN" ]]; then cp "$MAIN" "$PROJECT/backup/MainActivity-pre-v030-$(date +%Y%m%d%H%M%S).kt.bak"; fi
cp "$HERE/app/src/main/java/org/beaconcomputing/bcilock/MainActivity.kt" "$MAIN"
cp "$HERE/app/src/main/assets/branding/bcilock_eye_lighthouse.png" "$ASSETS/branding/bcilock_eye_lighthouse.png"
[[ ! -f "$HOME/.config/bci-java17.sh" ]] || source "$HOME/.config/bci-java17.sh"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
cd "$PROJECT"
sha256sum "$MAIN" "$ASSETS/branding/bcilock_eye_lighthouse.png" > GUI_v030_SHA256SUMS
echo '[BCI] Developer Seal: CJB-DEV-2026'
echo '[BCI] Building native metallic GUI; Android auth remains disabled.'
if [[ -x ./gradlew ]]; then ./gradlew :app:assembleDebug --no-daemon --max-workers=2 --console=plain; else gradle :app:assembleDebug --no-daemon --max-workers=2 --console=plain; fi
APK="$PROJECT/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
sha256sum "$APK" | tee "$APK.sha256"
echo "[BCI] Built APK: $APK"
