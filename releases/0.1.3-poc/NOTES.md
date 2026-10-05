# PocketInstall 0.1.3 — UEFI PXE IPv4

Adds TFTP delivery and UEFI PXE IPv4 diagnostics. Non-root Android may fall back from UDP 69 to 6969; direct firmware PXE cannot use that alternate port without a suitable local relay/router setup. The app does not allocate DHCP addresses or configure the router.

Use an Ethernet-connected UEFI x64 PC. Success means the diagnostic EFI ran, not WinPE or Windows installation. Boot testing uses diskless guests; supplied loaders are unsigned.
