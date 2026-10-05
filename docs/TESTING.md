# Testing

## Build and focused checks

Use JDK 17, Android SDK 36 and Build Tools 36.0.0. Build the EFI asset with GNU-EFI and provide the release's iPXE loader and notices before packaging.

```sh
make -C boot
python3 scripts/sync_android_boot.py
cd android
bash gradlew :server-core:test :app:assembleDebug :app:lintDebug
```

Server tests cover HTTP/TFTP bounds, WinPE import/routes/progress, Windows media parsing/sizing, Debian resources/progress and download preflight. Metadata tests ensure HEAD never reads an asset body, invalid sizes block, off-source redirects are rejected and changed sizes fail before image creation.

## Download UI

For Windows, WinPE and Debian: open confirmation, observe size/source, cancel and verify no asset transfer. Repeat and accept. Test unknown Content-Length, missing assets, changed lengths, cancellation, offline state and insufficient space. Check all six locales and large-font/landscape scrolling. Actual publisher endpoints can change; successful mocks do not prove future upstream behavior.

## Boot and deployment

Use separate disposable/diskless environments for boot testing and disposable guest disks for deployment. Existing scripts include `scripts/test_linux_boot.py`, `scripts/qemu_winpe_boot.py` and `scripts/qemu_windows_install.py`; inspect their current arguments before use.

Debian startup evidence requires the installer's early runtime callback, not kernel/initrd delivery. Installation completion requires its late callback; physical first boot is a separate observation. WinPE startup likewise requires its runtime signal. Windows deployment and first boot must be recorded separately from WIM delivery.

Never rerun a destructive install on a physical disk merely to test the UI. For a physical test, record OS/edition, firmware/Secure Boot state, NIC/driver, network transport, source/version, consent and observed runtime result. Omit personal identifiers and session tokens from public evidence.

Historical evidence remains in `docs/evidence/`. It proves only the version and scope originally tested. See [Validation](VALIDATION.md).
