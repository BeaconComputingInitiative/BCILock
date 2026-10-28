#!/usr/bin/env python3
# BEACON COMPUTING INITIATIVE
# Component: BCILock Tailscale Telemetry API v0.1
# Developer Seal: CJB-DEV-2026
# File hashes are recorded in SHA256SUMS.
"""Read-only loopback HTTP backend for Tailscale Serve, bearer-authenticated."""
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import hmac
import json
from pathlib import Path
import time

ADDRESS = ('127.0.0.1', 18766)
TOKEN_PATH = Path('/etc/bci-lock/telemetry.token')
SNAPSHOT_PATH = Path('/run/bci-lock-telemetry/status.json')
MAX_AGE_SECONDS = 12


def load_status():
    try:
        data = json.loads(SNAPSHOT_PATH.read_text(encoding='utf-8'))
        checksum = data.pop('payload_sha256')
        raw = json.dumps(data, sort_keys=True, separators=(',', ':')).encode()
        if not hmac.compare_digest(checksum, hashlib.sha256(raw).hexdigest()):
            return 503, {'error': 'snapshot_checksum_mismatch'}
        age = time.time() - int(data['captured_unix'])
        if not (0 <= age <= MAX_AGE_SECONDS):
            return 503, {'error': 'snapshot_stale'}
        data['payload_sha256'] = checksum
        data['status_age_seconds'] = round(age, 2)
        return 200, data
    except (OSError, ValueError, TypeError, KeyError):
        return 503, {'error': 'snapshot_unavailable'}


class Handler(BaseHTTPRequestHandler):
    server_version = 'BCILockTelemetry/0.1'
    sys_version = ''

    def respond(self, status, data):
        payload = (json.dumps(data, separators=(',', ':')) + '\n').encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        try:
            token = TOKEN_PATH.read_text(encoding='ascii').strip()
        except OSError:
            return self.respond(503, {'error': 'credentials_unavailable'})
        header = self.headers.get('Authorization', '')
        if not token or not hmac.compare_digest(header, 'Bearer ' + token):
            return self.respond(401, {'error': 'unauthorized'})
        if self.path != '/v1/status':
            return self.respond(404, {'error': 'not_found'})
        status, data = load_status()
        self.respond(status, data)

    def log_message(self, fmt, *args):
        # Avoid recording authorization headers or request parameters.
        pass


if __name__ == '__main__':
    server = ThreadingHTTPServer(ADDRESS, Handler)
    print(f'[BCI] Local API on {ADDRESS[0]}:{ADDRESS[1]}', flush=True)
    server.serve_forever(poll_interval=0.5)
