# PocketInstall with Freebox Revolution

## One-time setup

1. Reserve a stable local IP for the phone in Freebox DHCP settings.
2. Enable the Freebox TFTP server and select a dedicated folder, for example `/Disque dur/PocketInstall`.
3. Put the release's `snponly.efi` and the app's exported `pocketinstall.ipxe` in that folder.
4. Set the DHCP TFTP server to the **Freebox LAN IP**, commonly `192.168.0.254`, and the boot filename to **snponly.efi**.
5. Connect the PC over Ethernet and select **UEFI PXE IPv4**, not legacy PXE.

The phone's IP, such as `192.168.0.35`, is the HTTP server address in the exported script. It is not the TFTP server address when the Freebox serves the files. No Internet port forwarding is needed.

If your PC already starts PocketInstall automatically, keep these settings. Re-export `pocketinstall.ipxe` if the phone IP changes. Setup remains manual; the app does not administer the Freebox.

## Every boot

Choose Windows or Debian on the phone. Review the actual media size and source before confirming the download. Windows needs the separate WinPE environment and official installation image; Debian does not.

Start the phone server, then boot the PC via PXE. iPXE performs DHCP and calls the phone's `/boot.ipxe`, which provides the current session automatically. No token or command needs to be typed on the PC.

## Progress and diagnostics

A file GET or completed WIM transfer is not proof of boot. WinPE/Debian must send a runtime startup signal. First Windows boot is a separate state; OOBE completion must still be observed on the PC.

Diagnostics retain IPs, HTTP activity and manual commands. If the PC cannot contact the phone, check server state, reserved phone IP, client isolation and the same LAN. If WinPE starts without networking, check its NIC driver and network initialization. HTTP sessions expire after 30 minutes.

The supplied loaders are unsigned. Firmware Secure Boot policy may reject them. This guide does not bypass Windows requirements or activate Windows.
