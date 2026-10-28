#!/usr/bin/env python3
# BEACON COMPUTING INITIATIVE
# Component: BCILock BLE Challenge Verifier v0.4.0
# Developer Seal: CJB-DEV-2026
# SHA-256 in SHA256SUMS
"""Explicit pairing + one-time cryptographic test. NEVER unlocks a session."""
import argparse
import asyncio
import hashlib
import os
from pathlib import Path
from bleak import BleakClient, BleakScanner
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.exceptions import InvalidSignature

SERVICE = 'd527a80f-7cfa-4ed9-85da-29b9fe088f61'
PUBLIC = 'd527a810-7cfa-4ed9-85da-29b9fe088f61'
CHALLENGE = 'd527a811-7cfa-4ed9-85da-29b9fe088f61'
RESPONSE = 'd527a812-7cfa-4ed9-85da-29b9fe088f61'
PREFIX = b'BCILOCK-V1|UNLOCK|Enterprise|'
KEY_FILE = Path.home() / '.config' / 'bci-lock' / 'trusted-phone.der'

async def connect_device():
    print('[BCI] Scanning for BCILock BLE advertisement (up to 25s)…', flush=True)
    def matches(device, data):
        return SERVICE.lower() in [uuid.lower() for uuid in data.service_uuids]
    device = await BleakScanner.find_device_by_filter(matches, timeout=25)
    if device is None:
        raise RuntimeError('BCILock BLE advertisement not found; open BCILock Auth and tap Start')
    return device

async def procedure(mode):
    device = await connect_device()
    async with BleakClient(device, timeout=20) as client:
        remote = bytes(await client.read_gatt_char(PUBLIC))
        if not remote:
            raise RuntimeError('Phone public key unavailable; check Android secure screen lock')
        pub = serialization.load_der_public_key(remote)
        if not isinstance(pub, ec.EllipticCurvePublicKey) or not isinstance(pub.curve, ec.SECP256R1):
            raise RuntimeError('Unsupported phone key')
        digest = hashlib.sha256(remote).hexdigest()
        print('[BCI] Phone public-key SHA-256:', digest, flush=True)
        if mode == 'enroll':
            print('Compare this fingerprint with the one shown on the phone screen.')
            answer = input('Matches exactly? Type ENROLL to trust this key: ')
            if answer != 'ENROLL':
                raise RuntimeError('Enrollment cancelled')
            KEY_FILE.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            KEY_FILE.write_bytes(remote)
            KEY_FILE.chmod(0o600)
            print('[BCI] Phone public key enrolled locally; no desktop-lock changes.')
            return
        if not KEY_FILE.is_file():
            raise RuntimeError('No enrolled key: run --enroll first')
        enrolled = KEY_FILE.read_bytes()
        if remote != enrolled:
            raise RuntimeError('Public-key mismatch: rejecting device')
        nonce = os.urandom(32)
        await client.write_gatt_char(CHALLENGE, nonce, response=True)
        print('[BCI] Challenge sent. Approve on phone using its lock credential.', flush=True)
        signature = b''
        for _ in range(40):
            await asyncio.sleep(1)
            signature = bytes(await client.read_gatt_char(RESPONSE))
            if signature:
                break
        if not signature:
            raise RuntimeError('Timed out or denied: no signature returned')
        try:
            pub.verify(signature, PREFIX + nonce, ec.ECDSA(hashes.SHA256()))
        except InvalidSignature as exc:
            raise RuntimeError('Signature INVALID') from exc
        print('[BCI] PASS: fresh signed challenge verified against enrolled phone key.')
        print('[BCI] Verification test only: desktop remains unchanged.')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='BCILock BLE signature verification test')
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--enroll', action='store_true')
    group.add_argument('--test', action='store_true')
    args = parser.parse_args()
    try:
        asyncio.run(procedure('enroll' if args.enroll else 'test'))
    except Exception as exc:
        print(f'[BCI][FAIL] {exc}')
        raise SystemExit(1)
