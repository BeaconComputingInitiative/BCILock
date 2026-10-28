#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# BCILock Android Project Generator v0.1.0
# Developer Seal: CJB-DEV-2026
# SHA-256: generated for project sources.
set -euo pipefail

PROJECT="$HOME/BCI/BCILock/android"
PACKAGE="$PROJECT/app/src/main/java/org/beaconcomputing/bcilock"
RES="$PROJECT/app/src/main/res"

mkdir -p "$PACKAGE" "$RES/values" \
  "$PROJECT/app/src/main/assets/fonts"

cd "$PROJECT"

cat > settings.gradle.kts <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BCILock"
include(":app")
EOF

cat > build.gradle.kts <<'EOF'
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
EOF

cat > app/build.gradle.kts <<'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.beaconcomputing.bcilock"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.beaconcomputing.bcilock"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
EOF

cat > gradle.properties <<'EOF'
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=false
android.useAndroidX=true
kotlin.code.style=official
EOF

cat > app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<!-- BEACON COMPUTING INITIATIVE -->
<!-- Developer Seal: CJB-DEV-2026 -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="BCILock"
        android:theme="@android:style/Theme.Material.NoActionBar"
        android:usesCleartextTraffic="false"
        android:allowBackup="false">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

cat > "$PACKAGE/MainActivity.kt" <<'EOF'
// BEACON COMPUTING INITIATIVE
// Component: BCILock Android v0.1.0
// Developer Seal: CJB-DEV-2026
// This is a UI shell, not an unlock authorization.
package org.beaconcomputing.bcilock

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val cyan = Color.rgb(45, 221, 239)
    private val maroon = Color.rgb(112, 25, 57)
    private val onyx = Color.rgb(13, 17, 23)

    private fun font(name: String, fallback: Typeface): Typeface =
        try {
            Typeface.createFromAsset(assets, "fonts/$name")
        } catch (_: Exception) {
            fallback
        }

    private fun rounded(color: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = 22f
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val displayFont = font("oxanium.ttf", Typeface.DEFAULT_BOLD)
        val uiFont = font("rajdhani.ttf", Typeface.DEFAULT)
        val monoFont = font("jetbrainsmono.ttf", Typeface.MONOSPACE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(30, 64, 30, 32)
            setBackgroundColor(onyx)
        }

        fun text(
            value: String,
            size: Float,
            color: Int,
            face: Typeface
        ): TextView = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = face
            gravity = Gravity.CENTER
        }

        root.addView(text(
            "BEACON COMPUTING INITIATIVE",
            13f, cyan, displayFont
        ))

        root.addView(text(
            "BCI LOCK",
            44f, Color.WHITE, displayFont
        ).apply {
            setPadding(0, 64, 0, 8)
        })

        root.addView(text(
            "ENDPOINT AUTHENTICATION",
            17f, cyan, uiFont
        ))

        val status = text(
            "ENDPOINT: ENTERPRISE\n\n" +
            "PHONE AUTHENTICATION\n" +
            "NOT CONFIGURED",
            19f, Color.WHITE, uiFont
        ).apply {
            setPadding(20, 36, 20, 36)
            background = rounded(Color.rgb(27, 35, 44))
        }

        root.addView(
            status,
            LinearLayout.LayoutParams(
                -1,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 70 }
        )

        val action = text(
            "UNLOCK SETUP PENDING",
            20f, Color.WHITE, displayFont
        ).apply {
            setPadding(18, 24, 18, 24)
            background = rounded(maroon)
            isEnabled = false
        }

        root.addView(
            action,
            LinearLayout.LayoutParams(
                -1,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 35 }
        )

        root.addView(text(
            "v0.1.0  |  CJB-DEV-2026",
            13f, cyan, monoFont
        ).apply {
            setPadding(0, 60, 0, 0)
        })

        setContentView(root)
    }
}
EOF

# Install the three BCI typefaces from official sources.
# If a download fails, the app will use system fonts.
FONTDIR="$PROJECT/app/src/main/assets/fonts"

fetch_font() {
    local url="$1" output="$2"
    if curl -fsSL --retry 2 "$url" -o "$output.tmp"; then
        mv "$output.tmp" "$output"
    else
        rm -f "$output.tmp"
        echo "[BCI] Font unavailable: $(basename "$output")"
    fi
}

fetch_font \
 "https://raw.githubusercontent.com/google/fonts/main/ofl/oxanium/static/Oxanium-Bold.ttf" \
 "$FONTDIR/oxanium.ttf"

fetch_font \
 "https://raw.githubusercontent.com/google/fonts/main/ofl/rajdhani/Rajdhani-Medium.ttf" \
 "$FONTDIR/rajdhani.ttf"

fetch_font \
 "https://raw.githubusercontent.com/JetBrains/JetBrainsMono/master/fonts/ttf/JetBrainsMono-Regular.ttf" \
 "$FONTDIR/jetbrainsmono.ttf"

# Generate source integrity manifest.
find app settings.gradle.kts build.gradle.kts gradle.properties \
  -type f -print0 |
  sort -z |
  xargs -0 sha256sum > SOURCE_SHA256SUMS

echo "================================="
echo "BEACON COMPUTING INITIATIVE"
echo "Developer Seal: CJB-DEV-2026"
echo "Android project: $PROJECT"
echo "Source hash manifest: SOURCE_SHA256SUMS"
echo "================================="
