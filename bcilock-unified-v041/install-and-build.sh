#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# BCILock Unified Android APK v0.4.1
# Developer Seal: CJB-DEV-2026
# Generated SHA-256 manifest includes all changed files.
set -euo pipefail
PROJECT="${1:-$HOME/BCI/BCILock/android}"
MAIN="$PROJECT/app/src/main/java/org/beaconcomputing/bcilock/MainActivity.kt"
BRIDGE="$PROJECT/app/src/main/java/org/beaconcomputing/bcilock/BciBridgeActivity.kt"
MANIFEST="$PROJECT/app/src/main/AndroidManifest.xml"
[[ -f "$MAIN" && -f "$BRIDGE" && -f "$MANIFEST" ]] || { echo '[BCI] Existing metallic app/bridge not found'; exit 1; }
mkdir -p "$PROJECT/bci-backups"
cp -p "$MAIN" "$PROJECT/bci-backups/MainActivity.before-v041.kt"
cp -p "$MANIFEST" "$PROJECT/bci-backups/AndroidManifest.before-v041.xml"
python3 - "$MAIN" "$MANIFEST" <<'PY'
from pathlib import Path
import sys, xml.etree.ElementTree as ET
main,manifest=map(Path,sys.argv[1:])
s=main.read_text()
if 'class MainActivity : Activity()' not in s:
    raise SystemExit('[BCI] Unexpected MainActivity structure: refusing to patch')
# Modify only existing authorization placeholder, keeping metallic surface unchanged.
old='''                info("AUTHORIZATION NOT AVAILABLE",
                    "This is a design preview. No unlock signal will be sent. Phone-side cryptographic signing and Kali-side verification must be implemented before activation.")'''
new='''                startActivity(android.content.Intent(this@MainActivity, BciBridgeActivity::class.java))'''
if old in s: s=s.replace(old,new,1)
elif new not in s: raise SystemExit('[BCI] Authorize action differs from expected v0.3.0; refusing to patch')
s=s.replace('"  AUTHORIZE UNLOCK  ›"','"  OPEN PHONE AUTHENTICATOR  ›"')
s=s.replace('"DESIGN PREVIEW · AUTHORIZATION DISABLED"','"SIGNED APPROVAL TEST · DESKTOP UNLOCK DISABLED"')
s=s.replace('"CJB-DEV-2026   //   v0.3.0"','"CJB-DEV-2026   //   v0.4.1"')
main.write_text(s)
# Remove extra launcher category only from BciBridgeActivity, retaining original MainActivity launcher.
ns='{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android','http://schemas.android.com/apk/res/android')
t=ET.parse(manifest); root=t.getroot(); app=root.find('application')
if app is None: raise SystemExit('[BCI] Missing <application>')
bridge=next((a for a in app.findall('activity') if a.get(ns+'name')=='.BciBridgeActivity'),None)
if bridge is None: raise SystemExit('[BCI] Missing BciBridgeActivity')
for filt in list(bridge.findall('intent-filter')):
    actions=[a.get(ns+'name') for a in filt.findall('action')]
    categories=[a.get(ns+'name') for a in filt.findall('category')]
    if 'android.intent.action.MAIN' in actions and 'android.intent.category.LAUNCHER' in categories:
        bridge.remove(filt)
bridge.set(ns+'exported','false')
bridge.set(ns+'label','BCILock Authentication')
t.write(manifest,encoding='unicode',xml_declaration=True)
print('[BCI] Merged into one Android application and launcher')
PY
if [[ -f "$HOME/.config/bci-java17.sh" ]]; then source "$HOME/.config/bci-java17.sh"; fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
cd "$PROJECT"
if [[ -x ./gradlew ]]; then ./gradlew :app:assembleDebug --no-daemon --max-workers=2 --console=plain; else gradle :app:assembleDebug --no-daemon --max-workers=2 --console=plain; fi
APK="$PROJECT/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
sha256sum "$MAIN" "$BRIDGE" "$MANIFEST" "$APK" | tee "$PROJECT/BCILock-v041-SHA256SUMS"
echo '[BCI] One APK, one launcher; build complete.'
echo "[BCI] APK: $APK"
