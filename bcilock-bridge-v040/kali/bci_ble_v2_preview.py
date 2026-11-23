#!/usr/bin/env python3
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Component: BCILock V2 signed BLE preview verifier
# SHA-256: docs/BCILock-v163-STAGE2-SHA256SUMS
# Mode: READ_ONLY_AUTHORIZATION_PREVIEW (never changes networking)
"""BCILock V2 preview: read-only computer-side challenger and signature verifier."""
import argparse
import asyncio
import hashlib
import json
import os
import time
from datetime import datetime, timezone
from pathlib import Path

SERVICE = "d527a80f-7cfa-4ed9-85da-29b9fe088f61"
PUBLIC = "d527a810-7cfa-4ed9-85da-29b9fe088f61"
V2_REQUEST = "d527a813-7cfa-4ed9-85da-29b9fe088f61"
V2_RESPONSE = "d527a814-7cfa-4ed9-85da-29b9fe088f61"
DOMAIN = b"BCILOCK-AUTH-V2\x00"
PROTOCOL = "BCILOCK-AUTH-V2"
SCOPE = "egress.preview"
TARGET = "Enterprise"
KEY_FILE = Path.home() / ".config" / "bci-lock" / "trusted-phone.der"
AUDIT_DIR = Path.home() / "BCI" / "Projects" / "BCILock" / "diagnostics"


def build_request(public_der: bytes, *, now: int | None = None, nonce: bytes | None = None):
    """Canonical 7-field ASCII JSON signed by Android; never asks for enforcement."""
    instant = int(time.time()) if now is None else now
    fresh = os.urandom(32) if nonce is None else nonce
    if len(fresh) != 32:
        raise ValueError("Nonce must be exactly 32 bytes")
    payload = {
        "protocol": PROTOCOL,
        "scope": SCOPE,
        "target": TARGET,
        "nonce": fresh.hex(),
        "key_sha256": hashlib.sha256(public_der).hexdigest(),
        "issued_at": instant,
        "expires_at": instant + 45,
    }
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("ascii")
    if not (64 <= len(canonical) <= 1024):
        raise ValueError("V2 request length invalid")
    return payload, canonical


def frames(canonical: bytes):
    """20-byte maximum GATT writes: START + 2-byte length; NEXT + 2-byte sequence."""
    if not (64 <= len(canonical) <= 1024):
        raise ValueError("V2 request length invalid")
    yield bytes((1,)) + len(canonical).to_bytes(2, "big") + canonical[:17]
    sequence = 1
    for i in range(17, len(canonical), 17):
        if sequence > 65535:
            raise ValueError("Packet sequence overflow")
        yield bytes((2,)) + sequence.to_bytes(2, "big") + canonical[i:i + 17]
        sequence += 1


def audit(status: str, request_sha256: str, detail: str):
    """Append audit metadata, never signatures or private keys; hash the resulting file."""
    AUDIT_DIR.mkdir(mode=0o700, parents=True, exist_ok=True)
    path = AUDIT_DIR / ("bci-v2-preview-" + datetime.now(timezone.utc).strftime("%Y%m%d") + ".jsonl")
    record = {
        "initiative": "BEACON COMPUTING INITIATIVE",
        "developer_seal": "CJB-DEV-2026",
        "time_utc": datetime.now(timezone.utc).isoformat(),
        "scope": SCOPE, "target": TARGET,
        "mode": "READ_ONLY_AUTHORIZATION_PREVIEW",
        "result": status, "request_sha256": request_sha256,
        "detail": detail[:100],
    }
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
    with os.fdopen(fd, "a", encoding="utf-8") as f:
        f.write(json.dumps(record, sort_keys=True) + "\n")
    manifest = path.with_suffix(path.suffix + ".sha256")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    manifest.write_text(f"{digest}  {path.name}\n", encoding="ascii")
    manifest.chmod(0o600)
    print(f"[BCI] Audit record SHA-256: {digest}", flush=True)


async def procedure():
    # Lazy imports keep the offline tests independent of BLE and cryptography installations.
    from bleak import BleakClient, BleakScanner
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.exceptions import InvalidSignature

    if not KEY_FILE.is_file():
        raise RuntimeError("No enrollment; verify V1 first. Never auto-enroll.")
    trusted = KEY_FILE.read_bytes()
    key_hash = hashlib.sha256(trusted).hexdigest()
    print(f"[BCI] Enrolled public-key SHA-256: {key_hash}", flush=True)
    public = serialization.load_der_public_key(trusted)
    if not isinstance(public, ec.EllipticCurvePublicKey) or not isinstance(public.curve, ec.SECP256R1):
        raise RuntimeError("Enrolled key is not P-256")
    print("[BCI] Scanning for BCILock GATT advertisement (25 seconds).", flush=True)
    device = await BleakScanner.find_device_by_filter(
        lambda dev, data: SERVICE.lower() in [s.lower() for s in data.service_uuids],
        timeout=25,
    )
    if device is None:
        raise RuntimeError("No BCILock advertisement. Start GATT on Android.")

    client = BleakClient(device, timeout=20)
    connected = False
    req_hash = ""
    try:
        await client.connect()
        connected = True
        remote = bytes(await client.read_gatt_char(PUBLIC))
        if not remote or remote != trusted:
            raise RuntimeError("Public-key mismatch: refusing V2 request; do not auto-enroll")
        payload, canonical = build_request(trusted)
        req_hash = hashlib.sha256(canonical).hexdigest()
        print("[BCI] Verified enrolled identity; sending V2 egress.preview only.", flush=True)
        print(f"[BCI] Request SHA-256: {req_hash}", flush=True)
        for frame in frames(canonical):
            await client.write_gatt_char(V2_REQUEST, frame, response=True)

        print("[BCI] Review target/scope/expiry on phone, tap APPROVE V2 PREVIEW, then unlock.", flush=True)
        for _ in range(40):
            await asyncio.sleep(1)
            if int(time.time()) >= payload["expires_at"]:
                raise RuntimeError("Request expired before signed response")
            signature = bytes(await client.read_gatt_char(V2_RESPONSE))
            if not signature:
                continue
            try:
                public.verify(signature, DOMAIN + canonical, ec.ECDSA(hashes.SHA256()))
            except InvalidSignature as exc:
                raise RuntimeError("V2 signature INVALID") from exc
            audit("PASS", req_hash, "Fresh signed preview verified; no network modifications")
            print("[BCI] PASS: V2 signed egress.preview verified against enrolled Android key.")
            print("[BCI] NO firewall changes; no enforcement; no desktop unlock.")
            return
        raise RuntimeError("Timed out waiting for a phone-approved V2 signature")
    except Exception as exc:
        audit("FAIL", req_hash, type(exc).__name__)
        raise
    finally:
        if connected:
            try:
                await client.disconnect()
            except Exception as exc:
                # BlueZ may throw after a successful remote disconnect: this is cleanup,
                # and never changes the outcome of the verified signature.
                print(f"[BCI] BLE disconnect cleanup: {type(exc).__name__}", flush=True)


def main():
    parser = argparse.ArgumentParser(description="BCILock Android V2 BLE signed preview (no enforcement)")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--test", action="store_true", help="Perform live Bluetooth preview")
    group.add_argument("--self-test", action="store_true", help="Offline protocol framing test")
    args = parser.parse_args()
    print("BEACON COMPUTING INITIATIVE | Developer Seal: CJB-DEV-2026")
    if args.self_test:
        payload, raw = build_request(b"offline-dummy", now=1700000000, nonce=bytes(range(32)))
        packets = list(frames(raw))
        rebuilt = b"".join(packet[3:] for packet in packets)
        assert rebuilt == raw and all(4 <= len(p) <= 20 for p in packets)
        assert payload["scope"] == "egress.preview"
        print(f"[BCI] PASS: V2 frame self-test ({len(packets)} packets) SHA-256 {hashlib.sha256(raw).hexdigest()}")
        return
    try:
        asyncio.run(procedure())
    except Exception as exc:
        print(f"[BCI][FAIL] {exc}", flush=True)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
