# PocketInstall 3.4.1 — download consent and source links

- Shows the current exact file sizes and total before Windows ISO, WinPE ZIP and Debian boot downloads.
- Uses HTTPS HEAD metadata checks without downloading asset bodies. Unknown, invalid or changed sizes block transfer and require a fresh confirmation.
- Adds links to the prepared PocketInstall ZIP release and the original Microsoft ADK/WinPE source. Attribution does not grant redistribution rights.
- Downloads the public WinPE bundle without GitHub login/session cookies; existing manual import remains available.
- Translates repository presentation, documentation, contribution templates, license and release notes to English. Keeps the app's six languages and adds localized confirmation copy.

Install PocketInstall-3.4.1.apk. Existing Freebox, snponly.efi and WinPE setup stay valid. Stop the server to prepare new downloads; review the source/size and confirm. Linux package downloads on the PC are separate from the displayed phone boot-file total.

Validation: focused HEAD/redirect/unknown-size/changed-size tests, server tests, Android build and lint. No new full physical Linux installation is claimed. This is a GitHub development APK, not a signed production Google Play AAB.
