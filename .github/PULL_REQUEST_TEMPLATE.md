# BEACON COMPUTING INITIATIVE - BCILock Change Review
Developer Seal: CJB-DEV-2026

## Change and component ownership
Describe exactly which installer, Android module, Linux service, or telemetry component changes.

## Verification
- [ ] No private keys, tokens, enrollment files, device identifiers, or runtime logs added.
- [ ] `sha256sum -c docs/SHA256SUMS` and static tests pass.
- [ ] Existing deployed file paths and independent nftables tables are preserved.
- [ ] Android build/device tests performed (or clearly marked not run).
- [ ] BLE replay, invalid signature, absent phone, and timeout cases assessed when relevant.
- [ ] Firewall, lockdown, and local-console recovery assessed when relevant.

## Release claim
State actual verification evidence; do not promote staging code without device tests.
