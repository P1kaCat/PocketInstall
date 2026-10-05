# Observed validation

## Scope

- The original diagnostic EFI reached its success message through native HTTP/PXE boot in isolated tests.
- Android/shared-server build and bounded protocol tests have been recorded in historical evidence.
- WinPE startup has been observed on the physical development PC, including runtime reporting after networking became available.
- Windows installation reached first boot on the development PC; this does not certify every edition, hardware configuration or OOBE completion.
- Debian's actual installer started in a diskless VM. The production Linux boot arguments and HTTP routes were used, with a runtime early callback as proof. The guest had no installation disk.
- Version 3.4.1 adds focused download metadata/consent checks and build/lint validation. It does not claim a new full Linux installation or all-device UI test.

## Historical records

`docs/evidence/` retains original logs, reports and machine-readable results; these are evidence, not translated product documentation. Reports must be associated with their original commit/version. Old diagnostic success must not be presented as WinPE or Windows installation success.

## Still to verify

Complete Linux installation and physical cold boot; broader Windows edition/firmware/NIC coverage; first-boot callbacks when the phone session expires; reduced-animation/large-font interactions on representative Android devices; production signing and Google Play submission.

A completed download, image apply operation or callback at one stage is never substituted for later-stage proof. A source checksum is not an independent publisher signature.
