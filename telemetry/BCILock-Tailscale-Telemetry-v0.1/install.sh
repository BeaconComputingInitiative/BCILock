#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Component: BCILock v0.1 Telemetry Installer
# SHA-256: Uses package SHA256SUMS; never changes bci-egress.service or nftables.
# Installation iff source integrity and prerequisites pass.
set -euo pipefail
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"
if [[ "$EUID" -ne 0 ]]; then echo '[BCI][FAIL] Run with sudo' >&2; exit 1; fi
sha256sum -c SHA256SUMS
for tool in /usr/bin/python3 /usr/bin/systemctl /usr/sbin/nft /usr/bin/openssl; do
  if [[ ! -x "$tool" ]]; then echo "[BCI][FAIL] Missing $tool" >&2; exit 1; fi
done
if [[ ! -d /etc/systemd/system ]]; then echo '[BCI][FAIL] systemd unavailable' >&2; exit 1; fi
if ! getent passwd bci-telemetry >/dev/null; then
  useradd --system --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin bci-telemetry
fi
install -d -o root -g root -m 0755 /usr/local/libexec/bci-telemetry
install -d -o root -g root -m 0755 /run/bci-lock-telemetry
install -d -o root -g root -m 0755 /etc/bci-lock
install -o root -g root -m 0644 src/collector.py /usr/local/libexec/bci-telemetry/collector.py
install -o root -g root -m 0644 src/api.py /usr/local/libexec/bci-telemetry/api.py
# Never silently rotate an existing enrollment token.
if [[ ! -e /etc/bci-lock/telemetry.token ]]; then
  /usr/bin/openssl rand -hex 32 > /etc/bci-lock/telemetry.token
fi
chown root:bci-telemetry /etc/bci-lock/telemetry.token
chmod 0640 /etc/bci-lock/telemetry.token
for unit in systemd/*.service systemd/*.timer; do
  install -o root -g root -m 0644 "$unit" "/etc/systemd/system/$(basename "$unit")"
done
systemctl daemon-reload
systemctl enable --now bci-lock-telemetry-collect.timer
systemctl start bci-lock-telemetry-collect.service
systemctl enable --now bci-lock-telemetry-api.service
printf '[BCI] Telemetry API staged on loopback port 18766.\n'
printf '[BCI] Existing egress service and nftables unchanged.\n'
printf '[BCI] Do not publish with Funnel. Configure Tailscale Serve separately.\n'
sha256sum /usr/local/libexec/bci-telemetry/*.py
