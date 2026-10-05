# Releases and validation

A release must identify its source commit, build result and validation scope. An HTTP download is not proof of OS startup. Preserve third-party notices and matching iPXE source archives.

For 3.4.1, the download-consent workflow builds the APK, runs server tests and Android lint, checks its signature and packages checksums. Publication uses the reviewed artifact from an identical Git tree. Existing release notes are translated to English without replacing historical binaries/tags.

## Before publishing

- Increase versionCode/versionName and write release notes.
- Check download consent for known/unknown/changed sizes and allowed redirects.
- Include `snponly.efi`, its corresponding source, notices and SHA256SUMS.
- Clearly distinguish unit/build checks, VM installer startup, deployment and physical first boot.
- Do not publish Windows ISOs or claim redistribution permission solely from source attribution.

The GitHub APK is a debug development build. Google Play requires a separately signed production AAB and upload-key configuration, privacy policy, data-safety/foreground-service declarations and review of Microsoft distribution terms. Never commit signing keys, passwords, cookies or private credentials.
