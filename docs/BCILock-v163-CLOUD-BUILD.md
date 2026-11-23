# BEACON COMPUTING INITIATIVE
**Developer Seal:** CJB-DEV-2026  
**Component:** BCILock v1.6.3 cloud-build staging  
**SHA-256:** See `BCILock-v163-SHA256SUMS`

This draft stage installs `BciAuthorizationV2.kt` alongside the existing Android application without changing the legacy challenge response. The GitHub workflow builds a *debug APK* on a hosted runner and emits `out/SHA256SUMS` to verify the artifact.

**This is a signing library and compile qualification only.** There is no Android credential UI integration, BLE V2 GATT framing, live authorization, network egress activation or release signing. The app version may still report v1.6.2 staging. Do not deploy as a production authenticator. A CI build does not prove on-device security.

Acceptance is restricted to source integrity, successful Android compilation and a nonempty debug APK whose SHA-256 matches the uploaded manifest. Production deployment remains on HOLD. Do not insert an egress permission without a distinct Android-approved signed V2 payload and independently verified policy enforcement.
