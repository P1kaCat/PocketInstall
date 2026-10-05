# PocketInstall 3.1.3 — DISM version parsing fix

Fixes `The property Build cannot be found` after DiskPart: WinPE Get-WindowsImage can return Version as text. The installer explicitly parses System.Version before Windows 10/11 validation; edition and architecture checks remain active. Missing/invalid versions stop image application.

Keep the same image/edition/layout, restart the phone server and PXE boot the PC to load the new script. No Freebox or ZIP change. For a failure before application, `REPRENDRE N` validates the checkpoint, disk, partition GUIDs/sizes and image and mounts existing partitions without erasing them. Do not erase again to bypass rejected resume.

Targeted version/partition/resume/HTTP checks and APK compilation were performed. A full physical installation was not certified at publication; first boot was subsequently observed by the user.
