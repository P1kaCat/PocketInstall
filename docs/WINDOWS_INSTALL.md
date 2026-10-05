# Install Windows from PocketInstall

## On the phone

1. Download/import the validated WinPE ZIP. Source links distinguish the prepared PocketInstall ZIP from Microsoft ADK/WinPE.
2. Select Windows 10/11, Home/Pro and media language according to your license.
3. Obtain an official Microsoft ISO using the app. Review its actual size before confirming. Allow roughly twice the ISO size for download and extraction; additional device headroom may be needed.
4. Alternatively import official ISO/WIM/ESD using Android's file picker. Supported ISO9660/simple physical-partition UDF media are extracted locally. ARM64 and split SWM media are rejected.
5. Choose storage and optional debloat, enable installation on the next PXE boot, then start the WinPE server.
6. Boot the PC in Ethernet / UEFI PXE IPv4. Startup, networking and hardware reporting run automatically.

No full Windows ISO is hosted by PocketInstall. Source-host validation, length checks and integrity checks remain active. Without an independently supplied publisher checksum, an import hash only protects subsequent integrity. A failed import preserves the previous environment.

## On the PC

This is a **clean installation that erases the selected disk**, not an upgrade or file-preserving reinstall. Back up first.

WinPE displays internal disks, identity and size. Select a disk and type the exact on-screen confirmation, currently `EFFACER N`. The phone does not remotely approve erasure. Disk identity is reread before writing.

**Image transfer occurs after erasure/partitioning. A failed network or image can leave the disk erased.** Keep the phone powered and the network stable. The server expires after 30 minutes.

The installer prepares GPT, a 300 MiB FAT32 EFI partition, 16 MiB MSR, Windows NTFS and 2 GiB recovery. DISM applies the selected image; BCDBoot prepares UEFI boot and REAgentC registers WinRE. USB/SD media, offline/read-only disks and unsafe identities are rejected.

Restart on the internal disk, finish OOBE, install required drivers and activate with your Windows license. A startup callback during `specialize` is distinct from completed OOBE.

## Storage

Single-volume mode keeps Windows and user files on C:. Split mode keeps Windows/applications/temporary files on C: and gives the remaining space to D:, requiring at least 16 GiB for D:. EFI/MSR/recovery remain hidden. Recovery sits between C: and D:.

Automatic Windows sizing uses the selected edition's size, 10 GiB temporary headroom, 16 GiB update headroom, peak image staging and Windows minimum capacity, rounded up to 4 GiB. It is an estimate, not a temporary-file quota. Missing metadata requires a manual choice. RAM/pagefile, hibernation, updates and installed software can change usage.

Optional Explorer hiding does not remove C: or prevent direct paths. The generated `D:\Afficher Windows.cmd` can restore visibility for the current account after signing out/in. If D: conflicts with another volume, C: remains visible. Personal folders are not automatically relocated.

After an interrupted transfer, `REPRENDRE N` can resume only with a valid matching checkpoint, disk, image and partition layout. It mounts existing partitions without cleaning/formatting them. It does not resume an already-started DISM application. Do not erase again merely to bypass a rejected checkpoint.

## Debloat and compatibility

None removes nothing. Light removes provisioned Clipchamp, Solitaire, News and Weather. Custom selects these separately. Auto uses Light below 8 GiB RAM or at most two detected physical cores; otherwise None. Edition selection remains a licensing choice.

Defender, Windows Update, Microsoft Store and drivers are not disabled. Changes are logged in `C:\PocketInstall\debloat.json`; removed applications can be reinstalled through Store.

Known Windows 11 RAM/core/TPM failures are rejected; unknown information is not proof of compatibility. CPU support and firmware capabilities must also be checked. No activation or hardware-requirement bypass is provided. Windows 10 standard support has ended.

## Diagnostics and evidence

DISM logs live in `X:\Windows\Logs\DISM`; installation logs are in `W:\PocketInstall` when available, then `C:\PocketInstall` after normal boot. Expired tokens require a new server session/PXE boot.

Windows reached first boot on the development PC. That does not certify every edition, driver, machine or completed OOBE. See [Validation](VALIDATION.md).
