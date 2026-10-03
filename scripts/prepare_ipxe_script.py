#!/usr/bin/env python3
"""Render a lab iPXE script for a known LAN/session; never execute it here."""
import argparse
import ipaddress
from pathlib import Path
import re
from urllib.parse import urlsplit

p = argparse.ArgumentParser()
p.add_argument('--base-url', required=True)
p.add_argument('--output', type=Path, default=Path('winpe-output'))
args = p.parse_args()
url = urlsplit(args.base_url)
address = ipaddress.ip_address(url.hostname or '')
private = any(address in ipaddress.ip_network(n) for n in ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16'))
if url.scheme != 'http' or address.version != 4 or not (private or address.is_loopback) or url.query or url.fragment or url.username or url.password:
    p.error('Use an explicit private IPv4 HTTP URL without credentials/query')
if not re.fullmatch(r'/[a-f0-9]{32}/?', url.path):
    p.error('The URL must end with the exact 32-character session')
base = args.base_url.rstrip('/')
args.output.mkdir(parents=True, exist_ok=True)
script = f'''#!ipxe
# PocketInstall WinPE lab; Ethernet first. Nothing installs Windows here.
set base {base}
kernel ${{base}}/winpe/wimboot || goto failed
initrd --name bootmgfw.efi ${{base}}/winpe/bootmgfw.efi bootmgfw.efi || goto failed
initrd --name BCD ${{base}}/winpe/BCD BCD || goto failed
initrd --name boot.sdi ${{base}}/winpe/boot.sdi boot.sdi || goto failed
initrd --name boot.wim ${{base}}/winpe/boot.wim boot.wim || goto failed
boot || goto failed
:failed
echo PocketInstall: WinPE could not be loaded. No installation was started.
prompt Press a key to enter the local iPXE shell
shell
'''
(args.output / 'boot.ipxe').write_text(script)
(args.output / 'embedded.ipxe').write_text(f'#!ipxe\ndhcp || goto failed\nchain {base}/winpe/boot.ipxe || goto failed\n:failed\necho PocketInstall network handoff failed\nshell\n')
print('Lab scripts created; next prepare Microsoft files yourself and validate the loader chain.')
