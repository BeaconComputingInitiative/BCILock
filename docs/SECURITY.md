# BEACON COMPUTING INITIATIVE - BCILock Security Policy
Developer Seal: CJB-DEV-2026

## Repository exposure
Keep this repository private pending a full secret and metadata review. Earlier tracked installer scripts contain a specific Bluetooth MAC address. A MAC is a device identifier, not a password, but it must be parameterized before public portfolio publication. Historical backups, test reports, and build manifests can also disclose device or host metadata.

## Never commit
Android signing keystores, private keys, API tokens, enrollment tokens, trusted-phone key material, personal device configuration, local telemetry databases, raw hardware inventory logs, signed APK release material, or real user credentials.

The `.gitignore` rules protect *new untracked* files only. They do not remove previously committed material or erase Git history. If a credential was committed, revoke/rotate it and follow a carefully reviewed history-rewrite process before public disclosure.

## Authorization and fail-closed
- A Bluetooth link, device MAC, or paired/trusted flag is **not** a signed approval.
- A signed authorization is valid if and only if the enrolled public key matches, the challenge is fresh, the signature verifies, and a local policy explicitly consumes that result.
- The presently independent connection-based egress guard is not evidence of that signed-policy coupling.
- A telemetry or API outage must not silently authorize egress.
- Keep emergency local-console recovery independent from the phone.

## Change control
Run static checks in CI. Test signed BLE behavior, power-loss boot, Bluetooth loss, failed signatures, network failures, and recovery physically before enabling any access-control modification. CI must never enable `bci-egress.service`, run `nft` changes, unlock XSecureLock, or pair a phone.

## Responsible reporting
Do not publish exploitable enrollment data or secrets in public issues. Contact the repository owner privately to report suspected authorization bypasses or credential exposure.
