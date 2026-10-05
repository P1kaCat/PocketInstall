# UEFI PXE IPv4

The recommended current path is router TFTP → `snponly.efi` → `pocketinstall.ipxe` → phone HTTP `/boot.ipxe` → WinPE or Debian. See [Freebox setup](WINPE_FREEBOX.md).

## Router and port requirements

PXE firmware normally contacts UDP port 69. A non-root Android process may be unable to bind it and fall back to 6969. Firmware cannot simply use that alternate port. Serve the loader from the router or use a separately configured local relay; Internet port forwarding does not solve local PXE configuration.

The app does not assign DHCP addresses or administer your router. Phone and PC must share a private LAN; the PC uses Ethernet. Select UEFI PXE IPv4, not legacy PXE. Supplied loaders are unsigned.

## Diagnostic EFI

The older `bootx64.efi` is a disk-free proof of concept. Its success message is not Windows installation or WinPE. It accepts a key or shuts down after 30 seconds. Historical relay instructions concern this diagnostic path, not automatic OS deployment.

## Linux relay

A separate Linux host can provide proxy-DHCP/TFTP when a router lacks boot options. The app's advanced relay command requires the relay IP, Ethernet interface and target PC MAC. Follow the exact current command and inspect `scripts/` for its implementation. Avoid an unintended second DHCP address server.

## Diagnose

Verify DHCP lease, TFTP filename/root, phone IP/server state and same-LAN reachability. Check logs for iPXE contact and individual HTTP failures. Manual DHCP/chain commands remain debug tools; normal boot must not require typing session URLs.

For VM testing, distinguish loader delivery, EFI execution, installer startup, deployment and first boot. See [Testing](TESTING.md).
