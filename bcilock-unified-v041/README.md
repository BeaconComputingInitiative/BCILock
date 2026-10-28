# BEACON COMPUTING INITIATIVE — BCILock v0.4.1
Developer Seal: CJB-DEV-2026

This patch merges BCILock's metallic dashboard and the BLE authenticator into **one existing Android application** (`org.beaconcomputing.bcilock`). The main red-eye/lighthouse interface remains the launcher. Its authorization control opens the signing interface as a second screen *inside the same APK*. The signing screen has no additional launcher icon and is not exported to other applications.

## Installation

Run `bash install-and-build.sh "$HOME/BCI/BCILock/android"` after applying the v0.3.0 metallic dashboard and v0.4.0 BLE bridge. Then install the APK with `adb install -r "$HOME/BCI/BCILock/android/app/build/outputs/apk/debug/app-debug.apk"`.

If the app was previously installed, `adb install -r` normally preserves its Android Keystore key and app data. Don't uninstall or clear app data during enrollment: doing so can invalidate the enrolled key.

## Bluetooth test limitations

The Kali `--test` operation issues a nonce and polls for a signed response. On the handset, **open BCILock, tap OPEN PHONE AUTHENTICATOR, and tap START BLUETOOTH AUTHENTICATOR**. Leave that screen visible, start `--test` on Kali, and accept the confirmation prompt and phone lock credential. If the prompt is not visible, check foreground state and nearby-device permissions. The existing signer relies on deprecated credential-confirmation APIs, which may behave differently on modern Android. Treat a timeout as failure rather than bypassing signing.

This version does **not** release XSecureLock or provide live firewall telemetry. It does not change your Kali firewall or desktop locking configuration. Do not enable phone-only desktop lock until signed verification succeeds and recovery is tested.
