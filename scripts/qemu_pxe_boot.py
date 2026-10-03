#!/usr/bin/env python3
"""Native OVMF PXE -> proof EFI, isolated SLIRP network, no guest storage.

This reference test uses QEMU/libslirp's TFTP server, not the Android service.
The shared Kotlin TFTP server is verified separately by real UDP tests.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess

from verify_efi import verify


def udp_packets(pcap: Path):
    """Read Ethernet/IPv4/UDP packets from QEMU's classic PCAP capture."""
    raw = pcap.read_bytes()
    if raw[:4] == b"\xd4\xc3\xb2\xa1":
        order = "<"
    elif raw[:4] == b"\xa1\xb2\xc3\xd4":
        order = ">"
    else:
        raise ValueError("Expected classic PCAP")
    offset = 24
    while offset + 16 <= len(raw):
        length = struct.unpack_from(order + "I", raw, offset + 8)[0]
        frame = raw[offset + 16:offset + 16 + length]
        offset += 16 + length
        if len(frame) < 42 or frame[12:14] != b"\x08\x00" or frame[23] != 17:
            continue
        ip_header = (frame[14] & 15) * 4
        at = 14 + ip_header
        if ip_header < 20 or len(frame) < at + 8:
            continue
        source, destination, size = struct.unpack_from("!HHH", frame, at)
        yield source, destination, frame[at + 8:at + size]


def capture_result(pcap: Path, filename: str, expected: bytes) -> dict:
    rrq = False
    blocks = {}
    acknowledged = set()
    for source, destination, payload in udp_packets(pcap):
        if destination == 69 and payload.startswith(b"\x00\x01"):
            rrq |= payload[2:].split(b"\x00", 1)[0] == filename.encode()
        if source not in (67, 68) and len(payload) >= 4 and payload[:2] == b"\x00\x03":
            block = struct.unpack_from("!H", payload, 2)[0]
            blocks[block] = payload[4:]
        if len(payload) == 4 and payload[:2] == b"\x00\x04":
            acknowledged.add(struct.unpack_from("!H", payload, 2)[0])
    received = b"".join(blocks[n] for n in sorted(blocks))
    return {"tftp_rrq": rrq, "tftp_bytes_match": received == expected,
            "tftp_blocks": len(blocks), "tftp_all_blocks_acknowledged": bool(blocks) and set(blocks) <= acknowledged}


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--qemu", default="qemu-system-x86_64")
    parser.add_argument("--code", type=Path, default=Path("/usr/share/OVMF/OVMF_CODE_4M.fd"))
    parser.add_argument("--vars", type=Path, default=Path("/usr/share/OVMF/OVMF_VARS_4M.fd"))
    parser.add_argument("--efi", type=Path, default=root / "android/app/src/main/assets/boot/bootx64.efi")
    parser.add_argument("--output", type=Path, default=Path("lab-pxe-output"))
    parser.add_argument("--timeout", type=int, default=120)
    args = parser.parse_args()
    proof = verify(args.efi)
    if not args.code.is_file() or not args.vars.is_file():
        parser.error("An OVMF code/variables pair with PXE is required")
    args.output.mkdir(parents=True, exist_ok=True)
    filename = "0123456789abcdef0123456789abcdef/bootx64.efi"
    tftp = args.output / "tftp"
    destination = tftp / filename
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(args.efi, destination)
    variables = args.output / "OVMF_VARS.fd"
    shutil.copyfile(args.vars, variables)
    serial = args.output / "serial.log"
    pcap = args.output / "network.pcap"
    log = args.output / "qemu.log"
    command = [args.qemu, "-machine", "q35", "-nodefaults", "-accel", "tcg", "-m", "512",
               "-drive", f"if=pflash,format=raw,readonly=on,file={args.code.resolve()}",
               "-drive", f"if=pflash,format=raw,file={variables.resolve()}",
               "-netdev", f"user,id=lan,tftp={tftp.resolve()},bootfile={filename}",
               "-device", "virtio-net-pci,netdev=lan,romfile=",
               "-object", f"filter-dump,id=traffic,netdev=lan,file={pcap.resolve()}",
               "-object", "rng-random,id=rng0,filename=/dev/urandom",
               "-device", "virtio-rng-pci,rng=rng0",
               "-boot", "order=n,strict=on", "-vga", "none", "-display", "none",
               "-serial", f"file:{serial.resolve()}", "-monitor", "none", "-no-reboot", "-nic", "none"]
    result = {"transport": "native OVMF UEFI PXE IPv4", "tftp_server": "QEMU/libslirp reference",
              "kotlin_server_in_this_vm": False, "guest_disks": 0, "nic_option_rom": "disabled",
              "secure_boot": "disabled", "efi_sha256": proof["sha256"], "success": False}
    try:
        with log.open("wb") as output:
            process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT)
            try:
                code = process.wait(timeout=args.timeout)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait(); code = -1
        text = serial.read_text(errors="replace") if serial.exists() else ""
        captured = capture_result(pcap, filename, args.efi.read_bytes()) if pcap.exists() else {}
        displayed = "PocketInstall boot successful" in text
        shutdown = code == 0 and "PocketInstall: shutting down." in text
        result.update(captured, displayed_success=displayed, shutdown=shutdown, qemu_exit=code)
        result["success"] = displayed and shutdown and all(captured.get(key) for key in
            ("tftp_rrq", "tftp_bytes_match", "tftp_all_blocks_acknowledged"))
    finally:
        (args.output / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
    return 0 if result["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
