# BEACON COMPUTING INITIATIVE - BCILock

**Developer Seal:** CJB-DEV-2026  
**Repository:** BeaconComputingInitiative/BCILock  
**Status:** private development repository; Android **v1.6.2-staging**

BCILock is a Kali Linux + Android endpoint-security project incorporating local desktop locking, Bluetooth authentication, cryptographic BLE challenge verification, network egress controls, and optional enterprise telemetry.

## Start here

| Area | Location | Role |
|---|---|---|
| Android canonical app | [android/](android/) | Android source, Gradle configuration, BLE GATT service, authorization interface |
| Linux component map | [linux/README.md](linux/README.md) | Existing Kali entrypoints, boundaries and migration constraints |
| Architecture | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Trust boundaries, independent enforcement paths |
| Full inventory | [docs/COMPONENTS.md](docs/COMPONENTS.md) | Active versus historical file classification |
| Operations | [docs/OPERATIONS.md](docs/OPERATIONS.md) | Read-only status commands and build guidance |
| Security policy | [docs/SECURITY.md](docs/SECURITY.md) | Repository secrecy, authorization, recovery |
| Version matrix | [docs/VERSION_MATRIX.md](docs/VERSION_MATRIX.md) | Verified evidence versus staging |
| Static checks | [tests/](tests/) | Syntax, structure and SHA-256 without altering host state |
| Optional telemetry | [telemetry/](telemetry/) | Separate Tailscale telemetry module |
| Baselines | [baselines/](baselines/) | Historical snapshots and integrity records |

## Compatibility layout

Existing root-level Kali installers remain at their original paths. Earlier Android patch sets (`bcilock-metallic-v030/`, `bcilock-bridge-v040/`, `bcilock-unified-v041/`) are **legacy integration history**, not competing active apps. Do not run them over the current Android source during routine setup.

## Important security boundary

The BLE signature verifier and connection-based egress gate have been tested as separate capabilities. **Automatic signed-challenge authorization of network access or desktop unlock is not yet end-to-end verified.** A paired Bluetooth connection must not be treated as cryptographic approval.

The repository is private because existing scripts and historical records include device-specific metadata. Never commit secret keys or tokens. See [security policy](docs/SECURITY.md).

## Read-only repository verification

```bash
python3 -m unittest discover -s tests -v
sha256sum -c docs/SHA256SUMS
```

These commands do not install services, modify nftables, or change phone enrollment. Never enable or deploy an access-control change without a tested local-console recovery path.

## Provenance

All newly added organization files are checked by `docs/SHA256SUMS`. The initial legacy root `SHA256SUMS` and historical manifests remain unchanged for audit continuity.
