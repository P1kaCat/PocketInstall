"""Boot the real Debian installer in a diskless VM, without partitioning any disk."""
import json, pathlib, subprocess, time, urllib.request
root = pathlib.Path('linux-lab')
root.mkdir(exist_ok=True)
lablog = (root/'server.log').open('w')
server = subprocess.Popen(['java','-cp','android/server-core/build/install/server-core/lib/*','app.pocketinstall.server.LinuxLab',str(root)],stdout=lablog,stderr=subprocess.STDOUT)
vm = None
try:
    deadline=time.monotonic()+150
    while not (root/'endpoint').exists():
        if server.poll() is not None: raise RuntimeError((root/'server.log').read_text())
        if time.monotonic()>deadline: raise TimeoutError('Debian download timed out')
        time.sleep(1)
    endpoint=(root/'endpoint').read_text()
    with (root/'console.log').open('w') as output:
        vm=subprocess.Popen(['qemu-system-x86_64','-machine','q35','-m','2048','-display','none','-serial','stdio','-no-reboot','-kernel',str(root/'linux'),'-initrd',str(root/'initrd.gz'),'-append',f'auto=true priority=high netcfg/choose_interface=auto url={endpoint} console=ttyS0,115200n8','-nic','user,model=e1000'],stdout=output,stderr=subprocess.STDOUT)
        deadline=time.monotonic()+180
        while not (root/'installer-started').exists():
            if vm.poll() is not None: raise RuntimeError('Debian VM exited before runtime callback')
            if time.monotonic()>deadline: raise TimeoutError('No Debian installer runtime callback; see console.log')
            time.sleep(1)
    (root/'result.json').write_text(json.dumps({'debian_installer_started':True,'guest_disks':0,'full_install_tested':False,'physical_boot_tested':False},indent=2)+'\n')
    print((root/'result.json').read_text())
finally:
    for process in [vm,server]:
        if process is not None:
            process.terminate()
            try: process.wait(timeout=5)
            except subprocess.TimeoutExpired: process.kill(); process.wait()
    lablog.close()
