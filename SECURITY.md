# Security

## Local network boundary

- Bind only the selected private IPv4 interface; reject mobile/VPN/public candidates.
- Restrict clients to its subnet; loopback is allowed in local tests.
- Use a new random 128-bit session per server start.
- Expose only explicit GET/HEAD resources; no directory listing or arbitrary path access.
- Reject unexpected query strings, encodings, relative segments and write methods.
- Bound headers, connection count, read duration and local logs.
- Stop on user request, expiry after 30 minutes, or network loss/change.
- Keep URL tokens out of request logs. Do not configure Internet port forwarding.

HTTP/TFTP are not encrypted. A private LAN and token do not protect against a hostile LAN peer, spoofing, interception or resource modification. Tokens do not authenticate firmware or operating-system publishers.

## Downloads

Outbound media transfers use HTTPS with explicit host/path checks, bounded redirects and sizes, integrity checks and private storage. HEAD preflight reads no asset body. The user sees the size and source before accepting; the service rejects a changed length. The public WinPE ZIP requires no GitHub login or session cookie. Windows source-page interaction uses a restricted WebView, without a native JavaScript interface or file access.

Integrity recorded during import detects later corruption; it is not an independent publisher signature. Debian checksum files and Windows official source provenance must still be considered within their trust models.

## Disk operations

The diagnostic EFI uses no disk protocol. Windows deployment is a separate, explicitly destructive workflow: it requires disk selection and confirmation on the PC. A failed transfer after partitioning leaves the target disk erased. Resume mode validates its checkpoint and existing disk/partitions and must not clean or format again. Linux partitioning remains interactive on the PC.

Never infer that installing a new OS preserves old data. Back up first. PocketInstall does not unlock BitLocker, bypass activation or bypass Windows hardware requirements. Firmware changes can affect BitLocker recovery; retain recovery information before changing boot settings.

## Reporting

Report reproducible problems to the maintainer through the [official repository](https://github.com/P1kaCat/PocketInstall). Do not publish passwords, cookies, session URLs, recovery keys or private disk identifiers. No dedicated private vulnerability-reporting channel is declared here; public issues should omit sensitive exploit details.
