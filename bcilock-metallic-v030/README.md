# BEACON COMPUTING INITIATIVE — BCILock Metallic Interface v0.3.0
Developer Seal: **CJB-DEV-2026**

Source patch for existing Kali BCILock Android project. This is a real programmatic GUI, not a flat screenshot. Uses native Android views and a custom Canvas-drawn metallic surface to avoid new Compose / AndroidX dependencies. The design has a red eye and cyan lighthouse backdrop, angled metal cards, metallic highlights, cyan/red emission, subtle technical grid, Oxanium / Rajdhani / JetBrains Mono, responsive vertical scrolling, settings/information actions, and clear safety statuses.

**Security boundary:** This patch does not implement Bluetooth communication, Android Keystore signatures, local Kali verification, actual phone-only unlocking, or live network monitoring. The UNLOCK action shows an explanatory dialog and does not send commands. Nothing in the UI claims that unimplemented protection is functioning.

## Install

```bash
unzip -o ~/Downloads/bcilock-metallic-v030.zip -d ~/BCI/BCILock
bash ~/BCI/BCILock/bcilock-metallic-v030/install-and-build.sh ~/BCI/BCILock/android
```

The installer backs up the existing MainActivity, copies the bundled BCI art asset, writes GUI_v030_SHA256SUMS and builds the existing `:app` target. The build toolchain needs to exist on Kali; it is not bundled.

## Verify and deploy

```bash
cd ~/BCI/BCILock/android
sha256sum -c GUI_v030_SHA256SUMS
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Visual internals

`MetalDrawable` draws angled octagonal panel silhouettes, diagonal gunmetal gradients, thin upper silver highlights, dual crimson/cyan trim, lower light rails and precise rivets. `GridOverlay` draws a low-opacity cyan electronics grid and red vertical alignment guides. All screen elements remain separate Android Views for interaction and accessibility. The hero image is an actual in-app asset rendered with FitCenter.
