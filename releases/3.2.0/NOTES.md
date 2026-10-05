# PocketInstall 3.2.0 — simplified interface

Prepare, Install and Help tabs; compact sections, wrapping choices, light/dark themes and expandable help. Long technical instructions move to help buttons; URLs/manual commands/logs remain in Diagnostics. Errors and local disk confirmation remain visible.

WinPE displays organized stages. DiskPart output is logged to `X:\PocketInstall-native.log`, then `W:\PocketInstall\native.log`; DISM retains real progress. Transfer completion is not reported as confirmed boot.

Install the matching APK and PXE reboot. The WinPE ZIP/snponly.efi remain unchanged. To replace the old repeated iPXE wait text, export pocketinstall.ipxe from Help and replace that file in the router's TFTP folder.

Validation included targeted routes/storage checks, Android build/lint and emulator navigation/help/light-dark/large-font/landscape checks. Physical terminal rendering and this release's full installation remained to be confirmed. The user's earlier 3.1.3 installation reached first boot, not guaranteed OOBE completion on all PCs.
