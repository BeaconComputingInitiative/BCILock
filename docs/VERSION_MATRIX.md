# BEACON COMPUTING INITIATIVE - BCILock Version Matrix
Developer Seal: CJB-DEV-2026

| Component | Source/version identifier | Evidence / limitations |
|---|---|---|
| Legacy Kali isolation | `bci-lock-kali-install.sh` v1.0 | Independent radio/network arm/home installer |
| Connection egress | `install-egress.sh` v1.4.1; `deploy-v15.sh` v1.5 | Phone-connection-based guard; separate from signed BLE test |
| Bluetooth reconnect auth | `auth_bci_bt` v1.1 | Reconnection test for XSecureLock, not signed BLE |
| Android BLE bridge | `bcilock-bridge-v040/` v0.4.0 | Earlier BLE patch and Kali signature verification |
| Android UI integration | `bcilock-unified-v041/` v0.4.1 | Earlier app-merging patch |
| Android canonical source | `android/app/build.gradle.kts` v1.6.2-staging | Compile and device behavior must be independently verified |
| Tailscale telemetry | `telemetry/BCILock-Tailscale-Telemetry-v0.1/` v0.1 | Optional telemetry module; not a security-authorizing component |

## Release controls
One active Android source tree lives in `android/`; snapshots and patch-sets are evidence, not extra releases. Tags should represent tested releases, not every script/backup. A release is accepted if and only if source review, integrity checks, relevant tests, device verification, and documented recovery all pass.

## Upcoming gates
1. Parameterize hard-coded enrolled device identity.
2. Verify Android staging app compilation and runtime permissions on device.
3. Verify negative BLE challenge cases and fresh approval.
4. Couple signed verification to local lock/egress policy with explicit timeout and rollback behavior.
5. Test fail-closed state and local recovery before promotion.
