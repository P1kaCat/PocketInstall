[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BundleDirectory,
    [Parameter(Mandatory)][string]$Wimboot,
    [Parameter(Mandatory)][string]$WimbootSha256,
    [Parameter(Mandatory)][string]$Ipxe,
    [Parameter(Mandatory)][string]$IpxeSha256,
    [Parameter(Mandatory)][string]$OutputZip
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$output = [IO.Path]::GetFullPath($OutputZip)
if (Test-Path -LiteralPath $output) { throw 'Output already exists; choose a new ZIP.' }
foreach ($pair in @(@($Wimboot, $WimbootSha256), @($Ipxe, $IpxeSha256))) {
    if ($pair[1] -notmatch '^[a-fA-F0-9]{64}$') { throw 'Expected SHA-256 required for both downloaded loaders.' }
    if ((Get-FileHash -LiteralPath $pair[0] -Algorithm SHA256).Hash -ine $pair[1]) { throw 'Downloaded loader SHA-256 mismatch.' }
}
$stage = Join-Path ([IO.Path]::GetTempPath()) ('PocketInstall-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stage | Out-Null
try {
    foreach ($name in @('boot.wim', 'boot.sdi', 'BCD', 'bootmgfw.efi')) {
        Copy-Item -LiteralPath (Join-Path $BundleDirectory $name) -Destination (Join-Path $stage $name)
    }
    Copy-Item -LiteralPath $Wimboot -Destination (Join-Path $stage 'wimboot')
    Copy-Item -LiteralPath $Ipxe -Destination (Join-Path $stage 'snponly.efi')
    $resources = @()
    [long]$total = 0
    foreach ($name in @('boot.wim', 'boot.sdi', 'BCD', 'bootmgfw.efi', 'wimboot', 'snponly.efi')) {
        $file = Join-Path $stage $name
        [long]$size = (Get-Item -LiteralPath $file).Length
        [long]$limit = if ($name -eq 'boot.wim') { 2GB - 64MB } else { 16MB }
        if ($size -le 0 -or $size -gt $limit) { throw "Invalid size: $name" }
        $total += $size
        $resources += [ordered]@{ name = $name; bytes = $size; sha256 = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() }
    }
    $manifest = [ordered]@{ kind = 'pocketinstall-winpe-bundle-v1'; architecture = 'x64'; installsWindows = $false; resources = $resources } | ConvertTo-Json -Depth 6
    [IO.File]::WriteAllText((Join-Path $stage 'manifest.json'), $manifest, [Text.UTF8Encoding]::new($false))
    if ($total + (Get-Item (Join-Path $stage 'manifest.json')).Length -gt 2GB) { throw 'Bundle exceeds 2 GiB.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::CreateFromDirectory($stage, $output)
    Write-Host "Import this ZIP in PocketInstall: $output"
} catch {
    Remove-Item -LiteralPath $output -ErrorAction SilentlyContinue
    throw
} finally {
    Remove-Item -LiteralPath $stage -Recurse -Force
}
