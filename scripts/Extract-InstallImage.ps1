[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$IsoPath,
    [Parameter(Mandatory)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedIsoSha256,
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($env:OS -ne 'Windows_NT') { throw 'Run this preparation helper on Windows.' }
$iso = (Resolve-Path -LiteralPath $IsoPath).Path
$actual = (Get-FileHash -LiteralPath $iso -Algorithm SHA256).Hash
if ($actual -ne $ExpectedIsoSha256) { throw 'Official ISO hash mismatch. No extraction was performed.' }
if (Test-Path $OutputDirectory) { throw 'Choose a new output directory. Existing files are not replaced.' }
$previous = Get-DiskImage -ImagePath $iso
$mountedHere = !$previous.Attached
try {
    $image = if ($mountedHere) { Mount-DiskImage -ImagePath $iso -Access ReadOnly -PassThru } else { $previous }
    $volumes = @($image | Get-Volume | Where-Object DriveLetter)
    if ($volumes.Count -ne 1) { throw 'Expected a single mounted ISO volume.' }
    $root = "$($volumes[0].DriveLetter):\sources"
    $source = @('install.wim', 'install.esd') | ForEach-Object { Join-Path $root $_ } | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (!$source) { throw 'No install.wim or install.esd in this official media.' }
    New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
    $destination = Join-Path $OutputDirectory (Split-Path $source -Leaf)
    Copy-Item -LiteralPath $source -Destination $destination
    $info = & dism.exe '/Get-ImageInfo' "/ImageFile:$destination"
    if ($LASTEXITCODE -ne 0) { throw 'DISM could not inspect the extracted image.' }
    $info | Set-Content -LiteralPath (Join-Path $OutputDirectory 'image-info.txt') -Encoding UTF8
    [ordered]@{
        source = 'User-downloaded Microsoft ISO'; verifiedIsoSha256 = $actual.ToLowerInvariant()
        image = (Split-Path $destination -Leaf); bytes = (Get-Item $destination).Length
        extractedSha256 = (Get-FileHash $destination -Algorithm SHA256).Hash.ToLowerInvariant()
        editionIndexes = 'Inspect image-info.txt; do not hardcode Home/Pro index numbers.'
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory 'image-manifest.json') -Encoding UTF8
} finally {
    if ($mountedHere) { Dismount-DiskImage -ImagePath $iso }
}
