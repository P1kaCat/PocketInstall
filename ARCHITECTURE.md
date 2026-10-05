# Architecture

## Runtime components

| Component | Responsibility |
|:---|:---|
| Android / Jetpack Compose | OS selection, download consent, private storage, library, help and progress |
| Foreground download services | Explicitly approved Windows ISO, WinPE ZIP and Debian boot downloads, notifications and cancellation |
| Shared Kotlin `server-core` | Bounded HTTP/TFTP serving, boot scripts, media validation and runtime progress |
| iPXE / wimboot | PC firmware handoff and WinPE RAM boot |
| Windows PowerShell scripts | Local disk confirmation, GPT layout, image transfer, DISM deployment and UEFI boot configuration |
| Debian installer | Account, disk and installation decisions on the PC |

## Automatic PXE workflow

The router supplies `snponly.efi`. iPXE runs the exported `pocketinstall.ipxe`, performs DHCP and contacts the phone's stable `/boot.ipxe` entry. That entry serves the selected environment's session-specific script. Tokens are regenerated each session and are transparent to the user. Router setup remains manual.

WinPE loads wimboot, boot manager, BCD, SDI, WIM and startup files. Debian loads its official kernel/initrd and a generated preseed that leaves account and disk choices interactive. Both report startup through runtime callbacks; HTTP asset requests alone do not establish boot success.

## Download consent and storage

`DownloadMetadata` uses allowlisted HTTPS HEAD requests to retrieve current content lengths without reading asset bodies. The confirmation lists individual files, their total and source links. Download services receive approved byte counts; an unknown or changed length blocks the payload. SHA-256 and format validation still run after delivery.

Windows ISOs use Microsoft hosts. WinPE uses the public, pinned PocketInstall GitHub ZIP and its SHA-256. Debian uses its official mirror and SHA256SUMS. Windows import uses the Storage Access Framework; all retained files are stored privately. Atomic import preserves an existing environment when validation fails.

## Local server

The server binds an explicitly selected private IPv4 interface. It accepts same-subnet clients, GET/HEAD and a finite resource list. Requests, concurrency, read times and logs are bounded. Byte ranges support image transfers. Sessions expire after 30 minutes and stop on network loss/change or user request.

HTTP/TFTP are plaintext local transports. The session token is not encryption or firmware authentication. See [Security](SECURITY.md).

## Deployment boundary

Selecting an OS on the phone does not authorize disk erasure remotely. Windows disk identity and destructive confirmation are checked on the PC. Linux disk choices remain in Debian's installer. The separate diagnostic EFI does not install an OS or access disk protocols.

Direct USB mass-storage emulation is not provided. Experimental USB tethering is a network transport and additionally needs compatible PC firmware. See [USB](docs/USB_CABLE.md).
