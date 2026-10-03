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
import xml.etree.ElementTree as ET


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
    boot_manifest = json.loads((assets / "boot/manifest.json").read_text())
    efi_asset = assets / "boot/bootx64.efi"
    if boot_manifest["sha256"] != sha256(efi_asset) or boot_manifest["bytes"] != efi_asset.stat().st_size:
        raise ValueError("EFI manifest differs from the asset")
    pxe_result = root / "lab-pxe-output/result.json"
    validation = json.loads(pxe_result.read_text())
    if validation.get("success") is not True or validation.get("guest_disks") != 0 or \
       validation.get("efi_sha256") != sha256(efi_asset) or validation.get("kotlin_server_in_this_vm") is not False:
        raise ValueError("Expected successful isolated reference PXE boot of this EFI")
    tests = sorted((root / "android/server-core/build/test-results/test").glob("TEST-*.xml"))
    if not tests:
        raise ValueError("Missing shared server test results")
    summary = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for report in tests:
        suite = ET.parse(report).getroot()
        for key in summary:
            summary[key] += int(suite.get(key, "0"))
    if summary["tests"] < 30 or any(summary[key] for key in ("failures", "errors", "skipped")):
        raise ValueError("Expected all TCP/USB/TFTP tests to pass")
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
        "PXE-QEMU-result.json": pxe_result,
        "PXE-QEMU-serial.txt": root / "lab-pxe-output/serial.log",
        "PXE-QEMU-network.pcap": root / "lab-pxe-output/network.pcap",
    }
    for name, source in copies.items():
        shutil.copyfile(source, output / name)
    reports = ET.Element("testsuites", {key: str(value) for key, value in summary.items()})
    for report in tests:
        reports.append(ET.parse(report).getroot())
    ET.ElementTree(reports).write(output / "SERVER-TESTS.xml", encoding="utf-8", xml_declaration=True)
    # Useful standalone relay source bundle; the phone supplies the proof binary.
    relay_files = ["LICENSE", "docs/PXE.md", "scripts/prepare_pxe_relay.py", "scripts/verify_efi.py",
                   "scripts/test_pxe_relay.py", "android/app/src/main/assets/boot/manifest.json"]
    with zipfile.ZipFile(output / "PXE-relay-tools.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        for name in relay_files:
            archive.write(root / name, name)
    artifact_names = sorted([*copies, "SERVER-TESTS.xml", "PXE-relay-tools.zip"])
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
        "pxe": {"firmware": "UEFI x64 PXE IPv4", "android_dhcp": False,
                "requires": "configured boot DHCP and standard TFTP, or an external relay",
                "android_tftp_ports": [69, 6969], "usb_pxe": False},
        "validation": {"pxe_reference_vm": validation, "shared_server_tests": summary,
                       "android_hardware_tested": False},
        "artifacts": [
            {"name": name, "bytes": (output / name).stat().st_size,
             "sha256": sha256(output / name)}
            for name in artifact_names
        ],
    }
    (output / "release.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    names = sorted([*artifact_names, "release.json"])
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
