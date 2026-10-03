#!/usr/bin/env python3
"""Boot the POC via native OVMF HTTP Boot; attach no guest storage disks.

Requires QEMU, an HTTP-enabled OVMF pair, and virt-fw-vars from virt-firmware.
The only writable guest backing file is a disposable firmware variable store.
"""
import argparse
import json
import subprocess
import threading
from pathlib import Path
from dev_server import make_server


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--qemu', default='qemu-system-x86_64')
    parser.add_argument('--vars-tool', default='virt-fw-vars')
    parser.add_argument('--code', type=Path, default=Path('/usr/share/OVMF/OVMF_CODE_4M.fd'))
    parser.add_argument('--vars', type=Path, default=Path('/usr/share/OVMF/OVMF_VARS_4M.fd'))
    parser.add_argument('--efi', type=Path, default=Path(__file__).resolve().parents[1] / 'boot/build/bootx64.efi')
    parser.add_argument('--output', type=Path, default=Path('lab-output'))
    parser.add_argument('--qemu-data', type=Path)
    parser.add_argument('--timeout', type=int, default=120)
    parser.add_argument('--external-url', help='Optional exact Android URL. Host and phone must share a LAN.')
    args = parser.parse_args()
    for file in (args.code, args.vars, args.efi):
        if not file.is_file():
            parser.error(f'Missing {file}; see docs/TESTING.md')
    args.output.mkdir(parents=True, exist_ok=True)
    log = args.output / 'http.jsonl'
    log.write_text('')
    server = None
    if args.external_url:
        if not args.external_url.startswith('http://') or not args.external_url.endswith('/bootx64.efi'):
            parser.error('Use the exact HTTP boot URL displayed by Android')
        url = args.external_url
    else:
        server = make_server('127.0.0.1', 0, '127.0.0.0/8', {'bootx64.efi': args.efi}, log)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        url = f'http://10.0.2.2:{server.server_port}/{server.session}/bootx64.efi'
    variables = args.output / 'OVMF_VARS.fd'
    serial = args.output / 'serial.log'
    stdout = args.output / 'qemu.log'
    debug = args.output / 'firmware-debug.log'
    serial.write_text('')
    debug.write_text('')
    command = [args.qemu, '-machine', 'q35', '-nodefaults', '-accel', 'tcg', '-m', '512',
               '-drive', f'if=pflash,format=raw,readonly=on,file={args.code.resolve()}',
               '-drive', f'if=pflash,format=raw,file={variables.resolve()}',
               '-netdev', 'user,id=lan',
               '-device', 'virtio-net-pci,netdev=lan,romfile=',
               '-object', 'rng-random,id=rng0,filename=/dev/urandom',
               '-device', 'virtio-rng-pci,rng=rng0',
               '-vga', 'none', '-display', 'none', '-serial', f'file:{serial.resolve()}',
               '-monitor', 'none', '-no-reboot', '-nic', 'none',
               '-debugcon', f'file:{debug.resolve()}', '-global', 'isa-debugcon.iobase=0x402']
    # romfile= disables the NIC's iPXE option ROM: the HTTP client must be OVMF.
    # VirtioNetDxe provides the NIC driver in OVMF, without an option ROM.
    # Current EDK II network libraries require EFI_RNG_PROTOCOL; supply entropy.
    # -nic none disables QEMU's implicit second NIC; the explicit virtio NIC stays.
    if args.qemu_data:
        command += ['-L', str(args.qemu_data.resolve())]
    result = {'transport': 'native UEFI HTTP Boot', 'guest_disks': 0,
              'secure_boot': 'disabled', 'efi': str(args.efi), 'success': False}
    try:
        subprocess.run([args.vars_tool, '-i', str(args.vars), '--set-boot-uri', url,
                        '-o', str(variables)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        with stdout.open('wb') as output:
            process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT)
            try:
                code = process.wait(timeout=args.timeout)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
                code = -1
        text = serial.read_text(errors='replace') if serial.exists() else ''
        downloaded = bool(server and any(r['method'] == 'GET' and r['status'] == 200 and
                                        r['file'] == 'bootx64.efi' for r in server.records))
        displayed = 'PocketInstall boot successful' in text
        shutdown = code == 0 and 'PocketInstall: shutting down.' in text
        result.update(http_get=downloaded if server else 'check Android log',
                      displayed_success=displayed, shutdown=shutdown, qemu_exit=code,
                      success=displayed and shutdown and (downloaded or server is None))
    finally:
        if server:
            server.shutdown()
            server.server_close()
        (args.output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
    if not result['success']:
        print('FAILED: inspect serial.log/qemu.log. OVMF may omit HTTP Boot; do not substitute PXE.')
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
