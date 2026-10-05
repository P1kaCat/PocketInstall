# Technical feasibility

Historical research summary.

The practical path is a non-root Android local HTTP server and a PC with suitable UEFI network boot support. A router can provide DHCP/TFTP and iPXE handoff. Wi-Fi firmware support and reuse by later boot stages are device-dependent; Ethernet is the reliable initial path. DHCP gives an address, not OS startup. WinPE needs wimboot and BCD/SDI/WIM resources, its own drivers and applicable Microsoft terms. Firmware Secure Boot policy is separate from Windows hardware requirements. Installation requires verified media, local disk selection and explicit destructive confirmation. These initial research notes predate the current implementation; follow the current guides for supported behavior.

## Original primary references

- http://192.168.1.42:8080/<session
- https://uefi.org/specs/UEFI/2.11/24_Network_Protocols_SNP_PXE_BIS.html
- https://uefi.org/specs/UEFI/2.10/24_Network_Protocols_SNP_PXE_BIS.html
- https://uefi.org/specs/UEFI/2.10/02_Overview.html
- https://www.dell.com/support/manuals/en-us/bios-connect/https_ug/introduction-to-https-boot?guid=guid-dbc85161-a46f-4c9f-97bd-2134b37c0dce&lang=en-us
- https://uefi.org/specs/UEFI/2.11/13_Protocols_Media_Access.html
- https://github.com/tianocore/edk2/blob/edk2-stable202605/NetworkPkg/HttpBootDxe/HttpBootDhcp4.c
- https://ipxe.org/appnote/uefihttp
- https://ipxe.org/appnote/wimboot_architecture
- https://ipxe.org/howto/winpe
- https://ipxe.org.
- https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/winpe-network-drivers-initializing-and-adding-drivers?view=windows-11
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/privacy-and-security/local-network-permission
- https://developer.android.com/training/monitoring-device-state/doze-standby
- https://uefi.org/specsandtesttools
- https://support.microsoft.com/en-us/servicing/os/windows/2025/02/updating-windows-bootable-media-to-use-the-pca2023-signed-boot-manager
- https://ipxe.org/secboot
- https://learn.microsoft.com/en-us/windows/deployment/configure-a-pxe-server-to-load-windows-pe
- https://learn.microsoft.com/en-us/windows-hardware/get-started/adk-install
- https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/winpe-intro?view=windows-11
- https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/winpe-create-usb-bootable-drive?view=windows-11
- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://kotlinlang.org/docs/gradle-configure-project.html
- https://www.microsoft.com/en-us/software-download/windows11
- https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/dism-image-management-command-line-options-s14?view=windows-11
- https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/bcdboot-command-line-options-techref-di?view=windows-11
- https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/configure-uefigpt-based-hard-drive-partitions?view=windows-11
- https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/dism-unattended-servicing-command-line-options?view=windows-11
- https://github.com/tianocore/edk2/blob/edk2-stable202605/NetworkPkg/Library/DxeNetLib/DxeNetLib.inf
