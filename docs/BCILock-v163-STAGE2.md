# BEACON COMPUTING INITIATIVE
**Developer Seal:** CJB-DEV-2026
**Component:** BCILock v1.6.3 Stage 2 — BLE V2 signed preview
**Mode:** READ_ONLY_AUTHORIZATION_PREVIEW
**Source hashes:** `docs/BCILock-v163-STAGE2-SHA256SUMS`

## Contract

V1 remains unchanged: PUBLIC, CHALLENGE, RESPONSE and `BCILOCK-V1|UNLOCK|Enterprise|` remain supported. Stage 2 adds WRITE UUID `d527a813-7cfa-4ed9-85da-29b9fe088f61` and READ UUID `d527a814-7cfa-4ed9-85da-29b9fe088f61` under the existing BCILock service `d527a80f-7cfa-4ed9-85da-29b9fe088f61`.

V2 request body is 7-field ASCII canonical JSON with keys `protocol`, `scope`, `target`, `nonce`, `key_sha256`, `issued_at` and `expires_at`. Only `egress.preview` for `Enterprise` is eligible. The client generates a cryptographically random 32-byte nonce (lowercase hex), binds the request to the locally enrolled phone DER fingerprint and uses a 45-second lifetime. Android validates the scope, target, key fingerprint, canonical request bytes, expiry and an in-process replay cache; the user must explicitly approve after reviewing the fields and pass the secure screen-lock prompt.

V2 request fragmentation is independent of ATT MTU: `0x01|uint16_be(total_length)|first 17 bytes`; subsequent `0x02|uint16_be(sequence)|next <=17 bytes`. Each frame is 4–20 bytes and written with response. Reject sequences that are not contiguous, incomplete, oversized or stale. A GATT read on the V2 response characteristic returns only DER ECDSA signature bytes if the request was approved; empty otherwise. Linux verifies `ECDSA(P-256,SHA-256)` over `b"BCILOCK-AUTH-V2\\0" + canonical_request_bytes` with the existing enrolled DER public key.

**Transport limitations:** BLE peer presence does not establish computer identity; an unauthorized BLE peer might submit a preview request. This protocol does NOT authorize production firewall changes, egress, account unlock, or replay-proof durable permissions. Android re-enrollment after uninstall may change the private key; do not uninstall the enrolled authenticator to test Stage 2. A GitHub-hosted debug APK may use a different signing certificate than the installed app and MUST NOT be used to overwrite it without a safe update-signing strategy.

## Offline test (Kubuntu)

```bash
# BEACON COMPUTING INITIATIVE | Developer Seal: CJB-DEV-2026
python3 -m unittest discover -s tests -v
~/BCI/Projects/BCILock/ble-verifier-v31-venv/bin/python bcilock-bridge-v040/kali/bci_ble_v2_preview.py --self-test
```

## Live test (staging/test device only, once correctly signed/installed)

Open Android BCILock Authenticator, press START GATT, then from Kubuntu:
```bash
# BEACON COMPUTING INITIATIVE | Developer Seal: CJB-DEV-2026
~/BCI/Projects/BCILock/ble-verifier-v31-venv/bin/python bcilock-bridge-v040/kali/bci_ble_v2_preview.py --test
```
Review the display, press APPROVE V2 PREVIEW, use your device credential, and inspect the Linux output. PASS requires matching enrolled public key, fresh unexpired challenge and valid purpose-bound DER signature. The verifier creates only a JSONL audit file under `~/BCI/Projects/BCILock/diagnostics` and hashes it. It never changes firewall policies.

## Deployment gate

BUILD is a compilation qualification, not live Android verification. Do NOT replace the working enrolled Android app with an unsigned/differently signed CI debug artifact. The default firewall state stays unchanged, and V2 remains preview only until on-device integration, provenance, and explicit operator approvals are verified.
