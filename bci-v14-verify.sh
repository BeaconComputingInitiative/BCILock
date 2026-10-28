#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# Component: BCI Lock v1.4 — Verification
# Developer Seal: CJB-DEV-2026
# Verification-only: no security policy changes.

set -uo pipefail

ROOT="$HOME/BCI/BCILock"
REPORT="$ROOT/v14-verification.txt"

{
  echo "BEACON COMPUTING INITIATIVE"
  echo "Developer Seal: CJB-DEV-2026"
  echo "Component: BCI Lock v1.4"
  echo "Date: $(date -Is)"

  echo
  echo "=== PHONE STATUS ==="
  bluetoothctl info 48:BC:E1:E1:42:DB || true

  echo
  echo "=== BCI EGRESS SERVICE ==="
  systemctl is-enabled bci-egress.service 2>&1 || true
  systemctl is-active bci-egress.service 2>&1 || true
  systemctl show bci-egress.service \
    -p FragmentPath -p ExecStart 2>&1 || true

  echo
  echo "=== NFTABLES POLICY ==="
  sudo nft list ruleset 2>&1 || true

  echo
  echo "=== BLUETOOTH TRANSFER SERVICES ==="
  systemctl --user is-enabled obex.service 2>&1 || true
  pgrep -a 'obexd|bluetoothd|rfcomm' || true

  echo
  echo "=== BLUETOOTH NETWORK INTERFACES ==="
  ip -brief link show || true
  ip -details link show type bridge || true

  echo
  echo "=== USBGUARD STATE ==="
  command -v usbguard || true
  systemctl is-active usbguard.service 2>&1 || true
  systemctl is-enabled usbguard.service 2>&1 || true

  echo
  echo "=== USB DEVICE INVENTORY ==="
  lsusb || true

  echo
  echo "=== LINUX LOCKER ==="
  command -v xsecurelock || true
  bci-lock-bt status 2>&1 || true

} > "$REPORT"

chmod 600 "$REPORT"
sha256sum "$REPORT" > "$REPORT.sha256"

echo "BEACON COMPUTING INITIATIVE"
echo "Developer Seal: CJB-DEV-2026"
echo "Report: $REPORT"
echo "Report SHA-256:"
cat "$REPORT.sha256"

echo
echo "=== SERVICE SUMMARY ==="
grep -A 5 \
  '=== BCI EGRESS SERVICE ===' "$REPORT"

echo
echo "=== USBGUARD SUMMARY ==="
grep -A 4 \
  '=== USBGUARD STATE ===' "$REPORT"
