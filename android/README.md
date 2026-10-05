# Android application

Native Jetpack Compose app, minimum API 26, compile/target API 36, JDK 17. Four tabs: Prepare, Install, Library and Help. AppCompat preserves six language selections; logo and adaptive icons are included.

```sh
bash gradlew :server-core:test :app:assembleDebug :app:lintDebug
```

Provide the diagnostic EFI/iPXE assets and corresponding notices before packaging; see the root [README](../README.md) and [Releasing](../docs/RELEASING.md).

`server-core` has no Android dependency. The app adapter manages private storage, Storage Access Framework imports, notifications, wake locks, selected LAN and bounded sessions. Download services are user-started foreground dataSync services; PXE serving uses connectedDevice. No global storage access permission or Android self-update mechanism is used.

Windows/WinPE/Debian downloads require source/size confirmation. Public WinPE download does not require GitHub authentication. A changed or unknown size blocks downloading. Retained images can be removed through Library while transfers/server are stopped.

The supplied APK is a debug build, not a signed Google Play artifact. Proprietary terms apply only to original elements expressly covered by [LICENSE](../LICENSE); third-party and earlier grants remain intact.
