#!/usr/bin/env bash
# ==================================================
# BEACON COMPUTING INITIATIVE
# Component: BCI Data Guard v1.3
# Developer Seal: CJB-DEV-2026
#
# Policy:
# Standard Bluetooth OBEX transfers are disabled
# if and only if the OBEX user units are masked
# and no existing OBEX daemon remains running.
#
# SHA-256: Generated at installation.
# ==================================================

set -euo pipefail

ROOT="$HOME/BCI/BCILock"
REPORT="$ROOT/data-guard-audit.txt"
PHONE="48:BC:E1:E1:42:DB"

mkdir -p "$ROOT"
chmod 700 "$ROOT"

case "${1:-audit}" in
  protect)
    echo "[BCI] Disabling Bluetooth OBEX transfers"

    # Block ordinary D-Bus activation of OBEX.
    systemctl --user mask --now \
      obex.service \
      dbus-org.bluez.obex.service

    # Stop a previously launched OBEX process
    # owned by the current user.
    if pgrep -u "$(id -u)" -x obexd >/dev/null; then
      pkill -u "$(id -u)" -x obexd || true
    fi

    echo "[BCI] OBEX protection applied"
    ;;

  restore)
    systemctl --user unmask \
      obex.service \
      dbus-org.bluez.obex.service

    echo "[BCI] OBEX service masks removed"
    ;;

  audit)
    {
      echo "BEACON COMPUTING INITIATIVE"
      echo "Developer Seal: CJB-DEV-2026"
      echo "Component: BCI Data Guard v1.3"
      echo "Date: $(date -Is)"
      echo

      echo "=== BLUETOOTH AUTHENTICATION ==="
      bluetoothctl info "$PHONE" || true

      echo
      echo "=== OBEX SERVICES ==="
      systemctl --user is-enabled \
        obex.service \
        dbus-org.bluez.obex.service || true

      systemctl --user is-active \
        obex.service \
        dbus-org.bluez.obex.service || true

      pgrep -a obexd || true

      echo
      echo "=== BLUETOOTH PROFILES ==="
      bluetoothctl show || true

      echo
      echo "=== NETWORK INTERFACES ==="
      ip -details link show || true

      echo
      echo "=== NON-IP / BRIDGE CONFIGURATION ==="
      bridge link show || true

      echo
      echo "=== USB DEVICES ==="
      lsusb || true
      lsblk -o NAME,TYPE,TRAN,MOUNTPOINTS || true

      echo
      echo "=== MTP / FILE SHARING PACKAGES ==="
      dpkg-query -W \
        'gvfs-backends' 'libmtp*' \
        'bluez-obexd' 'usbguard' 2>/dev/null || true

      echo
      echo "=== BCI FIREWALL ==="
      sudo nft list tables || true

      echo
      echo "=== LOADED PROTOCOL MODULES ==="
      lsmod | grep -E \
        'rfcomm|bnep|bluetooth|usb_storage' || true

    } > "$REPORT"

    chmod 600 "$REPORT"
    sha256sum "$REPORT" > "$REPORT.sha256"

    echo "[BCI] Audit: $REPORT"
    echo "[BCI] SHA-256:"
    cat "$REPORT.sha256"
    ;;

  status)
    echo "BEACON COMPUTING INITIATIVE"
    echo "Developer Seal: CJB-DEV-2026"

    echo "OBEX unit states:"
    systemctl --user is-enabled \
      obex.service \
      dbus-org.bluez.obex.service || true

    echo "OBEX processes:"
    pgrep -a obexd || true

    echo "Enrolled phone:"
    bluetoothctl info "$PHONE" || true
    ;;

  *)
    echo "Usage: $0 {protect|restore|audit|status}"
    exit 2
    ;;
esac
