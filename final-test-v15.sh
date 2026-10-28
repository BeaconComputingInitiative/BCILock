#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# BCI Lock v1.5 — Final Integration Test
# Developer Seal: CJB-DEV-2026
set -uo pipefail

ROOT="$HOME/BCI/BCILock"
REPORT="$ROOT/final-test-v15.log"

{
    echo "BEACON COMPUTING INITIATIVE"
    echo "Developer Seal: CJB-DEV-2026"
    echo "BCI Lock v1.5 Integration Test"
    echo "Date: $(date -Is)"

    echo
    echo "=== INTEGRITY ==="
    sudo sha256sum -c /etc/bci-egress-SHA256SUMS

    echo
    echo "=== BLUETOOTH ==="
    bluetoothctl info 48:BC:E1:E1:42:DB

    echo
    echo "=== ACTIVATING EGRESS GUARD ==="
    sudo systemctl enable --now bci-egress.service
    sleep 3

    echo
    echo "=== SERVICE ==="
    sudo systemctl is-active bci-egress.service

    echo
    echo "=== FIREWALL ==="
    sudo nft list table inet bci_phone_gate

    echo
    echo "=== BLUETOOTH OBEX ==="
    systemctl --user is-enabled obex.service
    pgrep -a obexd || true

    echo
    echo "=== USBGUARD ==="
    if command -v usbguard >/dev/null; then
        echo "USBGuard installed"
    else
        echo "USBGuard installation pending"
    fi

    echo
    echo "=== DESKTOP LOCKER ==="
    command -v xsecurelock
    bci-lock-bt status

} 2>&1 | tee "$REPORT"

sha256sum "$REPORT" > "$REPORT.sha256"

echo
echo "BEACON COMPUTING INITIATIVE"
echo "Developer Seal: CJB-DEV-2026"
echo "Final report integrity:"
sha256sum -c "$REPORT.sha256"
