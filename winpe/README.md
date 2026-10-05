# WinPE environment

The app downloads/imports a separately prepared `PocketInstall-WinPE-x64.zip`; no Microsoft boot image is embedded in the APK. The bundle provides wimboot, Microsoft boot manager, BCD, SDI and WIM resources. Import validates structure, hashes and actual generated HTTP routes before declaring readiness.

Source links distinguish the [PocketInstall prepared ZIP](https://github.com/P1kaCat/PocketInstall/releases/tag/v3.2.0) from the [official Microsoft ADK/WinPE add-on](https://learn.microsoft.com/en-us/windows-hardware/get-started/adk-install). A link or checksum is not redistribution permission. Review applicable Microsoft terms before wider distribution.

## Preparing a bundle

Use a Windows preparation machine with the matching official ADK/WinPE add-on and required PowerShell/WMI/TPM components. Inspect `scripts/Build-WinPE.ps1`, `scripts/Package-WinPE.ps1` and their parameters. `scripts/verify_winpe_bundle.py` validates the resulting ZIP. Preserve third-party notices and provenance; do not include a full Windows install ISO in this bundle.

The current startup scripts are injected through HTTP. Initialization must wait for usable networking before startup reporting. A failed notification must not obscure a visibly successful WinPE boot.

## Installation is separate

An imported Windows image and enabled plan are required for deployment. The PC selects/verifies a disk and confirms erasure locally. The phone does not remotely authorize partitioning. See [Windows installation](../docs/WINDOWS_INSTALL.md), [Testing](../docs/TESTING.md) and [Licensing](../docs/LICENSING.md).
