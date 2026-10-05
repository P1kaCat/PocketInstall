# Diagnostic EFI

This small x64 EFI proof of concept displays PocketInstall success and shuts down after a key press or 30 seconds. It does not open disk protocols, install an OS or load WinPE.

Build with GNU-EFI:

```sh
make -C boot
python3 scripts/sync_android_boot.py
```

The Android asset's hash and identity must match the build. Native HTTP/PXE success proves the diagnostic loader executed, not OS installation. The image is unsigned; Secure Boot policy may reject it. GNU-EFI notices remain applicable.

See [iPXE](ipxe/README.md), [PXE](../docs/PXE.md) and [Testing](../docs/TESTING.md) for subsequent stages.
