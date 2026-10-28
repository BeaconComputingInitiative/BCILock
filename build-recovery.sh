#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# Component: Android Build Recovery v0.1.2
# Developer Seal: CJB-DEV-2026

set -uo pipefail

ROOT="$HOME/BCI/BCILock"
PROJECT="$ROOT/android"
LOG="$ROOT/build-recovery.log"

source "$HOME/.config/bci-java17.sh"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

cd "$PROJECT" || exit 1

echo "BEACON COMPUTING INITIATIVE"
echo "Developer Seal: CJB-DEV-2026"

echo "=== SYSTEM RESOURCES ==="
free -h
df -h "$HOME"

echo "=== GRADLE PROCESSES ==="
ps -eo pid,stat,etime,cmd |
  grep -E '[G]radleDaemon|[g]radle-launcher' || true

echo "=== BUILD START ==="

# Gradle --no-daemon may still use a disposable
# daemon if JVM settings require it.
#
# Timeout prevents an indefinite build hang.
# Record success iff Gradle returns exit code 0.

timeout --signal=INT --kill-after=10s 240s \
  gradle :app:assembleDebug \
    --no-daemon \
    --max-workers=2 \
    --no-parallel \
    --info \
    --stacktrace \
    --console=plain \
    -Dorg.gradle.jvmargs="-Xmx1536m -Dfile.encoding=UTF-8" \
    2>&1 | tee "$LOG"

RESULT=${PIPESTATUS[0]}

echo
echo "=== BUILD EXIT CODE: $RESULT ==="

if [[ "$RESULT" -eq 0 ]]; then
    APK="$PROJECT/app/build/outputs/apk/debug/app-debug.apk"

    if [[ -s "$APK" ]]; then
        echo "[BCI] BUILD SUCCESSFUL"
        sha256sum "$APK" | tee "$APK.sha256"
    else
        echo "[BCI] Gradle succeeded but APK not found"
    fi
elif [[ "$RESULT" -eq 124 || "$RESULT" -eq 137 ]]; then
    echo "[BCI] Build reached timeout."
else
    echo "[BCI] Build failed — inspect log."
fi

echo
echo "=== RECENT GRADLE DAEMON LOG ==="
DAEMON_LOG="$(
    find "$HOME/.gradle/daemon" \
      -name 'daemon-*.out.log' -type f \
      -printf '%T@ %p\n' 2>/dev/null |
    sort -nr | head -1 | cut -d' ' -f2-
)"

if [[ -n "$DAEMON_LOG" ]]; then
    tail -50 "$DAEMON_LOG"
fi

echo
echo "=== BUILD LOG SHA-256 ==="
sha256sum "$LOG" | tee "$LOG.sha256"

exit "$RESULT"
