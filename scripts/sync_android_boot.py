#!/usr/bin/env python3
"""Verify and copy the built proof EFI into the Android asset allowlist."""
import json
import shutil
from pathlib import Path
from verify_efi import verify

root = Path(__file__).resolve().parents[1]
source = root / 'boot/build/bootx64.efi'
result = verify(source)
target = root / 'android/app/src/main/assets/boot/bootx64.efi'
target.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(source, target)
(target.parent / 'manifest.json').write_text(json.dumps({
    'kind': 'pocketinstall-efi-proof', 'architecture': 'x64',
    'bytes': result['bytes'], 'sha256': result['sha256'], 'signed': result['signed'],
    'disk_access': False}, indent=2) + '\n')
print(f'Android EFI asset updated: {result["bytes"]} bytes, SHA256 {result["sha256"]}')
