#!/usr/bin/env python3
"""Run the Android server-core on JVM and prove native HTTP Boot in diskless QEMU.

Build with android/gradlew :server-core:installDist first. Firmware/QEMU arguments
are forwarded to qemu_http_boot.py. No Android service or device is simulated.
"""
import argparse
import json
import queue
import re
import subprocess
import sys
import threading
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, default=root / 'android/server-core/build/install/server-core/bin/server-core')
    parser.add_argument('--efi', type=Path, default=root / 'boot/build/bootx64.efi')
    parser.add_argument('--output', type=Path, default=Path('lab-output/kotlin-http'))
    args, firmware_args = parser.parse_known_args()
    if not args.server.is_file() or not args.efi.is_file():
        parser.error('Build the EFI and :server-core:installDist first.')
    if any(value in firmware_args for value in ('--external-url', '--output', '--efi')):
        parser.error('These options are managed by this wrapper.')
    args.output.mkdir(parents=True, exist_ok=True)
    messages = []
    ready = queue.Queue()
    process = subprocess.Popen([str(args.server.resolve()), str(args.efi.resolve()), '127.0.0.1', '8', '0'],
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)

    def read_output():
        for line in process.stdout:
            if line.startswith('BOOT_URL='):
                ready.put(line.strip().split('=', 1)[1])
                messages.append('BOOT_URL=<temporary session redacted>\n')
            else:
                messages.append(line)

    reader = threading.Thread(target=read_output, daemon=True)
    reader.start()
    try:
        try:
            address = urlsplit(ready.get(timeout=20))
        except queue.Empty:
            raise RuntimeError('JVM server did not start; see kotlin-http.log') from None
        # QEMU SLIRP exposes the host loopback through 10.0.2.2.
        guest_url = urlunsplit((address.scheme, f'10.0.2.2:{address.port}', address.path, '', ''))
        test = subprocess.run([sys.executable, str(root / 'scripts/qemu_http_boot.py'),
                               '--efi', str(args.efi), '--output', str(args.output),
                               '--external-url', guest_url, *firmware_args],
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        print(re.sub(r'/[0-9a-f]{32}/', '/<session>/', test.stdout))
        result = json.loads((args.output / 'result.json').read_text())
        length = args.efi.stat().st_size
        fetched = any(re.match(rf'FINISHED GET bootx64\.efi 200 {length}/{length} ', line) for line in messages)
        result.update(server='Android server-core on JVM', android_service_tested=False,
                      http_get=fetched, success=result['success'] and fetched and test.returncode == 0)
        (args.output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result, indent=2))
        return 0 if result['success'] else 1
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
        reader.join(timeout=2)
        (args.output / 'kotlin-http.log').write_text(''.join(messages))


if __name__ == '__main__':
    raise SystemExit(main())
