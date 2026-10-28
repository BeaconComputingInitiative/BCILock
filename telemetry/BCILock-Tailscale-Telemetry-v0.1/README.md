# BEACON COMPUTING INITIATIVE
Developer Seal: CJB-DEV-2026
Component: BCILock Tailscale Telemetry v0.1 (staging)

This is a **read-only** telemetry endpoint. It does not change the existing
BCILock v1.5 egress service or firewall policy, nor does it grant network access.
It samples the BCILock nftables table from a separate short-lived privileged
collector and serves JSON through a non-root API bound to 127.0.0.1:18766.

## Install

```bash
sudo bash install.sh
```

## Test locally

```bash
sudo systemctl status bci-lock-telemetry-collect.timer bci-lock-telemetry-api.service --no-pager
sudo cat /run/bci-lock-telemetry/status.json
sudo python3 - <<'PYTEST'
from pathlib import Path
from urllib.request import Request, urlopen
token = Path('/etc/bci-lock/telemetry.token').read_text().strip()
req = Request('http://127.0.0.1:18766/v1/status',
              headers={'Authorization': 'Bearer ' + token})
with urlopen(req, timeout=5) as response:
    print(response.read().decode())
PYTEST
```

Do not paste your token into chat or logs. Avoid command tracing (`set -x`).

## Expose to tailnet (not public internet)

Check `tailscale serve status` first: if other services are configured, plan
port/path coexistence before changing settings. To use dedicated port 8443:

```bash
sudo tailscale serve --bg --https=8443 http://127.0.0.1:18766
sudo tailscale serve status
```

Tailscale may prompt an administrator to enable HTTPS. Only Tailscale Serve
is intended; **never use Tailscale Funnel** for this service. Use the HTTPS
MagicDNS URL in the Serve status output and the token to access /v1/status.
Restrict tailnet access with Tailscale grants so only the managed phone and
administrators can access this endpoint. Port 8443 is not a security boundary.

This does **not** update the Android UI. The app still needs an authenticated
HTTPS client, safe token enrollment, endpoint discovery, and live status rendering.

## Meaning of reported gate states

- `allow_rules_present`: global accept found in both BCILock chains; the
  BCILock gate permits traffic, but other firewall chains may still drop.
- `default_drop_no_global_allow`: no unconditional global accept in either chain.
- `inconsistent`: one chain permits globally, the other does not.
- `unknown`: nftables cannot be inspected or expected policy drop is missing.

If snapshots are older than 12 seconds, the API returns HTTP 503.

## Rollback

```bash
sudo tailscale serve --https=8443 off
sudo systemctl disable --now bci-lock-telemetry-api.service bci-lock-telemetry-collect.timer
```

This rollback stops only the new telemetry components, not bci-egress.service.
Files and token remain for forensic/source integrity reasons.

## Operational limit

When the phone is disconnected, v1.5 may deny outbound packets needed
to reply to tailnet clients. This staging package does not introduce a
management-plane firewall exception. Remote status may therefore disappear
when locked, while the local snapshot continues to be produced. Do not infer
that a stale remote reading means egress was allowed or denied.
