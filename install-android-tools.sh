#!/usr/bin/env bash
# ==================================================
# BEACON COMPUTING INITIATIVE
# Component: BCILock Android Toolchain v1.0
# Developer Seal: CJB-DEV-2026
# Target: Kali Linux / x86_64
# Hash: Generated after script creation
# ==================================================

set -euo pipefail

BASE="$HOME/BCI/BCILock"
SDK="$HOME/Android/Sdk"
TOOLS="$SDK/cmdline-tools/latest"
GRADLE_VERSION="8.9"
GRADLE_HOME="$HOME/.local/opt/gradle-$GRADLE_VERSION"

ZIP="commandlinetools-linux-15859902_latest.zip"
EXPECTED_SHA="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"

mkdir -p "$BASE" "$SDK" "$HOME/.local/opt"

echo "======================================"
echo "BEACON COMPUTING INITIATIVE"
echo "BCILock Android Development Setup"
echo "Developer Seal: CJB-DEV-2026"
echo "======================================"

if [[ "$(uname -m)" != "x86_64" ]]; then
    echo "[BCI] Unsupported architecture."
    exit 1
fi

echo "[BCI] Installing system dependencies"

sudo apt-get update
sudo apt-get install -y \
    git \
    curl \
    wget \
    unzip \
    zip \
    ca-certificates \
    adb \
    cmake \
    ninja-build \
    build-essential \
    python3 \
    python3-venv \
    usbutils

JAVA17="$HOME/.local/opt/bci-temurin17"

if [[ ! -x "$JAVA17/bin/java" ]]; then
    echo "[BCI] Java 17 not found."
    exit 1
fi

echo "[BCI] JAVA_HOME: $JAVA17"

# Install official SDK command-line tools.
if [[ ! -x "$TOOLS/bin/sdkmanager" ]]; then
    echo "[BCI] Downloading Android SDK tools"

    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT

    curl -fL --retry 3 \
      "https://dl.google.com/android/repository/$ZIP" \
      -o "$TMP/$ZIP"

    echo "$EXPECTED_SHA  $TMP/$ZIP" | sha256sum -c -

    mkdir -p "$SDK/cmdline-tools"
    unzip -q "$TMP/$ZIP" -d "$TMP/extracted"

    mv "$TMP/extracted/cmdline-tools" "$TOOLS"
else
    echo "[BCI] Android command-line tools already installed"
fi

# Install Gradle 8.9 into the user directory.
if [[ ! -x "$GRADLE_HOME/bin/gradle" ]]; then
    echo "[BCI] Installing Gradle $GRADLE_VERSION"

    TMP_GRADLE="$(mktemp -d)"
    trap 'rm -rf "$TMP_GRADLE"' EXIT

    curl -fL --retry 3 \
      "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
      -o "$TMP_GRADLE/gradle.zip"

    unzip -q "$TMP_GRADLE/gradle.zip" -d "$HOME/.local/opt"
fi

# Add Android and Java tools to shell environments.
ENVFILE="$HOME/.config/bci-android-env.sh"
mkdir -p "$(dirname "$ENVFILE")"

cat > "$ENVFILE" <<EOF
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
export JAVA_HOME="$JAVA17"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$JAVA17/bin:$TOOLS/bin:$SDK/platform-tools:$SDK/build-tools/35.0.0:$GRADLE_HOME/bin:\$PATH"
EOF

for SHELLFILE in "$HOME/.zshrc" "$HOME/.bashrc"; do
    touch "$SHELLFILE"
    LINE='[ -f "$HOME/.config/bci-android-env.sh" ] && source "$HOME/.config/bci-android-env.sh"'
    grep -Fqx "$LINE" "$SHELLFILE" || \
        printf '\n%s\n' "$LINE" >> "$SHELLFILE"
done

# Load tools for this installation session.
source "$ENVFILE"

echo "[BCI] Installing Android SDK packages"

echo
echo "Android SDK licenses must be reviewed."
echo "Accept the licenses if you agree to their terms."
echo

"$TOOLS/bin/sdkmanager" --licenses

"$TOOLS/bin/sdkmanager" \
    "platform-tools" \
    "platforms;android-35" \
    "build-tools;35.0.0"

echo
echo "[BCI] Toolchain verification"

"$JAVA_HOME/bin/java" -version
"$GRADLE_HOME/bin/gradle" --version
"$TOOLS/bin/sdkmanager" --version
"$SDK/platform-tools/adb" version

echo
echo "[BCI] Generating integrity manifests"

sha256sum "$ENVFILE" > "$BASE/android-environment.sha256"

echo "======================================"
echo "[BCI] ANDROID TOOLCHAIN INSTALLED"
echo "Developer Seal: CJB-DEV-2026"
echo "SDK: $SDK"
echo "Gradle: $GRADLE_HOME"
echo "======================================"
