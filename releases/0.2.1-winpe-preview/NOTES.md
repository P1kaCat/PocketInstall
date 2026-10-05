# PocketInstall 0.2.1 — automatic WinPE boot with Freebox

Adds automatic DHCP/iPXE chaining through exported pocketinstall.ipxe and the phone's stable boot entry. Configure the Freebox manually once; subsequent boots do not require session URLs to be typed on the PC.

WinPE startup is reported separately from file delivery. Network initialization and driver availability can delay or prevent the phone notification even when WinPE is visible. No installation/formatting is requested by the boot-only test.
