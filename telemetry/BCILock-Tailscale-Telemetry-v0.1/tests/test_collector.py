#!/usr/bin/env python3
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Component: BCILock Telemetry Collector Tests
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))
import unittest
from collector import describe_gate


def fixture(out=False, fwd=False):
    arr = [{'chain': {'family':'inet','table':'bci_phone_gate','name':name,
                      'hook':name,'type':'filter','policy':'drop'}}
           for name in ('output', 'forward')]
    for name, enabled in [('output',out),('forward',fwd)]:
        if enabled: arr.append({'rule': {'family':'inet','table':'bci_phone_gate',
                   'chain':name,'expr':[{'accept': None}]}})
    return {'nftables': arr}


class TestCollector(unittest.TestCase):
    def test_allow(self): self.assertEqual(describe_gate(fixture(True, True)), 'allow_rules_present')
    def test_block(self): self.assertEqual(describe_gate(fixture()), 'default_drop_no_global_allow')
    def test_inconsistent(self): self.assertEqual(describe_gate(fixture(True, False)), 'inconsistent')
    def test_unknown(self): self.assertEqual(describe_gate({'nftables': []}), 'unknown')
    def test_loopback_only(self):
        obj = fixture()
        obj['nftables'].append({'rule': {'family':'inet','table':'bci_phone_gate','chain':'output',
             'expr':[{'match': {'op': '==', 'left': {'meta': {'key': 'oifname'}}, 'right': 'lo'}}, {'accept': None}]}})
        self.assertEqual(describe_gate(obj), 'default_drop_no_global_allow')


if __name__ == '__main__': unittest.main()
