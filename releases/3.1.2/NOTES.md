# PocketInstall 3.1.2 — automatic Windows capacity

Split storage now estimates C: automatically instead of reserving a fixed 128 GiB. It takes the maximum of selected-edition size + 10 GiB temporary + 16 GiB update headroom; edition + temporary WIM/ESD + 2 GiB staging headroom; and Windows minimum capacity (64 GiB for Windows 11, 32 GiB for Windows 10), rounded up to 4 GiB.

D: gets the remainder with at least 16 GiB. This is a capacity estimate, not a temporary-file quota or guaranteed future free space. WinPE rechecks the plan and DISM metadata. Missing metadata requires manual sizing; undersized manual choices are rejected.

Use the matching APK; no Freebox/WinPE ZIP changes. PXE reboot loads the current installer. Targeted sizing/metadata/layout checks and APK compilation passed; final physical space usage remained to be confirmed at publication.
