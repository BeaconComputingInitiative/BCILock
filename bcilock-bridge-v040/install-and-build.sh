#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# Component: BCILock Android BLE Bridge Installer v0.4.0
# Developer Seal: CJB-DEV-2026
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
PROJECT="${1:-$HOME/BCI/BCILock/android}"
TARGET="$PROJECT/app/src/main/java/org/beaconcomputing/bcilock"
MANIFEST="$PROJECT/app/src/main/AndroidManifest.xml"
[[ -f "$PROJECT/settings.gradle.kts" && -f "$MANIFEST" ]] || { echo '[BCI] Android project not found'; exit 1; }
mkdir -p "$TARGET" "$PROJECT/bci-backups"
cp "$MANIFEST" "$PROJECT/bci-backups/AndroidManifest.before-v040.xml"
cp "$SCRIPT_DIR/android/BciBridgeActivity.kt" "$TARGET/BciBridgeActivity.kt"
python3 - "$MANIFEST" <<'PY'
import sys
from pathlib import Path
p=Path(sys.argv[1]); src=p.read_text()
if 'android:name=".BciBridgeActivity"' in src:
    print('[BCI] Manifest already patched'); raise SystemExit(0)
perms='''    <!-- BCI BLE permissions: legacy + Android 12+ -->
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
'''
activity='''        <!-- Separate launcher keeps the existing metallic dashboard intact. -->
        <activity android:name=".BciBridgeActivity" android:exported="true" android:label="BCILock Auth">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
'''
assert '</manifest>' in src and '</application>' in src
src=src.replace('<application',perms+'    <application',1)
src=src.replace('</application>',activity+'    </application>',1)
p.write_text(src)
PY
sha256sum "$TARGET/BciBridgeActivity.kt" "$MANIFEST" > "$PROJECT/BRIDGE_v040_SHA256SUMS"
[[ -f "$HOME/.config/bci-java17.sh" ]] && source "$HOME/.config/bci-java17.sh"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
cd "$PROJECT"
echo '[BCI] Building BLE authentication companion APK…'
if [[ -x ./gradlew ]]; then ./gradlew :app:assembleDebug --no-daemon --max-workers=2 --console=plain
else gradle :app:assembleDebug --no-daemon --max-workers=2 --console=plain; fi
APK="$PROJECT/app/build/outputs/apk/debug/app-debug.apk"
[[ -s "$APK" ]] || { echo '[BCI] APK missing'; exit 1; }
sha256sum "$APK" | tee "$PROJECT/BCILock-v040-apk.sha256"
echo "[BCI] Debug APK ready: $APK"
echo '[BCI] Install with: adb install -r app/build/outputs/apk/debug/app-debug.apk'
