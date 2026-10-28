# BEACON COMPUTING INITIATIVE - BCILock Security Policy
Developer Seal: CJB-DEV-2026

## Public repository disclosure

**This repository is public.** Earlier tracked installers, test reports and snapshots contain device-specific identifiers and host configuration metadata, including Bluetooth hardware addresses and local audit details. Public visibility means previously committed material may remain accessible through Git history even after files are removed from the current tree.

Until a complete public-history review and sanitization are finished, do **not** consider the repository free of identifying metadata. Treat any historically exposed credential or token as compromised: revoke/rotate it, validate runtime enrollment state, and only then plan history remediation with coordinated repository owners and existing clones. Do not force-push history casually; its security implications and clone synchronization must be reviewed.

## Never commit

Android signing keystores, private keys, API tokens, enrollment tokens, trusted-phone key material, personal device configuration, raw telemetry databases, hardware inventory dumps, signed APK release material, or real user credentials. Use parameterized or synthetic device identifiers for published examples.

The `.gitignore` file only protects *new untracked* files. It does not erase previously tracked files, Git objects, pull request content or forks.

## Authorization and fail-closed behavior

- A Bluetooth connection, device address, or paired/trusted flag is **not** a signed approval.
- Signed authorization is valid **if and only if** an enrolled key matches, a fresh non-replayable challenge verifies, relevant phone approval is satisfied, and an explicit local policy accepts the result for the requested operation and lifetime.
- The currently separate connection-based egress guard does **not** establish this signed-policy coupling.
- Telemetry and policy-management failures must never silently authorize egress.
- Local-console emergency recovery must exist and be physically tested before security enforcement is activated on production hosts.
- Do not assume all possible non-IP exfiltration or host root-bypass paths are blocked by the current prototype.

## Change control

Run static checks in CI, then perform scoped device and local-console tests for any authorization or enforcement change. Test invalid/replayed/expired signatures, removed phone, Bluetooth failure, restart and boot, race conditions, firewall coexistence, loss of remote management, and emergency recovery. CI must never enable `bci-egress.service`, run live `nft` changes, unlock XSecureLock, or pair a phone.

## Responsible reporting

Do not publish exploit-ready enrollment material or sensitive credentials in a public issue. Report suspected bypasses or exposure privately to the repository owner. Do not assume security reports are handled through an automated response process.
