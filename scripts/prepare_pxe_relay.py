#!/usr/bin/env python3
"""Prepare a restricted dnsmasq PXE relay; never start DHCP automatically.

The relay caches ONLY the verified PocketInstall proof EFI from the phone.
It advertises boot information for one target MAC while the router allocates IPs.
Linux + dnsmasq and an explicitly started privileged process are required.
"""
from __future__ import annotations

import argparse
import hashlib
import ipaddress
import json
from pathlib import Path
import re
import shlex
import socket
import urllib.parse
import urllib.request

from verify_efi import verify

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = tuple(ipaddress.IPv4Network(n) for n in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"))


def private_ipv4(value: str) -> ipaddress.IPv4Address:
    address = ipaddress.IPv4Address(value)
    if not any(address in network for network in PRIVATE):
        raise ValueError("Expected an RFC 1918 LAN IPv4 address")
    return address


def validate_url(value: str) -> tuple[ipaddress.IPv4Address, str]:
    url = urllib.parse.urlsplit(value)
    if url.scheme != "http" or url.username or url.password or url.query or url.fragment:
        raise ValueError("Use the exact plain HTTP proof URL without credentials, query or fragment")
    host = private_ipv4(url.hostname or "")
    if url.port is None or url.port not in range(1024, 65536):
        raise ValueError("Expected the phone HTTP server port, normally 8080")
    if not re.fullmatch(r"/[0-9a-f]{32}/bootx64\.efi", url.path):
        raise ValueError("Expected /SESSION/bootx64.efi; only the proof EFI is accepted")
    if url.netloc != f"{host}:{url.port}":
        raise ValueError("Expected a literal IPv4 address and port")
    return host, url.path.lstrip("/")


def validate_network(relay: str, phone: ipaddress.IPv4Address, prefix: int,
                     interface: str, mac: str) -> ipaddress.IPv4Network:
    relay_ip = private_ipv4(relay)
    network = ipaddress.IPv4Network(f"{relay_ip}/{prefix}", strict=False)
    if not any(network.subnet_of(parent) for parent in PRIVATE):
        raise ValueError("The entire selected subnet must be private")
    if phone not in network or relay_ip == phone:
        raise ValueError("Phone and relay must be distinct IPs on the selected subnet")
    if relay_ip in (network.network_address, network.broadcast_address):
        raise ValueError("Relay IP cannot be the network or broadcast address")
    if not re.fullmatch(r"[A-Za-z0-9_.:-]{1,15}", interface):
        raise ValueError("Invalid Linux Ethernet interface name")
    if not re.fullmatch(r"(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}", mac):
        raise ValueError("Expected the Ethernet MAC address of the target PC")
    if int(mac[:2], 16) & 1 or mac.lower() == "00:00:00:00:00:00":
        raise ValueError("Expected a nonzero unicast MAC address")
    return network


def configuration(relay: str, network: ipaddress.IPv4Network, interface: str,
                  mac: str, root: Path, filename: str) -> str:
    if any(c in str(root) for c in "\r\n\x00#"):
        raise ValueError("Unsafe TFTP directory name")
    return f"""# PocketInstall proof only; router retains all IP address allocation.
port=0
interface={interface}
bind-interfaces
dhcp-range={network.network_address},proxy,{network.netmask}
dhcp-host={mac.lower()},set:pocketinstall-target
dhcp-ignore=tag:!pocketinstall-target
dhcp-no-override
# Firmware commonly reports architecture 7 (BC_EFI) or 9 (x86-64_EFI).
pxe-service=tag:pocketinstall-target,BC_EFI,"PocketInstall proof UEFI x64",{filename},{relay}
pxe-service=tag:pocketinstall-target,x86-64_EFI,"PocketInstall proof UEFI x64",{filename},{relay}
dhcp-boot=tag:pocketinstall-target,{filename},,{relay}
enable-tftp
tftp-root={root}
tftp-max=4
log-dhcp
log-facility=-
"""


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("Redirects are not accepted for the LAN proof")


def download(url: str) -> bytes:
    # Do not leak the LAN session to a configured external HTTP proxy.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    with opener.open(url, timeout=5) as response:
        if response.status != 200:
            raise ValueError("Expected a complete HTTP 200 proof download")
        data = response.read(65537)
        if len(data) > 65536:
            raise ValueError("Proof EFI is unexpectedly large; no OS images accepted")
        return data


def prepare(boot_url: str, relay: str, interface: str, mac: str, prefix: int, output: Path) -> dict:
    phone, filename = validate_url(boot_url)
    network = validate_network(relay, phone, prefix, interface, mac)
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.bind((relay, 0))  # Verify that the selected relay IP is actually local.
    if output.is_symlink() or output.exists() and (not output.is_dir() or any(output.iterdir())):
        raise ValueError("Output must be a new or empty directory, not a symlink")
    output = output.resolve()
    tftp = output / "tftp"
    config = configuration(relay, network, interface, mac, tftp, filename)
    data = download(boot_url)
    expected = json.loads((ROOT / "android/app/src/main/assets/boot/manifest.json").read_text())["sha256"]
    digest = hashlib.sha256(data).hexdigest()
    if digest != expected:
        raise ValueError("Phone EFI differs from this release: check the APK version and SHA256")
    output.mkdir(mode=0o700, parents=True, exist_ok=True)
    payload = tftp / filename
    payload.parent.mkdir(mode=0o700, parents=True)
    payload.write_bytes(data)
    payload.chmod(0o600)
    result = verify(payload)
    conf = output / "dnsmasq.conf"
    conf.write_text(config)
    conf.chmod(0o600)
    metadata = {"phone_ip": str(phone), "relay_ip": relay, "target_mac": mac.lower(),
                "boot_file": filename, "sha256": digest, "disk_access": False,
                "secure_boot": "unsigned", "cached_from_phone": True,
                "lifetime": "30 minutes maximum when using the documented timeout command"}
    (output / "relay.json").write_text(json.dumps(metadata, indent=2) + "\n")
    command = ["sudo", "timeout", "30m", "dnsmasq", "--no-daemon", "--user=root",
               "--conf-file=" + str(conf)]
    (output / "START.txt").write_text(shlex.join(command) + "\n")
    print("Proof verified:", result["sha256"])
    print("Configuration prepared for one target MAC; no DHCP service has been started.")
    print("Check: dnsmasq --test --conf-file=" + shlex.quote(str(conf)))
    print("Start on an authorized LAN: " + shlex.join(command))
    print("Ctrl+C stops the relay. Remove the cache after the test. This does not install Windows.")
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--boot-url", required=True)
    parser.add_argument("--relay-ip", required=True)
    parser.add_argument("--interface", required=True)
    parser.add_argument("--target-mac", required=True)
    parser.add_argument("--prefix", type=int, default=24)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.boot_url, args.relay_ip, args.interface, args.target_mac, args.prefix, args.output)


if __name__ == "__main__":
    main()
