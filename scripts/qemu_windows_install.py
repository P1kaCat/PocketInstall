#!/usr/bin/env python3
"""Deploy to one disposable QCOW2 disk, then cold boot the installed OS.
Only this test types the disk confirmation. Production never does so automatically.
Success requires the installed Windows specialize callback, not DISM or HTTP alone.
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


def qmp(out, command, arguments=None):
    with socket.socket(socket.AF_UNIX) as sock:
        sock.settimeout(10); sock.connect(str(out / 'qmp.sock')); stream=sock.makefile('rwb')
        stream.readline(); stream.write(b'{"execute":"qmp_capabilities"}\n'); stream.flush()
        while 'return' not in json.loads(stream.readline()): pass
        stream.write((json.dumps({'execute':command,'arguments':arguments or {}})+'\n').encode()); stream.flush()
        while True:
            response=json.loads(stream.readline())
            if 'error' in response: raise RuntimeError(str(response['error']))
            if 'return' in response: return response['return']


def screenshot(out, name):
    qmp(out,'screendump',{'filename':str(out / (name+'.ppm'))})
    from PIL import Image
    Image.open(out / (name+'.ppm')).save(out / (name+'.png'))


def type_vm_only(out, text):
    for character in text:
        key='ret' if character=='\n' else 'spc' if character==' ' else 'shift-'+character.lower() if character.isupper() else character
        qmp(out,'human-monitor-command',{'command-line':f'sendkey {key}'})
        time.sleep(.12)


def stop(process):
    if process is not None and process.poll() is None:
        process.terminate()
        try: process.wait(timeout=10)
        except subprocess.TimeoutExpired: process.kill(); process.wait()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--bundle',type=Path,required=True); p.add_argument('--image',type=Path,required=True)
    p.add_argument('--output',type=Path,default=Path('windows-vm')); p.add_argument('--timeout',type=int,default=1500)
    a=p.parse_args(); out=a.output.resolve(); out.mkdir(parents=True,exist_ok=False)
    bundle=out/'bundle'; bundle.mkdir()
    with zipfile.ZipFile(a.bundle) as z:
        if any('/' in name or '\\' in name or name in ('.','..') for name in z.namelist()): raise ValueError('Non-flat bundle')
        z.extractall(bundle)
    tftp=out/'tftp'; tftp.mkdir(); shutil.copyfile(bundle/'snponly.efi',tftp/'snponly.efi')
    (tftp/'pocketinstall.ipxe').write_text('#!ipxe\n:retry\nchain http://10.0.2.2:8080/boot.ipxe || goto retry\n')
    shutil.copyfile('/usr/share/OVMF/OVMF_VARS_4M.fd',out/'vars.fd')
    # No user disk/device/host directory is ever attached.
    disk=out/'disposable.qcow2'; subprocess.run(['qemu-img','create','-f','qcow2',str(disk),'80G'],check=True)
    (out/'tpm').mkdir()
    accel='kvm' if os.access('/dev/kvm',os.R_OK|os.W_OK) else 'tcg'
    common=['qemu-system-x86_64','-machine','q35','-accel',accel,'-cpu','max','-m','4096','-smp','2',
            '-drive','if=pflash,format=raw,readonly=on,file=/usr/share/OVMF/OVMF_CODE_4M.fd',
            '-drive',f'if=pflash,format=raw,file={out/"vars.fd"}',
            '-chardev',f'socket,id=chrtpm,path={out/"tpm.sock"}', '-tpmdev','emulator,id=tpm0,chardev=chrtpm','-device','tpm-tis,tpmdev=tpm0',
            '-display','none','-vga','std','-serial',f'file:{out/"serial.log"}',
            '-qmp',f'unix:{out/"qmp.sock"},server=on,wait=off']
    def launch(phase, log):
        network=['-netdev',f'user,id=lan,tftp={tftp},bootfile=snponly.efi','-device',f'e1000e,netdev=lan,bootindex={1 if phase=="pxe" else 2}']
        storage=['-drive',f'if=none,id=os,format=qcow2,file={disk}','-device',f'ide-hd,drive=os,bus=ide.0,serial=POCKETINSTALL-VM-ONLY-001,bootindex={2 if phase=="pxe" else 1}']
        return subprocess.Popen(common+network+storage+['-boot',f'order={"n" if phase=="pxe" else "c"},strict=on'],stdout=log,stderr=subprocess.STDOUT)
    server=vm=tpm=None; result={'success':False,'guest_disks':1,'disposable_disk':True,'windows_runtime_callback':False,'physical_pc_validated':False}
    deadline=time.monotonic()+a.timeout
    def wait_file(name):
        while not (out/name).exists():
            if (out/'report-error').exists(): raise RuntimeError((out/'report-error').read_text())
            if vm is not None and vm.poll() is not None: raise RuntimeError('VM exited before '+name)
            if server is not None and server.poll() is not None: raise RuntimeError('HTTP server exited')
            if time.monotonic()>deadline: raise TimeoutError('No runtime proof: '+name)
            time.sleep(1)
    try:
        with (out/'server.log').open('wb') as slog, (out/'qemu.log').open('ab') as qlog, (out/'tpm.log').open('wb') as tlog:
            tpm=subprocess.Popen(['swtpm','socket','--tpm2','--tpmstate',f'dir={out/"tpm"}','--ctrl',f'type=unixio,path={out/"tpm.sock"}','--flags','not-need-init'],stdout=tlog,stderr=subprocess.STDOUT)
            server=subprocess.Popen(['java','-cp','android/server-core/build/install/server-core/lib/*','app.pocketinstall.server.WinPeVmMainKt',str(bundle),str(out),str(a.image.resolve())],stdout=slog,stderr=subprocess.STDOUT)
            wait_file('server-ready'); vm=launch('pxe',qlog)
            wait_file('report-inventory')
            hardware=json.loads((out/'report-inventory').read_text())['hardware']
            if hardware['ramBytes'] < 4 * 1024**3 or hardware.get('ramSource') != 'SMBIOS' or '2.0' not in hardware['tpm']:
                raise RuntimeError('The emulated 4 GiB/TPM 2.0 hardware was not detected: '+json.dumps(hardware))
            wait_file('report-awaiting-disk'); screenshot(out,'disk-choice'); time.sleep(1); type_vm_only(out,'0\n')
            wait_file('report-awaiting-confirmation'); screenshot(out,'erase-review'); time.sleep(1); type_vm_only(out,'EFFACER 0\n')
            wait_file('report-prepared'); screenshot(out,'windows-applied'); stop(vm); vm=None
            vm=launch('disk',qlog)
            wait_file('report-windows-started'); screenshot(out,'windows-started')
            result.update(success=True,windows_runtime_callback=True,winpe_runtime_callback=(out/'winpe-started').exists(),
                          image_sha256=__import__('hashlib').file_digest(a.image.open('rb'),'sha256').hexdigest())
    except Exception as error: result['error']=str(error)
    finally:
        if vm is not None and vm.poll() is None:
            try: screenshot(out,'final-screen')
            except Exception as error: result['screenshot_error']=str(error)
        stop(vm); stop(server); stop(tpm)
        (out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2)); return 0 if result['success'] else 1


if __name__=='__main__': raise SystemExit(main())
