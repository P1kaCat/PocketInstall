# PocketInstall 3.1.1 — separate Windows and personal storage

- Fixes EFI validation when PowerShell cannot find `MSFT_Partition` by drive letter S, using GPT partition/volume identity instead.
- Adds C: for Windows/software and D: for the remaining disk, requiring at least 16 GiB for D: before erasure.
- Keeps EFI/MSR/recovery hidden and places recovery immediately after Windows.
- Optional Explorer hiding of C:, reversible through `D:\Afficher Windows.cmd` after signing out/in. Direct C: access remains possible.
- Personal folders/temporary files remain on C:; save files/games on D: yourself.
- Adds `REPRENDRE N` checkpoint-based resume before DISM image application, without cleaning/formatting existing partitions.

Install the matching APK and PXE reboot to load the new installer. Freebox/WinPE configuration is unchanged. Changing an existing layout requires another locally confirmed clean installation. Resume requires a matching checkpoint/image/layout and does not resume DISM already started.

Targeted GPT/volume/layout/resume checks and APK/script validation were performed. Physical WinPE boot was observed; full deployment and Explorer hiding across editions remained to be confirmed at publication.
