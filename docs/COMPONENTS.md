# BEACON COMPUTING INITIATIVE - BCILock Component Inventory
Developer Seal: CJB-DEV-2026

## Current source of truth
| Area | Current repository paths | Purpose / handling |
|---|---|---|
| Android application | `android/app/src/main/`, `android/*.gradle.kts` | Active Android sources; Gradle reports `1.6.2-staging` |
| Kali BLE verifier | `bcilock-bridge-v040/kali/bci_ble_verify.py` | Fresh signature test and local enrollment; **does not** release locks |
| Legacy desktop auth | `auth_bci_bt` | XSecureLock/Bluetooth reconnection authenticator; not cryptographic BLE authorization |
| Connection egress guard | `install-egress.sh`, `deploy-v15.sh`, `final-test-v15.sh` | Existing installation/update/test path with its own nftables table |
| Kali isolation | `bci-lock-kali-install.sh` | Independent arm/home firewall/radio isolation path |
| Transfer restrictions | `bci-data-guard.sh`, `usbguard-proposed.rules` | OBEX control and **unapplied** USBGuard proposal |
| Android build tools | `install-temurin17.sh`, `install-android-tools.sh`, `build-recovery.sh` | Host-side setup and diagnostics |
| Legacy Android patch sets | `bcilock-metallic-v030/`, `bcilock-bridge-v040/`, `bcilock-unified-v041/` | Earlier patch installers and source copies; not canonical app sources |
| Version snapshots | `baselines/`, `android/backup/`, `android/bci-backups/` | Historical baselines; retain hashes and paths |
| Optional telemetry | `telemetry/BCILock-Tailscale-Telemetry-v0.1/` | Separate API, collector, timer and tests |
| Audit artifacts | `audits/` and top-level `*.sha256` / reports | Provenance only; output may depend on local machine |

## Repository rules
1. Do not copy historical patch-source files over the active Android app as a routine deployment.
2. Do not move existing root installers or historical baselines before auditing hard-coded paths and verification manifests.
3. Changes to active security enforcement require isolated tests and a local-console recovery plan.
4. Generated APKs, signing material, runtime logs, database files, and enrolled public/private key files are not source artifacts.
5. Consolidate old snapshots only in a separately reviewed migration; preserve Git history and provenance.

## Published status
Android `1.6.2-staging` is a version string, not a verified production release. Prior BLE signature verification and connection-based egress evidence do not establish end-to-end signed authorization of the firewall.
