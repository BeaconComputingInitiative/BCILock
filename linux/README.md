# BEACON COMPUTING INITIATIVE - BCILock Linux Module
Developer Seal: CJB-DEV-2026

This directory defines the ownership boundary for Linux components. Existing operational entrypoints deliberately remain at the **repository root** because installed scripts and operator commands still refer to those paths.

- `../auth_bci_bt`: Bluetooth-reconnect XSecureLock authenticator (legacy).
- `../bci-lock-kali-install.sh`: standalone Kali isolation installer.
- `../install-egress.sh` and `../deploy-v15.sh`: egress engine install/update.
- `../final-test-v15.sh` and `../bci-v14-verify.sh`: test and audit procedures.
- `../bci-data-guard.sh`: OBEX transfer restriction and auditing.
- `../usbguard-proposed.rules`: proposed device policy, not active.
- `../bcilock-bridge-v040/kali/bci_ble_verify.py`: cryptographic BLE verification.

Do not interpret this folder as a second, complete implementation. Future source moves must update every installer, hash manifest, CI check, recovery instruction, and deployed reference atomically and only after local tests.
