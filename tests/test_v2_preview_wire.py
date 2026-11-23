# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Component: BCILock V2 wire protocol offline regression tests
# SHA-256: docs/BCILock-v163-STAGE2-SHA256SUMS
import hashlib
import importlib.util
import json
import sys
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "bcilock-bridge-v040" / "kali" / "bci_ble_v2_preview.py"
spec = importlib.util.spec_from_file_location("bci_ble_v2_preview", MODULE_PATH)
m = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = m
spec.loader.exec_module(m)


class V2ProtocolTests(unittest.TestCase):
    def setUp(self):
        self.key = b"unit-test-der-placeholder"
        self.nonce = bytes(range(32))
        self.payload, self.body = m.build_request(self.key, now=1700000000, nonce=self.nonce)

    def test_domain_separation(self):
        self.assertEqual(m.DOMAIN, b"BCILOCK-AUTH-V2\x00")
        self.assertNotIn(b"UNLOCK", m.DOMAIN)

    def test_exact_preview_scope(self):
        self.assertEqual(self.payload["scope"], "egress.preview")
        self.assertEqual(self.payload["target"], "Enterprise")
        self.assertEqual(self.payload["protocol"], "BCILOCK-AUTH-V2")

    def test_canonical_wire_bytes(self):
        expected = json.dumps(self.payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("ascii")
        self.assertEqual(self.body, expected)
        self.assertEqual(len(self.payload), 7)
        self.assertEqual(list(json.loads(self.body).keys()), sorted(self.payload))

    def test_key_binding_and_nonce(self):
        self.assertEqual(self.payload["key_sha256"], hashlib.sha256(self.key).hexdigest())
        self.assertEqual(self.payload["nonce"], self.nonce.hex())

    def test_lifetime(self):
        self.assertEqual(self.payload["expires_at"] - self.payload["issued_at"], 45)

    def test_all_frames_within_default_ble_mtu(self):
        packets = list(m.frames(self.body))
        self.assertGreater(len(packets), 10)
        self.assertTrue(all(4 <= len(p) <= 20 for p in packets))
        self.assertEqual(packets[0][0], 1)
        self.assertEqual(int.from_bytes(packets[0][1:3], "big"), len(self.body))
        self.assertEqual([int.from_bytes(p[1:3], "big") for p in packets[1:]], list(range(1, len(packets))))

    def test_reassembly(self):
        self.assertEqual(b"".join(p[3:] for p in m.frames(self.body)), self.body)

    def test_invalid_nonce_rejected(self):
        with self.assertRaises(ValueError):
            m.build_request(self.key, nonce=b"short")

    def test_invalid_frames_rejected(self):
        for wrong in (b"", b"x" * 63, b"x" * 1025):
            with self.assertRaises(ValueError):
                list(m.frames(wrong))

    def test_no_firewall_in_host_verifier(self):
        source = MODULE_PATH.read_text(encoding="utf-8")
        for forbidden in ("nft add ", "nft delete ", "iptables -A", "systemctl start bci-egress"):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
