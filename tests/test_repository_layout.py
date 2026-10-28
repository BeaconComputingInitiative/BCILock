#!/usr/bin/env python3
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Component: Read-only repository integrity checks
# SHA-256: listed in docs/SHA256SUMS

"""Test repository organization without changing security services or devices."""

import hashlib
import pathlib
import subprocess
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

REQUIRED = (
    "README.md",
    "android/settings.gradle.kts",
    "android/app/build.gradle.kts",
    "android/app/src/main/AndroidManifest.xml",
    "android/app/src/main/java/org/beaconcomputing/bcilock/BciBleService.kt",
    "android/app/src/main/java/org/beaconcomputing/bcilock/BciBridgeActivity.kt",
    "bcilock-bridge-v040/kali/bci_ble_verify.py",
    "auth_bci_bt",
    "bci-lock-kali-install.sh",
    "install-egress.sh",
    "deploy-v15.sh",
    "bci-data-guard.sh",
    "telemetry/BCILock-Tailscale-Telemetry-v0.1/src/collector.py",
)
SHELL_SCRIPTS = (
    "bci-lock-kali-install.sh",
    "install-egress.sh",
    "deploy-v15.sh",
    "bci-data-guard.sh",
    "final-test-v15.sh",
    "build-recovery.sh",
)
PYTHON_SOURCES = (
    "auth_bci_bt",
    "bcilock-bridge-v040/kali/bci_ble_verify.py",
    "telemetry/BCILock-Tailscale-Telemetry-v0.1/src/api.py",
    "telemetry/BCILock-Tailscale-Telemetry-v0.1/src/collector.py",
)


class RepositoryLayoutTests(unittest.TestCase):
    def test_required_entrypoints(self):
        for rel in REQUIRED:
            with self.subTest(rel=rel):
                self.assertTrue((ROOT / rel).is_file(), rel)

    def test_shell_syntax(self):
        for rel in SHELL_SCRIPTS:
            with self.subTest(rel=rel):
                result = subprocess.run(
                    ["bash", "-n", str(ROOT / rel)],
                    capture_output=True,
                    text=True,
                    check=False,
                )
                self.assertEqual(result.returncode, 0, result.stderr)

    def test_python_syntax(self):
        for rel in PYTHON_SOURCES:
            with self.subTest(rel=rel):
                source = (ROOT / rel).read_bytes()
                compile(source, rel, "exec")

    def test_document_hashes(self):
        manifest = ROOT / "docs" / "SHA256SUMS"
        entries = 0
        for line in manifest.read_text(encoding="utf-8").splitlines():
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            digest, relative = line.split(maxsplit=1)
            target = ROOT / relative.lstrip("*")
            with self.subTest(path=relative):
                self.assertTrue(target.is_file(), relative)
                self.assertEqual(hashlib.sha256(target.read_bytes()).hexdigest(), digest)
            entries += 1
        self.assertGreater(entries, 3)


if __name__ == "__main__":
    unittest.main()
