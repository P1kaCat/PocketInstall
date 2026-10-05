# iPXE handoff

The supplied `snponly.efi` is a separate GPL-licensed iPXE program. Its corresponding source archive and notices accompany the release. Do not confuse it with the diagnostic `bootx64.efi`.

A configured router supplies iPXE and the exported `pocketinstall.ipxe`. The script runs DHCP, contacts the phone's stable `/boot.ipxe` and receives the current WinPE/Debian session automatically. Tokens are not typed by users.

WinPE uses wimboot and required Microsoft BCD/SDI/WIM/boot-manager resources. Debian uses its official kernel/initrd. Missing or invalid assets must prevent a ready state. HTTP delivery is not proof of runtime startup.

The loader is unsigned. Firmware, Ethernet support and subsequent installer NIC drivers remain independent requirements. See [Freebox setup](../../docs/WINPE_FREEBOX.md).
