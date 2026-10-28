# BEACON COMPUTING INITIATIVE - BCILock Operator Guide
Developer Seal: CJB-DEV-2026

## Local source tree
```bash
cd "$HOME/BCI/BCILock"
git status --short
git pull --ff-only
```

Root-level installers stay at their original paths to retain compatibility with the deployed machine. **Do not** run installers merely to check the repository layout.

## Safe, read-only inspection
```bash
cd "$HOME/BCI/BCILock"
python3 -m unittest discover -s tests -v
sha256sum -c docs/SHA256SUMS
bash -n bci-lock-kali-install.sh deploy-v15.sh install-egress.sh final-test-v15.sh
python3 bcilock-bridge-v040/kali/bci_ble_verify.py --help
```

The last command may require the already-configured project virtual environment because `bleak` and `cryptography` are external dependencies. Static tests do not require them.

## Desktop and firewall status (no policy changes)
```bash
systemctl is-active bci-egress.service || true
sudo nft list table inet bci_phone_gate
sudo bci-lock status
```

The last command applies only if the independent Kali isolation installer has already installed `bci-lock`. Egress and isolation are separate firewall mechanisms.

## Android status
```bash
cd "$HOME/BCI/BCILock/android"
gradle :app:assembleDebug --no-daemon --max-workers=2 --console=plain
```

This is a build attempt, not a verified result. Keep the existing Android Keystore and app data intact; uninstalling may invalidate phone enrollment.

## Changes that require local-console recovery planning
Running `deploy-v15.sh`, `install-egress.sh`, `final-test-v15.sh`, `bci-lock-kali-install.sh`, or any `nft`/systemd enable operation may change connectivity. Review the actual installed services and tables first. Never perform first-time enforcement over SSH.
