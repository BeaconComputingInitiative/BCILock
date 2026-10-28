#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# Component: BCILock Java 17 Installer
# Developer Seal: CJB-DEV-2026
# Integrity: Upstream SHA-256 + local SHA-256
set -euo pipefail

ROOT="$HOME/BCI/BCILock"
TARGET="$HOME/.local/opt/bci-temurin17"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "BEACON COMPUTING INITIATIVE"
echo "Developer Seal: CJB-DEV-2026"
echo "Installing Eclipse Temurin Java 17"

mkdir -p "$ROOT" "$HOME/.local/opt"

# Resolve the official GitHub release assets.
python3 - "$TMP" <<'PY'
import json
import re
import sys
import urllib.request
from pathlib import Path

destination = Path(sys.argv[1])
url = (
    "https://api.github.com/repos/"
    "adoptium/temurin17-binaries/releases/latest"
)

request = urllib.request.Request(
    url,
    headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "BCILock-JDK-Installer"
    }
)

with urllib.request.urlopen(request, timeout=30) as response:
    release = json.load(response)

pattern = re.compile(
    r"^OpenJDK17U-jdk_x64_linux_hotspot_.*\.tar\.gz$"
)
assets = release["assets"]

archive = next(
    a for a in assets if pattern.fullmatch(a["name"])
)

checksum = next(
    a for a in assets
    if a["name"] == archive["name"] + ".sha256.txt"
)

(destination / "archive.url").write_text(
    archive["browser_download_url"]
)
(destination / "checksum.url").write_text(
    checksum["browser_download_url"]
)
(destination / "archive.name").write_text(archive["name"])

print("Release:", release["tag_name"])
print("Archive:", archive["name"])
PY

ARCHIVE="$(cat "$TMP/archive.name")"

curl -fL --retry 3 \
  "$(cat "$TMP/archive.url")" \
  -o "$TMP/$ARCHIVE"

curl -fL --retry 3 \
  "$(cat "$TMP/checksum.url")" \
  -o "$TMP/checksum.txt"

# Validate against the checksum published by Adoptium.
EXPECTED="$(grep -Eo '[a-fA-F0-9]{64}' \
  "$TMP/checksum.txt" | head -n1)"

test -n "$EXPECTED"

printf '%s  %s\n' "$EXPECTED" "$TMP/$ARCHIVE" \
  | sha256sum -c -

mkdir "$TMP/extracted"
tar -xzf "$TMP/$ARCHIVE" \
  -C "$TMP/extracted" --no-same-owner

JDK="$(find "$TMP/extracted" -mindepth 1 \
  -maxdepth 1 -type d | head -n1)"

test -n "$JDK"
test -x "$JDK/bin/java"
test -x "$JDK/bin/javac"

if [[ -d "$TARGET" ]]; then
    mv "$TARGET" "${TARGET}.previous"
fi

mv "$JDK" "$TARGET"

mkdir -p "$HOME/.config"

cat > "$HOME/.config/bci-java17.sh" <<'EOF'
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
export JAVA_HOME="$HOME/.local/opt/bci-temurin17"
export PATH="$JAVA_HOME/bin:$PATH"
EOF

LINE='[ -f "$HOME/.config/bci-java17.sh" ] && source "$HOME/.config/bci-java17.sh"'

for RC in "$HOME/.zshrc" "$HOME/.bashrc"; do
    touch "$RC"
    grep -Fqx "$LINE" "$RC" || echo "$LINE" >> "$RC"
done

source "$HOME/.config/bci-java17.sh"

echo
echo "=== JAVA VERIFICATION ==="
java -version
javac -version

echo
echo "=== INSTALLED FILE INTEGRITY ==="
sha256sum "$TARGET/bin/java" \
  "$TARGET/bin/javac" \
  > "$ROOT/temurin17-installed.sha256"

cat "$ROOT/temurin17-installed.sha256"

echo "[BCI] Temurin Java 17 installed."
