# BEACON COMPUTING INITIATIVE
**Developer Seal:** `CJB-DEV-2026`  
**Component:** BCILock developer history register  
**Audit baseline:** October 31, 2026  
**Repository:** https://github.com/BeaconComputingInitiative/BCILock

## Evidence rules

This record combines public main-branch commits with available development conversations and operator-reported results. Every commit at the initial baseline has its own ledger entry. Chat-only drafts and tests are separate updates and do **not** constitute pushed features, independently verified enforcement, or a certified production release. The public log intentionally omits enrolled-device addresses, public-key fingerprints, private IPs and other endpoint-specific identifiers.

**Audited baseline head:** `c558b25b09cd0779062b4478d2dec5a7abf55f86`. Four published commits, no GitHub release object. [Most recent baseline CI successful](https://github.com/BeaconComputingInitiative/BCILock/actions/runs/40763210738) for the documentation-only repository head. Android source v1.6.2 is staging, not a certified production release.

## Historical GitHub development updates — all baseline commits

| # | Git timestamp | Commit | Published development update | CI for that SHA |
|---:|---|---|---|---|
| 1 | 2026-10-28 20:07:03 UTC | [`1764dccd0b7`](https://github.com/BeaconComputingInitiative/BCILock/commit/1764dccd0b7b80b7c2f8418c04bf4ca28a02963d) | Initial BCILock repository — Linux and Android integration | no workflow evidence |
| 2 | 2026-10-28 20:13:29 UTC | [`05ea8396a30`](https://github.com/BeaconComputingInitiative/BCILock/commit/05ea8396a30870829e7cf9f676b06f412fa8c811) | chore: organize BCILock repository and add static validation | [success](https://github.com/BeaconComputingInitiative/BCILock/actions/runs/40762269682) |
| 3 | 2026-10-28 20:22:44 UTC | [`2cda8e5b901`](https://github.com/BeaconComputingInitiative/BCILock/commit/2cda8e5b9014a97ce1775a0978cdfc9455df1ef2) | docs: BCILock enterprise Linux gatekeeper overview | [success](https://github.com/BeaconComputingInitiative/BCILock/actions/runs/40763040638) |
| 4 | 2026-10-28 20:25:49 UTC | [`c558b25b09c`](https://github.com/BeaconComputingInitiative/BCILock/commit/c558b25b09cd0779062b4478d2dec5a7abf55f86) | docs: publish official BCILock icon in GitHub README | [success](https://github.com/BeaconComputingInitiative/BCILock/actions/runs/40763210738) |

## Conversation-derived developer updates predating or extending the Git repository

These entries are drawn from relevant available ChatGPT development conversations and operator-supplied terminal results. They are **not** extra GitHub commits or independently audited production security outcomes.

| Chat update | Date (2026) | Development change or request | Observation, status, security limitation |
|---|---|---|---|
| LK-C01 | Sep–Oct | Design a Linux endpoint lock and egress gate using an enrolled Android phone, BLE verification and nftables. Prior systemd audit showed an egress service active since September. | Development history; do not infer complete signed authorization from service uptime |
| LK-C02 | Oct 26 | Audit \`bci-egress.service\`, nftables output/forward chains and phone connection transitions. | Operator-reported service active; network ALLOWED/DENIED observed under paired/trusted/connected presence criteria |
| LK-C03 | Oct 26 | Develop BLE bridge v0.4.0 with Android Keystore-backed P-256 key enrollment and per-request signed nonce challenge; first test timed out, later \`--test\` passed. | Operator-reported **fresh signed challenge verification PASS**; verifier's test explicitly leaves desktop/egress unchanged |
| LK-C04 | Oct 26 | Merge BLE authenticator into existing Android app rather than creating a second app; update Android UI v0.4.1 with same package branding. | Chat request and source components; full Android runtime integration not guaranteed by Git publication |
| LK-C05 | Oct 26–27 | Build Android v1.6 staging and v1.6.1–1.6.2 repair series: resolve Gradle context, \`fillViewport\`/WindowInsets errors, restore launch behavior and themed icon. | Operator reported a successful debug build and phone challenge; no certified production APK/release in this repository |
| LK-C06 | Oct 27 | Audit Linux enforcement and crypto boundary. A valid phone BLE challenge was observed, while egress implementation still relied on connection state and nftables allowed traffic under an unconditional accept path. | **Critical boundary:** fresh signature was **not** proven to authorize/revoke the firewall; old egress and signed test are separate paths |
| LK-C07 | Oct 27 | Decide remote enterprise endpoints use private mesh/VPN for management telemetry, while local phone authorization stays BLE-based. | Deployment policy, not automatic installation or verified remote fleet management |
| LK-C08 | Oct 28 | Request a single installer for network egress, BLE phone registration, \`home\`/\`lock\` aliases, optional USBGuard, prerequisites, rollback and recovery. | Draft/preview and preflight work; end-to-end installer unverified; operator should not enable enforcement without console recovery |
| LK-C09 | Oct 28 | Publish and organize the BCILock public repository with a detailed enterprise Linux gatekeeper README, active-versus-historical component matrix, CI, official artwork and SHA-256. | Four published commits audited below; CI passing at then-current head; docs deliberately do not assert integrated signed enforcement |
| LK-C10 | Oct 29 | Introduce v1.6 shadow authorization tests with signed challenge lease, expiry, disconnect invalidation and audit chain; observed VALID then EXPIRED. | Chat/package-local test with no nftables mutation; never equate shadow approval with egress release |
| LK-C11 | Oct 29 | Rehearse v1.7 policy-denial cases (missing phone, signature, expired lease, controller failure, policy drift); report 12/12 tests and audit evidence. | Local simulation/read-only testing, not production policy promotion |
| LK-C12 | Oct 29 | Develop v1.8 root-owned shadow broker and a read-only BCILock-to-BCIBot event publisher with short-lived sanitized snapshots. | Chat and local project notes; Nexus screenshot later showed expected runtime event file unavailable, so publisher availability is not confirmed |
| LK-C13 | Oct 29 | Event bridge v0.1.0–0.1.2: add \`terminal\` compatibility, deterministic \`explain\`, strictly gated \`ask\`, evidence-ID requirements and rejection of unsupported model grounding. | Chat-derived/local package revisions, offline tests reported up to 29; do not claim these modules were all pushed to the BCILock public Git repository |
| LK-C14 | Oct 31 | Keep BCILock native as the sole phone/BLE/nftables enforcement authority while Nexus consumes read-only telemetry and BCIBot remains nonprivileged. | Cross-repository architectural decision; current Nexus interface does not establish signed enforcement integration |
| LK-C15 | Oct 31 | Adopt five substantive chat updates per consolidated repository publication, tracked independently of mechanical commits and test-only adjustments. | User workflow instruction for future development |

## Security acceptance: never equate presence with cryptographic authorization

A host egress rule should be released **if and only if** a fresh signed phone challenge has been validated against the enrolled key, a bounded authorization lease is active, and the *actual* policy controller has coupled that authorization to its enforcement decision. Existing public docs explicitly say this complete chain **has not been verified end-to-end**.

Chat-only shadow demos, a signed \`--test\` result, an Android debug APK, or a green documentation CI run are not proof of a production fail-closed deployment. BCILock may retain separate legacy \`inet bci_lock\` and connection-based \`inet bci_phone_gate\` tables; avoid conflating them.

### Remaining engineering tasks

- Fully parameterize enrolled identity and credential/key material without embedding secrets in logs or Git.
- Validate the Android v1.6.2 staging app on-device: launch, BLE service, permissions, key persistence and negative challenge behavior.
- Connect signed approval to host enforcement only through a separately designed, rollback-capable policy controller, with explicit expiry, disconnect and denial coverage.
- Establish a reliable read-only BCILock event publisher for Nexus, with source freshness, integrity and missing-data labeling.
- Verify workstation recovery and denial behavior locally before a release tag is promoted; security service edits are not part of this documentation audit.

## Five-update release discipline (future)

Count each substantive chat-driven development requirement as one tracked revision when implemented and tested; label pending work separately. Collect **five accepted changes** per repository before one consolidated GitHub update. Technical checksum fixes, documentation repairs and CI reruns do not automatically consume a five-update slot. Publish if and only if the integrated batch passes integrity, regression, security-boundary review and operator-appropriate recovery checks. Out-of-cycle security updates require operator approval. This one-time historical log publication is not a production release or retroactive five-chat batch.

## Reproducible documentation checks

```bash
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
cd "$HOME/BCI/BCILock"
sha256sum -c docs/DEVELOPERS_LOG.md.sha256
sha256sum -c docs/SHA256SUMS
python3 -m unittest discover -s tests -v
```

This is a documentation-only change: no phone pairing, Android installation, nftables or systemd modification is performed.
