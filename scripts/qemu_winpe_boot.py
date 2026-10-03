#!/usr/bin/env python3
"""Diskless OVMF PXE -> automatic iPXE -> production Kotlin HTTP -> wimboot -> WinPE.
Success requires a callback executed inside WinPE after wpeinit, never a WIM GET.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time
import zipfile


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--bundle', type=Path, required=True)
    p.add_argument('--server-command', required=True, help='JSON array, containing {bundle} and {output} placeholders')
    p.add_argument('--output', type=Path, default=Path('winpe-vm'))
    p.add_argument('--qemu', default='qemu-system-x86_64')
    p.add_argument('--code', type=Path, default=Path('/usr/share/OVMF/OVMF_CODE_4M.fd'))
    p.add_argument('--vars', type=Path, default=Path('/usr/share/OVMF/OVMF_VARS_4M.fd'))
    p.add_argument('--timeout', type=int, default=600)
    a = p.parse_args()
    out = a.output.resolve(); out.mkdir(parents=True, exist_ok=False)
    bundle = out / 'bundle'; bundle.mkdir()
    # verify_winpe_bundle.py is run separately before extraction; this test accepts only flat names.
    with zipfile.ZipFile(a.bundle) as z:
        for n in z.namelist():
            if '/' in n or '\\' in n or n in ('.','..'): raise ValueError('Non-flat bundle')
        z.extractall(bundle)
    tftp = out / 'tftp'; tftp.mkdir()
    shutil.copyfile(bundle / 'snponly.efi', tftp / 'snponly.efi')
    (tftp / 'pocketinstall.ipxe').write_text('#!ipxe\n:retry\nchain http://10.0.2.2:8080/boot.ipxe || goto waiting\nexit\n:waiting\nsleep 3\ngoto retry\n')
    shutil.copyfile(a.vars, out / 'vars.fd')
    accel = 'kvm' if os.access('/dev/kvm', os.R_OK | os.W_OK) else 'tcg'
    cmd = [a.qemu, '-machine', 'q35', '-accel', accel, '-cpu', 'max', '-m', '4096', '-smp', '2',
           '-drive', f'if=pflash,format=raw,readonly=on,file={a.code.resolve()}',
           '-drive', f'if=pflash,format=raw,file={out / "vars.fd"}',
           '-netdev', f'user,id=lan,tftp={tftp},bootfile=snponly.efi',
           '-device', 'e1000,netdev=lan,romfile=', '-boot', 'order=n,strict=on',
           '-display', 'none', '-vga', 'std', '-serial', f'file:{out / "serial.log"}',
           '-qmp', f'unix:{out / "qmp.sock"},server=on,wait=off', '-no-reboot']
    server = qemu = None
    result = dict(success=False, guest_disks=0, automatic_dhcp=True, automatic_chain=True,
                  kotlin_http_server=True, accelerator=accel, physical_pc_validated=False)
    try:
        sc = [x.replace('{bundle}',str(bundle)).replace('{output}',str(out)) for x in json.loads(a.server_command)]
        with (out / 'server.log').open('wb') as slog, (out / 'qemu.log').open('wb') as qlog:
            server = subprocess.Popen(sc,stdout=slog,stderr=subprocess.STDOUT)
            deadline = time.monotonic()+30
            while not (out / 'server-ready').exists():
                if server.poll() is not None or time.monotonic()>deadline: raise RuntimeError('Kotlin server did not start')
                time.sleep(.2)
            qemu = subprocess.Popen(cmd,stdout=qlog,stderr=subprocess.STDOUT)
            deadline = time.monotonic()+a.timeout
            while not (out / 'winpe-started').exists():
                if qemu.poll() is not None: raise RuntimeError('VM exited before WinPE runtime callback')
                if time.monotonic()>deadline: raise TimeoutError('No WinPE runtime callback; HTTP downloads are not proof of boot')
                time.sleep(1)
            result.update(success=True, winpe_runtime_callback=True)
    except Exception as e:
        result['error'] = str(e)
    finally:
        if qemu is not None and qemu.poll() is None:
            try:
                with socket.socket(socket.AF_UNIX) as sock:
                    sock.settimeout(5); sock.connect(str(out / 'qmp.sock')); f=sock.makefile('rwb')
                    f.readline(); f.write(b'{"execute":"qmp_capabilities"}\n'); f.flush(); f.readline()
                    f.write((json.dumps({'execute':'screendump','arguments':{'filename':str(out / 'screen.ppm')}})+'\n').encode()); f.flush(); f.readline()
                if (out / 'screen.ppm').exists():
                    from PIL import Image
                    Image.open(out / 'screen.ppm').save(out / 'screen.png')
            except Exception as e: result['screenshot_error']=str(e)
            qemu.terminate()
            try: qemu.wait(timeout=10)
            except subprocess.TimeoutExpired: qemu.kill(); qemu.wait()
        if server is not None:
            server.terminate()
            try: server.wait(timeout=10)
            except subprocess.TimeoutExpired: server.kill(); server.wait()
        (out / 'result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))
    return 0 if result['success'] else 1

if __name__ == '__main__': raise SystemExit(main())
