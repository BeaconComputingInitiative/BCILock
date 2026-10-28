# BCILock
### Enterprise Linux Endpoint Gatekeeper

**BEACON COMPUTING INITIATIVE (BCI)**  
**Developer:** Chance Joaquin Boone  
**Developer Seal:** CJB-DEV-2026  
**Repository:** [BeaconComputingInitiative/BCILock](https://github.com/BeaconComputingInitiative/BCILock)  
**Project stage:** Active development / security research prototype  
**Current Android source:** v1.6.2-staging — not a certified production release

> **A workstation should not be trusted simply because it is powered on, logged in, or connected to a network.** BCILock is being developed to make enterprise Linux endpoints enforce explicit, device-bound authorization before permitting sensitive local activity and network access.

BCILock combines a Linux enforcement layer, an Android phone authenticator, Bluetooth Low Energy (BLE), signed cryptographic challenges, and an optional private enterprise telemetry channel. Its long-term objective is to give administrators a consistent **gatekeeper** at each workstation: a local policy enforcement point that knows whether an endpoint should remain isolated, whether a user has proved possession of an enrolled device, and what network access is authorized.

**Transparency:** The current repository contains working, separately tested components, but the full cryptographic authorization-to-enforcement chain is **not yet integrated or verified end to end**. The existing connection-based network gate must not be mistaken for a gate driven by fresh signed approval. See [Implementation status](#implementation-status-and-evidence).

---

## 1. Why an endpoint gatekeeper?

Conventional endpoint controls often treat desktop authentication, Bluetooth proximity, host firewall rules, and remote device monitoring as independent concerns. BCILock is designed to bring those decisions under a coherent local security model.

Its intended enterprise responsibilities are:

- **Desktop access control:** keep the Linux user session protected until a permitted local authentication flow succeeds.
- **Outbound network control:** mediate egress at the Linux host, including IPv4 and IPv6, independently of application preferences.
- **Device-bound authorization:** use an enrolled Android work phone as a cryptographic authenticator, not merely as a nearby Bluetooth device.
- **Revocation and recovery:** withdraw access on policy failure, device loss, or authorization expiry, with a documented, auditable local-console recovery process.
- **Endpoint visibility:** report security posture, enforcement status, and significant state changes without granting remote telemetry services the ability to unlock the machine.
- **Enterprise operation:** support centrally distributed policy and management across fleets, while leaving enforcement and fresh phone approval on the endpoint.

The envisioned model is **zero-trust-inspired endpoint enforcement**, not a claim of certified Zero Trust Architecture or complete data-loss prevention.

## 2. The gatekeeper principle

The desired rule is:

**An endpoint receives a specific access privilege if and only if** (1) the enrolled device identity matches, (2) a fresh challenge has a valid signature and user approval, (3) the local policy authorizes the requested privilege, and (4) the authorization remains within its permitted lifetime.

Losing any required condition should revoke that privilege according to the enforced policy. A Bluetooth connection, paired-device flag, hardware address, cached response, or green UI indicator alone is **not authorization**.

This is the **target security contract**. The present v1.5 egress guard instead evaluates Bluetooth pairing/trust/connection state; a separate BLE verifier demonstrates signed challenge checks but does not command the firewall or desktop locker.

### Intended authorization sequence

```text
          ENTERPRISE MANAGEMENT / POLICY PLANE (planned)
            identity, device policy, revocation, audit
                              |
                   private VPN / mesh (planned
                    for managed remote endpoints)
                              |
                              v
                  LINUX ENDPOINT: BCILock
             +---------------------------------+
             | Local policy and state machine  |
             | Challenge verifier / key trust  |
             | Permission lease / revocation   |
             +----------------+----------------+
                              ^
                   BLE GATT   |   enrolled key
                              |
             +----------------+----------------+
             | ANDROID WORK PHONE              |
             | Android Keystore (P-256 key)    |
             | Explicit user-approved signing  |
             +---------------------------------+
                              |
                validated, time-bound decision
                              v
             +---------------------------------+
             | LOCAL ENFORCEMENT (Linux)       |
             | Desktop / session control       |
             | nftables egress policy          |
             | Optional device/transfer policy |
             +---------------------------------+
                              |
                  read-only posture/events
                              v
                  TELEMETRY / AUDIT PLANE
```

**Architecture note:** The diagram describes the intended integrated system. Today, the signed BLE test, Bluetooth-connection egress service, and XSecureLock-based authenticator are separate paths. Enterprise policy distribution and authorization leases are future work.

## 3. Security layers

| Layer | Responsibility | Present implementation / boundary |
|---|---|---|
| Android identity | Phone-held key, local user approval, signed response | Android app source in [`android/`](android/), v1.6.2-staging; device behavior requires continued testing |
| BLE verification | Enroll a public key, issue a fresh nonce, validate ECDSA P-256 signature | [`bci_ble_verify.py`](bcilock-bridge-v040/kali/bci_ble_verify.py); verification-only, does not unlock |
| Desktop gate | Protect or release the graphical session | [`auth_bci_bt`](auth_bci_bt) implements a legacy Bluetooth reconnection-based XSecureLock path |
| Network egress | Default-deny policy with conditional local authorization | [`install-egress.sh`](install-egress.sh) / [`deploy-v15.sh`](deploy-v15.sh); currently connection-based |
| Standalone isolation | Lock/restore radios, services, and host networking | [`bci-lock-kali-install.sh`](bci-lock-kali-install.sh); separate nftables table and recovery model |
| Transfer controls | Reduce selected Bluetooth/USB exfiltration avenues | [`bci-data-guard.sh`](bci-data-guard.sh) manages OBEX; USBGuard policy is only proposed |
| Telemetry | Collect and present endpoint guard state | [`telemetry/`](telemetry/), independent read-only Tailscale-oriented staging component |
| Enterprise orchestration | Enrollment lifecycle, policy rollout, approvals, fleet oversight | **Planned**, not a deployed fleet control plane |

No individual layer, by itself, establishes that the entire endpoint is safe or that all non-IP exfiltration paths have been blocked.

## 4. How Android authentication is intended to work

1. **Enrollment:** An administrator/operator pairs and verifies an authorized work phone, compares its public-key fingerprint out of band, and enrolls only the **public** key on Linux.
2. **Challenge:** The Linux verifier generates a fresh random challenge and sends it over BLE GATT. Bluetooth provides transport, **not trust**.
3. **User approval:** The Android application asks for explicit approval and device credential authentication as configured, then signs the challenge using Android Keystore-held key material.
4. **Verification:** Linux checks the enrolled public key, the expected signed message, and the ECDSA P-256/SHA-256 signature.
5. **Policy decision — future integration:** A local policy engine would bind a verified approval to a specific operation, endpoint and bounded authorization lifetime.
6. **Enforcement — future integration:** The Linux desktop gate or nftables controller would consume that scoped decision, revoke it on expiry/failure, and emit a security audit event.

A reusable Bluetooth address is **not** a credential. The private signing key is intended to remain on the phone. The existing verifier’s `--test` command explicitly reports success **without** releasing the desktop or allowing egress.

## 5. Endpoint state model (target design)

| State | Intended desktop posture | Intended network posture | Exit condition |
|---|---|---|---|
| `UNENROLLED` | Protected | Restricted to narrowly defined provisioning/recovery paths | Admin-authorized enrollment |
| `LOCKED` | Locked | Denied except explicit essential exceptions | Begin fresh authentication |
| `AUTH_PENDING` | Locked | Remains restricted | Verified approval or timeout |
| `AUTHORIZED` | Subject to scoped policy | Only authorized destinations/services and lease period | Expiry, phone loss, policy change, lock |
| `REVOKED` | Protected immediately | Restrictive policy restored | New approval or admin recovery |
| `RECOVERY` | Controlled local-console procedure | Minimal, explicitly documented access | Audited restoration |

These are **design states**, not a claim that a single state machine is implemented across all current components. Enterprise deployments require explicit DNS, DHCP, VPN/bootstrap, management and break-glass rules rather than indiscriminately dropping every packet.

### Fail-closed is necessary but not sufficient

The goal is to deny a privilege when authorization cannot be established. A production system must also prevent policy races, stale leases, reboot bypasses, systemd restart gaps, nftables rule collisions, and indefinite administrative lockout. **A recovery path must be verified on the physical console before enforcing a new policy.**

## 6. Enterprise deployment model

BCILock is intended for managed Linux fleets, including engineering workstations, operational terminals, security-analysis hosts, and remote employee laptops.

**On the workstation:** a local BCILock service owns authentication state and enforcement, integrates with the desktop lock and Linux firewall, records policy transitions, and refuses authorization when required evidence is missing.

**On the employee's Android work phone:** the authenticator holds the protected signing key and provides explicit human approval over a local Bluetooth link. The phone is not required to expose a public Internet endpoint.

**Across the enterprise:** a policy and telemetry plane would provision endpoint identities, define permitted destinations and roles, record attested state where available, revoke enrollment, and coordinate rollout. For remote enterprise endpoints, the planned management path is a **company VPN or mesh VPN**; this is separate from the local Bluetooth authentication channel.

**Management is not the unlock key.** A central monitoring service should not implicitly overrule a local, missing, expired, or invalid authentication result. Remote telemetry is observational until a separately specified and independently authenticated management protocol is built.

### Example policy scenario (proposed, not shipped)

A remote Linux engineer opens their laptop. The endpoint is placed in `LOCKED` with only the minimum approved bootstrap/management traffic. The engineer opens BCILock on their enrolled Android work phone, reviews the request, and signs a fresh challenge. A future local policy engine verifies the response, checks an employer-issued access policy, and permits a short-lived set of network destinations. When the phone disconnects or the authorization lease expires, the host returns to its restricted posture and reports the transition through its private management connection.

## 7. Implementation status and evidence

| Capability | Evidence in this repository | Status |
|---|---|---|
| BLE challenge with enrolled public key | Linux verifier uses a fresh nonce and P-256 signature verification; successful manual verification previously reported | **Component tested** |
| Connection-driven egress enforcement | v1.5 guard manages `inet bci_phone_gate` through nftables; earlier local tests reported working connection gating | **Component tested; not signature-gated** |
| Legacy desktop lock path | XSecureLock integration via `auth_bci_bt` and reconnection checks | **Separate legacy path** |
| Android authenticator and BLE service | Canonical Kotlin source, foreground service and UI in `android/` | **v1.6.2-staging; needs full device regression** |
| Optional private telemetry | Read-only collector, API, systemd units and tests under `telemetry/` | **Staging / independent** |
| BLE-signed approval automatically drives firewall or desktop unlock | No verified end-to-end coupling | **Not implemented/verified** |
| Central fleet policy service and role-based enrollment | Architecture direction only | **Planned** |
| USBGuard enforcement / complete non-IP containment | Proposed policy only | **Not enabled/verified** |
| Hardened multi-distro, production-grade fleet release | No production qualification evidence | **Not claimed** |

**Important distinction:** a successful cryptographic test does not mean the network gate is using cryptographic approval. A connected device can currently satisfy a separate, legacy egress condition. Do not deploy as a production passwordless-access or zero-trust enforcement solution on the strength of these tests alone.

## 8. Repository map

```text
BCILock/
├── README.md                        # Public overview and gatekeeper vision
├── android/                         # Canonical Kotlin / Gradle app sources
├── linux/README.md                  # Linux entrypoint ownership and compatibility
├── auth_bci_bt                       # Legacy XSecureLock Bluetooth authenticator
├── bci-lock-kali-install.sh          # Standalone Kali isolation installer
├── install-egress.sh                 # v1.4.1 connection-based egress setup
├── deploy-v15.sh                     # v1.5 egress update
├── final-test-v15.sh                 # Invasive integration test; enables service
├── bci-data-guard.sh                 # OBEX controls and local audits
├── usbguard-proposed.rules           # Example only; not an active policy
├── bcilock-bridge-v040/              # Prior BLE bridge and Kali verifier
├── bcilock-unified-v041/             # Historical Android integration patch
├── bcilock-metallic-v030/            # Historical Android UI patch
├── telemetry/                        # Optional read-only telemetry service
├── baselines/                        # Historical source snapshots
├── audits/                           # Historical audit metadata
├── tests/                            # Read-only repository-level tests
├── docs/
│   ├── ARCHITECTURE.md               # Trust boundaries
│   ├── COMPONENTS.md                 # Canonical vs historical inventory
│   ├── OPERATIONS.md                 # Safe operator guidance
│   ├── SECURITY.md                   # Disclosure and hardening policy
│   ├── VERSION_MATRIX.md             # Component status
│   └── SHA256SUMS                    # Documentation integrity checks
└── .github/workflows/               # Static validation CI
```

The older patch installers and historical snapshots are retained for provenance; **`android/` is the canonical Android source**. Existing Linux script locations are deliberately unchanged because several scripts use fixed installation paths. The independent nftables tables `inet bci_lock` and `inet bci_phone_gate` must never be treated as equivalent.

## 9. Safe evaluation (no security-policy changes)

Use the repository-level validation before examining live enforcement:

```bash
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Component: Read-only BCILock repository verification

git clone https://github.com/BeaconComputingInitiative/BCILock.git
cd BCILock

python3 -m unittest discover -s tests -v
sha256sum -c docs/SHA256SUMS

# Optional static inspection of Linux entrypoints:
bash -n bci-lock-kali-install.sh install-egress.sh deploy-v15.sh
```

These commands **do not** install, activate, or test a real firewall policy. A clean CI result verifies syntax, repository layout and the listed content hashes; it is **not** a deployment security certification.

For an existing Kali installation, follow [`docs/OPERATIONS.md`](docs/OPERATIONS.md). Review installers before execution: some intentionally enable services, modify nftables, or disable networking. In particular, `final-test-v15.sh` is **not read-only**. Never first deploy a lock/egress policy over SSH without local-console recovery.

### Android development

The application is a Gradle/Kotlin project requiring Android SDK components and a suitable Java 17/Gradle toolchain. From the `android/` directory, developers can attempt `gradle :app:assembleDebug` after installing the documented toolchain. Successful compilation alone does not validate permissions, enrollment persistence, BLE behavior, or the desktop enforcement link. Preserve existing phone app data and Keystore enrollment during updates.

## 10. Threat model and limitations

BCILock aims to reduce risks such as casual use of an unattended unlocked machine, uncontrolled host egress during a restricted session, unauthorized reauthorization by an untrusted phone, and unobserved policy-state changes.

The current prototype is **not** a replacement for full-disk encryption, secure boot, system hardening, EDR, kernel access control, VPN policy, PAM/FIDO2, or physical-security controls. A privileged attacker, compromised kernel, stolen unlocked endpoint, compromised phone, Bluetooth denial of service, alternative network namespace, tethering path, USB transport, or unprotected recovery mode may defeat incomplete controls. Network connectivity and confidentiality cannot be inferred from a single nftables table.

A successful BLE challenge proves possession/use of an enrolled signing key under the configured phone authentication mechanism; it does not prove user identity universally, device health, corporate compliance, or endpoint integrity.

## 11. Roadmap to enterprise readiness

1. **Secret and metadata hygiene:** remove device-specific identifiers from active code, sanitize historical audit artifacts, review the public Git history, and make enrollment configurable.
2. **Unified authorization contract:** define messages, state transitions, timeouts, explicit policy decisions, and per-privilege authorization leases.
3. **Signed-policy enforcement:** connect fresh verified BLE approval to the desktop and egress controllers, with replay protection and immediate revocation.
4. **Reliable Linux daemon:** make service restart/boot handling deterministic; prevent nftables collisions and race windows; provide safe idempotent installation and rollback.
5. **Fleet management:** certificate-backed device enrollment, per-tenant policy, identity/role checks, policy signatures, and controlled revocation.
6. **Private enterprise telemetry:** remotely view endpoint health over a managed VPN/mesh, with authenticated access and no implicit unlock privilege.
7. **Security validation:** threat-model reviews, negative tests, BLE replay/failure tests, lost-device scenarios, network-isolation tests, Android API compatibility, multi-distro testing, and auditable recovery exercises.
8. **Production packaging:** reproducible releases, signed packages, SBOM, documented upgrade path, and independent security review.

Only milestones proven by test evidence will be described as released functionality. See [`docs/VERSION_MATRIX.md`](docs/VERSION_MATRIX.md) for per-component version identifiers rather than treating each historical script as a separate product release.

## 12. Principles and governance

BCILock follows the Beacon Computing Initiative principles:

1. **Observe accurately** — verify actual hardware, network and authorization state.
2. **Preserve faithfully** — maintain source provenance, audit records and integrity hashes.
3. **Analyze transparently** — distinguish tested properties from design assumptions.
4. **Communicate securely** — protect enrollment details, credentials and telemetry.
5. **Act deliberately** — grant/revoke privileges through explicit policy with safe rollback.

Changes to authentication or enforcement require review of both success and failure paths. Repository code is provided for development and research; administrators must validate it in an isolated environment before use on managed workstations.

### Security, documentation and provenance

- [Architecture and trust boundaries](docs/ARCHITECTURE.md)
- [Component ownership](docs/COMPONENTS.md)
- [Operator guide](docs/OPERATIONS.md)
- [Security policy](docs/SECURITY.md)
- [Version matrix](docs/VERSION_MATRIX.md)
- [SHA-256 manifest](docs/SHA256SUMS)

**Public-repository warning:** Historical snapshots, scripts, and reports have contained device-specific identifiers and host configuration details. Publication does not anonymize earlier Git commits. Do not reuse embedded identifiers as defaults or assume this repository is safe to clone directly into an enterprise fleet. Security hygiene and history review remain open tasks.

---

**BEACON COMPUTING INITIATIVE**  
**BCILock — Endpoint authentication, controlled egress, and enterprise Linux gatekeeping.**  
**Developer Seal: CJB-DEV-2026**
