#!/usr/bin/env python3
"""Validate an APK against this checkout and prepare release assets."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare(apk: Path, output: Path, version: str, commit: str, aapt: Path) -> None:
    root = Path(__file__).resolve().parents[1]
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[a-zA-Z0-9.-]+)?", version):
        raise ValueError("Invalid version")
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Expected full source commit SHA")
    gradle = (root / "android/app/build.gradle.kts").read_text()
    expected_version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    expected_code = re.search(r"versionCode\s*=\s*(\d+)", gradle)
    if expected_version is None or expected_version.group(1) != version or expected_code is None:
        raise ValueError("Version does not match source checkout")
    badging = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True)
    package = re.search(
        r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",
        badging,
    )
    if package is None or package.groups() != (
        "app.pocketinstall", expected_code.group(1), version
    ):
        raise ValueError("APK application ID or version mismatch")

    license_text = (root / "LICENSE").read_bytes()
    if not license_text.startswith(b"PocketInstall Personal Use License 1.0"):
        raise ValueError("Unexpected project license")
    assets = root / "android/app/src/main/assets"
    required = [
        "licenses/PocketInstall-Personal.txt",
        "licenses/Apache-2.0.txt",
        "licenses/GNU-EFI.txt",
        "licenses/Skia-BSD.txt",
        "licenses/THIRD_PARTY_NOTICES.txt",
        "boot/bootx64.efi",
    ]
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise ValueError("Corrupt APK")
        if "assets/licenses/PocketInstall-MIT.txt" in archive.namelist():
            raise ValueError("Legacy project MIT license must not be bundled in this version")
        for resource in required:
            actual = archive.read("assets/" + resource)
            expected = (assets / resource).read_bytes()
            if actual != expected:
                raise ValueError("Bundled asset mismatch: " + resource)
        if archive.read("assets/licenses/PocketInstall-Personal.txt") != license_text:
            raise ValueError("APK project license differs from LICENSE")
        if archive.getinfo("assets/boot/bootx64.efi").compress_type != zipfile.ZIP_STORED:
            raise ValueError("EFI asset must be stored without compression")

    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        raise ValueError("Release output must be an empty directory")
    copies = {
        "PocketInstall-" + version + "-debug.apk": apk,
        "bootx64.efi": assets / "boot/bootx64.efi",
        "LICENSE.txt": root / "LICENSE",
        "THIRD_PARTY_NOTICES.txt": assets / "licenses/THIRD_PARTY_NOTICES.txt",
        "GNU-EFI.txt": assets / "licenses/GNU-EFI.txt",
        "Apache-2.0.txt": assets / "licenses/Apache-2.0.txt",
        "Skia-BSD.txt": assets / "licenses/Skia-BSD.txt",
    }
    for name, source in copies.items():
        shutil.copyfile(source, output / name)
    manifest = {
        "project": "PocketInstall",
        "version": version,
        "source_commit": commit,
        "license": "LicenseRef-PocketInstall-Personal-1.0",
        "android": {
            "application_id": "app.pocketinstall",
            "version_code": int(expected_code.group(1)),
            "signature": "debug; verified by apksigner before publication",
        },
        "hardware_tested": False,
        "efi_secure_boot": "unsigned",
        "artifacts": [
            {"name": name, "bytes": (output / name).stat().st_size,
             "sha256": sha256(output / name)}
            for name in sorted(copies)
        ],
    }
    (output / "release.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    names = sorted([*copies, "release.json"])
    (output / "SHA256SUMS").write_text(
        "".join(sha256(output / name) + "  " + name + "\n" for name in names),
        encoding="ascii",
    )
    print(json.dumps({"version": version, "source_commit": commit, "files": len(names) + 1}))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--aapt", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.apk, args.output, args.version, args.commit, args.aapt)


if __name__ == "__main__":
    main()
