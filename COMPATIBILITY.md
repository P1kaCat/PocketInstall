# Compatibility

| Requirement | Current support |
|:---|:---|
| Phone | Android 8 / API 26 or newer; no root required |
| PC firmware | UEFI x64 with PXE IPv4 and a usable Ethernet interface |
| Network | Phone and PC on the same private LAN; no guest/client isolation |
| Router | DHCP boot settings and TFTP serving, or a separately configured relay |
| Windows | Official x64 Windows 10/11 Home/Pro WIM/ESD or supported ISO |
| Linux | Debian 13 amd64, Xfce desktop or standard + SSH server |
| Internet | Required for media downloads; Debian PC also downloads packages |
| Secure Boot | Supplied iPXE/diagnostic loaders are unsigned; firmware policy may reject them |

ARM64, split SWM media and arbitrary ISO filesystem variants are not supported. Windows 11 hardware requirements are not bypassed. Unknown TPM/firmware information is not reported as confirmed compatibility. Windows 10 standard support has ended; users must assess licensing and support needs.

The app can identify candidate local network interfaces and observe HTTP activity. It cannot certify firmware boot support, correct router configuration, drivers, a complete installation or OOBE based on downloads alone.

Windows reached first boot on the development PC. The Debian installer started in a diskless VM. These observations do not certify all hardware or a full physical Linux installation. See [Validation](docs/VALIDATION.md).

USB tethering is experimental: the firmware must recognize the phone's USB network device and support the required boot method. A cable alone is not a bootable USB drive. See [USB guide](docs/USB_CABLE.md).
