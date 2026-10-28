#!/usr/bin/env python3
# BEACON COMPUTING INITIATIVE
# Component: BCILock Tailscale Telemetry Collector v0.1
# Developer Seal: CJB-DEV-2026
# File hashes are recorded in SHA256SUMS.
"""Privileged read-only nftables snapshot; never changes network policy."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time

SNAPSHOT = Path('/run/bci-lock-telemetry/status.json')
TABLE = 'bci_phone_gate'


def command(argv):
    return subprocess.run(argv, text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, timeout=5, check=True).stdout


def describe_gate(data):
    rows = data.get('nftables')
    if not isinstance(rows, list):
        return 'unknown'
    chains = {}
    rules = {'output': [], 'forward': []}
    for row in rows:
        chain = row.get('chain')
        if chain and chain.get('family') == 'inet' and chain.get('table') == TABLE:
            if chain.get('name') in rules:
                chains[chain['name']] = chain
        rule = row.get('rule')
        if rule and rule.get('family') == 'inet' and rule.get('table') == TABLE:
            if rule.get('chain') in rules:
                rules[rule['chain']].append(rule.get('expr', []))
    for name in rules:
        chain = chains.get(name)
        if (not chain or chain.get('hook') != name or chain.get('policy') != 'drop'
                or chain.get('type') != 'filter'):
            return 'unknown'
    def any_unconditional_accept(exprs):
        return any(isinstance(expr, list) and len(expr) == 1
                   and isinstance(expr[0], dict) and 'accept' in expr[0]
                   for expr in exprs)
    out_allow = any_unconditional_accept(rules['output'])
    fwd_allow = any_unconditional_accept(rules['forward'])
    if out_allow and fwd_allow:
        return 'allow_rules_present'
    if not out_allow and not fwd_allow:
        return 'default_drop_no_global_allow'
    return 'inconsistent'


def capture():
    active = False
    try:
        active = command(['/usr/bin/systemctl', 'is-active', 'bci-egress.service']).strip() == 'active'
    except (OSError, subprocess.SubprocessError):
        pass
    try:
        gate = describe_gate(json.loads(command(['/usr/sbin/nft', '-j', 'list', 'table', 'inet', TABLE])))
    except (OSError, subprocess.SubprocessError, ValueError, TypeError):
        gate = 'unknown'
    body = {
        'initiative': 'BEACON COMPUTING INITIATIVE',
        'developer_seal': 'CJB-DEV-2026',
        'component': 'BCILock Tailscale Telemetry',
        'schema': 'bci-lock-status-v1',
        'captured_unix': int(time.time()),
        'egress_service_active': active,
        'bci_gate_mode': gate,
        'crypto_egress_integration': 'pending',
        'scope': 'BCILock gate only; not global network reachability',
    }
    canonical = json.dumps(body, sort_keys=True, separators=(',', ':')).encode()
    body['payload_sha256'] = hashlib.sha256(canonical).hexdigest()
    return body


def save(body, location=SNAPSHOT):
    location.parent.mkdir(parents=True, exist_ok=True, mode=0o755)
    os.chmod(location.parent, 0o755)
    fd, temp = tempfile.mkstemp(prefix='.status-', dir=location.parent)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as handle:
            json.dump(body, handle, indent=2, sort_keys=True)
            handle.write('\n')
            handle.flush()
            os.fsync(handle.fileno())
        os.chmod(temp, 0o644)
        os.replace(temp, location)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)


if __name__ == '__main__':
    save(capture())
    print('[BCI] Snapshot saved:', SNAPSHOT)
