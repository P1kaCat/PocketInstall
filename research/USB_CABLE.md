# Direct USB networking feasibility

Historical research summary.

USB tethering provides a network interface, not USB storage emulation. Android must expose a compatible USB network function; firmware must recognize it and support booting over that link. Downstream DHCP and subsequent WinPE drivers are additional requirements. The app cannot promise that an arbitrary cable/phone/PC combination works. The practical current path uses Ethernet PXE and router configuration.

## Original primary references

- https://source.android.com/docs/core/ota/modular-system/tethering
- https://android.googlesource.com/platform/packages/modules/Connectivity/+/refs/heads/main/Tethering/src/com/android/networkstack/tethering/Tethering.java
- https://android.googlesource.com/platform/frameworks/base/+/2fffbcb7dfafdb61e2f0265e8265f66985c63147/packages/Tethering/res/values/config.xml
- https://developer.android.com/reference/java/net/NetworkInterface
- https://developer.android.com/reference/android/net/TetheringManager
- https://uefi.org/specs/UEFI/2.11/24_Network_Protocols_SNP_PXE_BIS.html
- https://learn.microsoft.com/en-us/surface/ethernet-adapters-and-surface-device-deployment
