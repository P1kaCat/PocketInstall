# USB cable networking — experimental

A non-root Android phone is not emulated as a bootable USB mass-storage drive. PocketInstall can use manually enabled USB tethering as a network transport if PC firmware recognizes that USB network device and supports the necessary boot protocol.

Enable tethering in Android settings, select the resulting private IPv4 interface in the app and start the local server. Network disappearance stops the session. Manufacturer USB functions, downstream DHCP, UEFI drivers and firmware boot support are independent prerequisites that the app cannot certify.

Test the diagnostic EFI with no guest disk before attempting any deployment. HTTP activity proves contact, not boot. WinPE requires its own network drivers after firmware handoff.

Ethernet PXE through a configured router is the supported practical path. Plugging in a cable alone does not provide PXE, a USB storage image or guaranteed installation. See [Compatibility](../COMPATIBILITY.md).
