# Blueprint only: no DiskPart, DISM Apply or BCDBoot execution is implemented.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateRange(0, 1024)][int]$DiskNumber,
    [Parameter(Mandatory)][string]$ImagePath,
    [Parameter(Mandatory)][ValidateRange(1, 10000)][int]$ImageIndex,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$ExpectedImageSha256
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$image = (Resolve-Path -LiteralPath $ImagePath).Path
if ([IO.Path]::GetExtension($image) -notin @('.wim', '.esd')) { throw 'Expected a local WIM/ESD, not an HTTP URL.' }
if ((Get-FileHash -LiteralPath $image -Algorithm SHA256).Hash -ne $ExpectedImageSha256) { throw 'Image hash mismatch.' }
$disk = Get-Disk -Number $DiskNumber
[ordered]@{
    schemaVersion = 1
    executable = $false
    reason = 'PocketInstall POC has no destructive deployment executor.'
    disk = [ordered]@{
        number = $disk.Number; model = $disk.FriendlyName; serial = $disk.SerialNumber
        uniqueId = $disk.UniqueId; bytes = $disk.Size
    }
    image = [ordered]@{ path = $image; index = $ImageIndex; sha256 = $ExpectedImageSha256 }
    requiredLocalConfirmation = 'ERASE'
    requiredPreflight = @('independent trusted hash', 'real image index/architecture', 'backup completed',
        'sufficient staging space', 'Windows 11 requirements', 'revalidate disk identity before first write')
    plannedStages = @('Download and verify image before writes', 'Confirm ERASE locally on Recovery PC',
        'GPT: ESP FAT32 >=300 MiB, MSR 16 MiB, Windows NTFS, dynamically sized Recovery',
        'DISM /Apply-Image from local staging', 'BCDBoot for intended ESP and firmware policy',
        'Configure WinRE', 'Apply supported unattend/offline configuration', 'Reboot and verify boot/OOBE')
} | ConvertTo-Json -Depth 6
