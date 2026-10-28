#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# BCI Lock v1.4.1 — Egress Guard
# Developer Seal: CJB-DEV-2026
# SHA-256 manifests generated during installation.

set -euo pipefail

APP=/usr/local/libexec/bci
PHONE=48:BC:E1:E1:42:DB

if [[ $EUID -ne 0 ]]; then
    echo "[BCI] Run using sudo."
    exit 1
fi

apt-get install -y bluez nftables python3
install -d -m 755 "$APP"

cat > "$APP/egress.py" <<'PY'
#!/usr/bin/env python3
# BEACON COMPUTING INITIATIVE
# BCI Egress Guard v1.4.1
# Developer Seal: CJB-DEV-2026
# Non-loopback host IP traffic is permitted by
# this guard iff the enrolled phone is connected.

import re
import subprocess
import sys
import time

PHONE = "48:BC:E1:E1:42:DB"
TABLE = "bci_phone_gate"


def command(args, data=None):
    return subprocess.run(
        args, input=data, text=True,
        capture_output=True, timeout=8,
        check=True
    ).stdout


def nft(script):
    return command(["nft", "-f", "-"], script)


def exists():
    try:
        command(["nft", "list", "table", "inet", TABLE])
        return True
    except (OSError, subprocess.SubprocessError):
        return False


def install_gate():
    if not exists():
        nft("""
add table inet bci_phone_gate
add chain inet bci_phone_gate output { type filter hook output priority -50; policy drop; }
add rule inet bci_phone_gate output oifname "lo" accept
""")


def policy(connected):
    # One atomic nftables transaction.
    # A disconnected phone gives no external allow rule.
    rules = """
flush chain inet bci_phone_gate output
add rule inet bci_phone_gate output oifname "lo" accept
"""
    if connected:
        rules += (
            "add rule inet bci_phone_gate output "
            'oifname != "lo" accept\n'
        )
    nft(rules)


def phone_connected():
    try:
        output = command(
            ["bluetoothctl", "info", PHONE]
        )
    except (OSError, subprocess.SubprocessError):
        return False

    if not re.search(
        r"^Device\s+" + re.escape(PHONE) + r"(?:\s|$)",
        output, re.I | re.M
    ):
        return False

    return all(
        re.search(
            rf"^\s*{field}:\s+yes\s*$",
            output, re.I | re.M
        )
        for field in ("Paired", "Trusted", "Connected")
    )


def monitor():
    install_gate()
    policy(False)
    last = False
    print("[BCI] Egress guard started", flush=True)

    while True:
        state = phone_connected()

        if state != last:
            policy(state)
            print(
                "[BCI] Phone connected — egress permitted"
                if state else
                "[BCI] Phone disconnected — egress denied",
                flush=True
            )
            last = state

        time.sleep(0.5)


def main():
    action = sys.argv[1] if len(sys.argv) > 1 else "status"

    if action == "monitor":
        monitor()
    elif action == "block":
        install_gate()
        policy(False)
    elif action == "status":
        print("BEACON COMPUTING INITIATIVE")
        print("Developer Seal: CJB-DEV-2026")
        print("Phone verified:", phone_connected())
        print("Gate installed:", exists())
        if exists():
            print(command([
                "nft", "list", "table", "inet", TABLE
            ]))
    else:
        raise SystemExit("Invalid action")


if __name__ == "__main__":
    main()
PY

chmod 755 "$APP/egress.py"

cat > /etc/systemd/system/bci-egress.service <<'UNIT'
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
[Unit]
Description=BCI Bluetooth Egress Guard
Requires=bluetooth.service
After=bluetooth.service

[Service]
Type=simple
ExecStart=/usr/bin/python3 /usr/local/libexec/bci/egress.py monitor
ExecStopPost=/usr/bin/python3 /usr/local/libexec/bci/egress.py block
Restart=always
RestartSec=1
TimeoutStopSec=10

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload

sha256sum \
  "$APP/egress.py" \
  /etc/systemd/system/bci-egress.service \
  > /etc/bci-egress-SHA256SUMS

echo
echo "BEACON COMPUTING INITIATIVE"
echo "Developer Seal: CJB-DEV-2026"
echo "[BCI] Egress Guard installed."
echo "[BCI] Service not activated."
cat /etc/bci-egress-SHA256SUMS
