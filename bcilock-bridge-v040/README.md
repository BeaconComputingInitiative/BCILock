# BEACON COMPUTING INITIATIVE — BCILock v0.4.0
Developer Seal: `CJB-DEV-2026`

## Scope
BLE GATT Android *peripheral* + Kali Linux *central* proof of authenticated challenge-response. It **does not unlock the desktop** or alter nftables. The original metallic dashboard stays installed; a second launcher `BCILock Auth` appears for transport testing. App must remain foreground during initial testing.

## Protocol
Custom GATT service `d527a80f-7cfa-4ed9-85da-29b9fe088f61`. `PUBLIC` read returns X.509 DER P-256 public key. `CHALLENGE` write accepts exactly 32 random bytes. Phone explicitly requests screen credentials, uses Android Keystore-backed P-256 key to ECDSA-SHA256 sign `BCILOCK-V1|UNLOCK|Enterprise|` concatenated with the challenge. `RESPONSE` read provides DER signature. Kali verifier checks public-key pinning and fresh random challenge.

**Security limitations:** This prototype uses a 30-second Android key-authentication window, not biometric per-operation confirmation. It doesn't enforce maximum signature lifetime at the GATT level; fresh random challenge binds verification. Bluetooth presence/transport isn't itself authentication. Manual first-time enrollment needs on-screen fingerprint comparison. BLE peripheral advertising support varies by phone and may fail; GATT read and 32-byte write might need ATT MTU negotiation. There is no daemonized, hardened session-unlock module yet, so keep existing Kali login and lock recovery enabled. No remote status collection yet.

## Install
From a terminal on Kali:

```bash
unzip bcilock-bridge-v040.zip -d "$HOME/BCI/BCILock"
bash "$HOME/BCI/BCILock/bcilock-bridge-v040/install-and-build.sh" "$HOME/BCI/BCILock/android"
adb install -r "$HOME/BCI/BCILock/android/app/build/outputs/apk/debug/app-debug.apk"
```

On phone: open **BCILock Auth**, grant Nearby Devices permission, tap **START BLUETOOTH AUTHENTICATOR**. Set a secure Android lock credential first.

For Kali tester:

```bash
sudo apt install -y python3-venv libdbus-1-dev libglib2.0-dev
python3 -m venv "$HOME/BCI/BCILock/.venv"
"$HOME/BCI/BCILock/.venv/bin/pip" install bleak cryptography
"$HOME/BCI/BCILock/.venv/bin/python" "$HOME/BCI/BCILock/bcilock-bridge-v040/kali/bci_ble_verify.py" --enroll
"$HOME/BCI/BCILock/.venv/bin/python" "$HOME/BCI/BCILock/bcilock-bridge-v040/kali/bci_ble_verify.py" --test
```

Start app before each `--enroll` and `--test`, and approve the credential prompt during test. Enrollment fingerprint must match between phone and Kali. This uses BLE advertising, which is **different** from the existing classic Bluetooth connection `48:BC:E1:E1:42:DB` and may use a randomized address.

## Next
After test passes, implement a per-session XSecureLock auth helper + restricted local IPC, credential expiry and replay protection, BLE reconnect lifecycle, and a safe rollback procedure. Then integrate the two launchers into the metallic dashboard.

## Integrity
Verify `SHA256SUMS` using `sha256sum -c SHA256SUMS` from this extracted directory. Source files contain BCI headers and developer seal.
