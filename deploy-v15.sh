#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# Component: BCI Lock v1.5 Deployment
# Developer Seal: CJB-DEV-2026
# SHA-256 generated for deployed components.
set -euo pipefail

ROOT=/usr/local/libexec/bci
HOME_DIR="${SUDO_USER:+$(getent passwd "$SUDO_USER" | cut -d: -f6)}"
HOME_DIR="${HOME_DIR:-$HOME}"
BACKUP="$ROOT/egress.py.v141.bak"
PHONE="48:BC:E1:E1:42:DB"

if [[ "$EUID" -ne 0 ]]; then
    echo "[BCI] Use sudo."
    exit 1
fi

test -f "$ROOT/egress.py" || {
    echo "[BCI] Previous guard missing."
    exit 1
}

cp -n "$ROOT/egress.py" "$BACKUP"

# Stop the previous service and remove only its
# own firewall table before replacing its engine.
systemctl stop bci-egress.service || true

if nft list table inet bci_phone_gate >/dev/null 2>&1; then
    nft delete table inet bci_phone_gate
fi

cat > "$ROOT/egress.py" <<'PY'
#!/usr/bin/env python3
# BEACON COMPUTING INITIATIVE
# BCI Lock — Egress Engine v1.5
# Developer Seal: CJB-DEV-2026
# Permit externally routed IP traffic iff the
# enrolled phone is paired, trusted, connected.

import re
import subprocess
import sys
import time

PHONE = "48:BC:E1:E1:42:DB"
TABLE = "bci_phone_gate"


def run(args, payload=None):
    return subprocess.run(
        args,
        input=payload,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=True,
        timeout=8,
    ).stdout


def nft(script):
    run(["nft", "-f", "-"], script)


def table_exists():
    try:
        run(["nft", "list", "table", "inet", TABLE])
        return True
    except (OSError, subprocess.SubprocessError):
        return False


def install():
    if table_exists():
        return

    nft("""
add table inet bci_phone_gate
add chain inet bci_phone_gate output { type filter hook output priority -50; policy drop; }
add chain inet bci_phone_gate forward { type filter hook forward priority -50; policy drop; }
add rule inet bci_phone_gate output oifname "lo" accept
""")


def connected():
    try:
        info = run(["bluetoothctl", "info", PHONE])
    except (OSError, subprocess.SubprocessError):
        return False

    identity = re.search(
        r"^Device\s+" + re.escape(PHONE) + r"(?:\s|$)",
        info,
        re.I | re.M,
    )

    properties = all(
        re.search(
            rf"^\s*{key}:\s+yes\s*$",
            info,
            re.I | re.M,
        )
        for key in ("Paired", "Trusted", "Connected")
    )

    return bool(identity and properties)


def apply(authorized):
    # Both chains are changed in one nft transaction.
    # An unauthorized state has no external allow rule.
    script = """
flush chain inet bci_phone_gate output
flush chain inet bci_phone_gate forward
add rule inet bci_phone_gate output oifname "lo" accept
"""

    if authorized:
        script += """
add rule inet bci_phone_gate output accept
add rule inet bci_phone_gate forward accept
"""

    nft(script)


def monitor():
    install()
    apply(False)
    previous = False

    print("[BCI] v1.5 monitoring active", flush=True)

    while True:
        current = connected()

        # Permission iff enrolled phone is verified.
        if current != previous:
            apply(current)
            previous = current
            print(
                "[BCI] Egress ALLOWED"
                if current else "[BCI] Egress DENIED",
                flush=True,
            )

        time.sleep(0.5)


def main():
    action = sys.argv[1] if len(sys.argv) > 1 else "status"

    if action == "monitor":
        monitor()

    elif action == "block":
        install()
        apply(False)

    elif action == "status":
        print("BEACON COMPUTING INITIATIVE")
        print("Developer Seal: CJB-DEV-2026")
        print("Phone:", PHONE)
        print("Phone verified:", connected())
        print("Firewall installed:", table_exists())
        if table_exists():
            print(run([
                "nft", "list", "table", "inet", TABLE
            ]))

    else:
        raise SystemExit("Unknown command")


if __name__ == "__main__":
    main()
PY

chmod 755 "$ROOT/egress.py"
python3 -m py_compile "$ROOT/egress.py"

# Verify known systemd service exists.
systemctl cat bci-egress.service >/dev/null

# Prepare, but DO NOT activate USBGuard.
POLICY="$HOME_DIR/BCI/BCILock/usbguard-proposed.rules"
if command -v usbguard >/dev/null 2>&1; then
    usbguard generate-policy -t block > "$POLICY"
    chmod 600 "$POLICY"
    if [[ -n "${SUDO_USER:-}" ]]; then
        chown "$SUDO_USER" "$POLICY"
    fi
    echo "[BCI] USBGuard policy generated; not activated."
else
    echo "[BCI] USBGuard not installed; policy deferred."
fi

# Regenerate hashes after the update.
sha256sum \
    "$ROOT/egress.py" \
    /etc/systemd/system/bci-egress.service \
    > /etc/bci-egress-SHA256SUMS

echo "BEACON COMPUTING INITIATIVE"
echo "Developer Seal: CJB-DEV-2026"
echo "[BCI] v1.5 deployment staged."
echo "[BCI] Previous engine backed up."
echo "[BCI] Service remains stopped until final test."
cat /etc/bci-egress-SHA256SUMS
