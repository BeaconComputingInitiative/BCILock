#!/usr/bin/env python3
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Component: BCILock Telemetry API Unit Tests
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))
import api


class TestAPI(unittest.TestCase):
    def test_fresh_snapshot(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'status.json'
            body = {'captured_unix': int(time.time()), 'initiative': 'BEACON COMPUTING INITIATIVE'}
            digest = hashlib.sha256(json.dumps(body, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
            path.write_text(json.dumps({**body, 'payload_sha256': digest}))
            with patch.object(api, 'SNAPSHOT_PATH', path):
                status, data = api.load_status()
                self.assertEqual(status, 200)
                self.assertEqual(data['payload_sha256'], digest)

    def test_reject_tampered_snapshot(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'status.json'
            path.write_text(json.dumps({'captured_unix': int(time.time()), 'payload_sha256': '0' * 64}))
            with patch.object(api, 'SNAPSHOT_PATH', path):
                status, data = api.load_status()
                self.assertEqual((status, data['error']), (503, 'snapshot_checksum_mismatch'))

    def test_reject_stale_snapshot(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'status.json'
            body = {'captured_unix': int(time.time()) - 3600}
            digest = hashlib.sha256(json.dumps(body, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
            path.write_text(json.dumps({**body, 'payload_sha256': digest}))
            with patch.object(api, 'SNAPSHOT_PATH', path):
                status, data = api.load_status()
                self.assertEqual((status, data['error']), (503, 'snapshot_stale'))


if __name__ == '__main__': unittest.main()
