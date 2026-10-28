# BEACON COMPUTING INITIATIVE - BCILock Architecture
Developer Seal: CJB-DEV-2026

## Scope
BCILock comprises a Kali Linux desktop controller, an Android BLE authenticator, an independent network egress guard, and optional Tailscale telemetry. These components do not yet form one verified end-to-end authorization chain.

## Trust boundaries

```text
Android BCILock app (Android Keystore; per-request user approval)
       | BLE GATT: public key, nonce, signed response
       v
Kali BLE verifier (bcilock-bridge-v040/kali/bci_ble_verify.py)
       | Validates enrolled key + fresh ECDSA P-256 signature
       v
VERIFICATION RESULT ONLY - no lock-release or firewall-allow integration
       
Bluetooth connection state (paired + trusted + connected)
       | separate legacy enforcement path
       v
bci-egress.service -> /usr/local/libexec/bci/egress.py
       | nftables table inet bci_phone_gate
       v
Outbound IP traffic gate (verified in connection-based configuration)
       
Desktop/XSecureLock -> auth_bci_bt (legacy reconnect-based path)
Optional Tailscale telemetry -> telemetry/BCILock-Tailscale-Telemetry-v0.1/
```

## Component ownership
- Android owns user presence, Android Keystore keys, BLE GATT advertising, and approval UI.
- BLE verifier owns phone public-key enrollment and verification of fresh signed challenges. Its `--test` must never unlock a desktop.
- Connection-based egress engine owns its *own* nftables table `inet bci_phone_gate`; this is not the same as `inet bci_lock` used by the older standalone Kali isolation installer.
- The Kali isolation installer owns `inet bci_lock` and its independent service, and MUST NOT be considered interchangeable with the BLE egress gate.
- Telemetry owns only its dedicated collector, API, timer, and service units. Its installer must not modify the egress guard.
- USBGuard policy exists only as a proposal until an operator separately reviews and applies it.

## Security invariant
Automatic access is authorized if and only if a fresh signed challenge was validated against the enrolled phone key **and** the authorized result is coupled to the relevant local enforcement mechanism. That combined invariant is a design target, **not** a verified present capability.

## Non-goals of this repository cleanup
No changes to firewall policies, systemd units, enrolled keys, desktop lock state, Android app credentials, Bluetooth settings, or running telemetry.
