#requires -RunAsAdministrator
[CmdletBinding()]
param(
    [string]$WorkDirectory = 'C:\PocketInstall-WinPE',
    [string]$OutputDirectory = 'C:\PocketInstall-Bundle',
    [switch]$WithPowerShell,
    [switch]$UseBootEx,
    [string]$DriverDirectory
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($env:OS -ne 'Windows_NT') { throw 'A Windows technician machine and Microsoft ADK are required.' }

function Invoke-Checked([string]$Program, [string[]]$ArgumentList) {
    & $Program @ArgumentList
    if ($LASTEXITCODE -ne 0) { throw "$Program exited with $LASTEXITCODE" }
}
function Safe-Path([string]$Path) {
    if ($Path -match '[&|<>^%!"\r\n]') { throw 'Use a plain local build path without shell metacharacters.' }
    return [IO.Path]::GetFullPath($Path)
}

$work = Safe-Path $WorkDirectory
$output = Safe-Path $OutputDirectory
if (Test-Path $work) { throw "Build directory already exists: $work. Choose a new directory; nothing is deleted automatically." }
if (Test-Path $output) { throw "Output directory already exists: $output. Choose a new directory." }
$adk = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10\Assessment and Deployment Kit'
$deployment = Join-Path $adk 'Deployment Tools'
$pe = Join-Path $adk 'Windows Preinstallation Environment'
$environment = Join-Path $deployment 'DandISetEnv.bat'
$copype = Join-Path $pe 'copype.cmd'
$media = Join-Path $pe 'MakeWinPEMedia.cmd'
$dism = Join-Path $deployment 'amd64\DISM\dism.exe'
foreach ($file in @($environment, $copype, $media, $dism)) {
    if (!(Test-Path $file)) { throw "Missing ADK/add-on tool: $file. See winpe/README.md." }
}
$repository = Split-Path $PSScriptRoot -Parent
$parent = Split-Path $work -Parent
New-Item -ItemType Directory -Force -Path $parent | Out-Null
$bootstrap = Join-Path $parent ('PocketInstall-' + [guid]::NewGuid().ToString('N') + '.cmd')
$mounted = $false
try {
    @"
@echo off
call "$environment"
if errorlevel 1 exit /b 1
call "$copype" amd64 "$work"
exit /b %errorlevel%
"@ | Set-Content -LiteralPath $bootstrap -Encoding ASCII
    Invoke-Checked $env:ComSpec @('/d', '/c', $bootstrap)
    $wim = Join-Path $work 'media\sources\boot.wim'
    $mount = Join-Path $work 'mount'
    Invoke-Checked $dism @('/Mount-Image', "/ImageFile:$wim", '/Index:1', "/MountDir:$mount")
    $mounted = $true
    if ($WithPowerShell) {
        $ocs = Join-Path $pe 'amd64\WinPE_OCs'
        foreach ($package in @('WinPE-WMI', 'WinPE-NetFX', 'WinPE-Scripting', 'WinPE-PowerShell', 'WinPE-StorageWMI', 'WinPE-DismCmdlets', 'WinPE-SecureStartup', 'WinPE-SecureBootCmdlets')) {
            $cab = Join-Path $ocs "$package.cab"
            if (!(Test-Path $cab)) { throw "Matching ADK optional component missing: $cab" }
            Invoke-Checked $dism @("/Image:$mount", '/Add-Package', "/PackagePath:$cab")
            $language = Join-Path $ocs "en-us\${package}_en-us.cab"
            if (Test-Path $language) { Invoke-Checked $dism @("/Image:$mount", '/Add-Package', "/PackagePath:$language") }
        }
    }
    if ($DriverDirectory) {
        $drivers = Safe-Path $DriverDirectory
        if (!(Test-Path $drivers -PathType Container)) { throw 'Driver directory not found.' }
        Invoke-Checked $dism @("/Image:$mount", '/Add-Driver', "/Driver:$drivers", '/Recurse')
    }
    Copy-Item -LiteralPath (Join-Path $repository 'winpe\startnet.cmd') -Destination (Join-Path $mount 'Windows\System32\startnet.cmd')
    if ($WithPowerShell) {
        New-Item -ItemType Directory -Path (Join-Path $mount 'PocketInstall') | Out-Null
        Copy-Item -LiteralPath (Join-Path $repository 'winpe\New-InstallPlan.ps1') -Destination (Join-Path $mount 'PocketInstall')
    }
    Invoke-Checked $dism @('/Unmount-Image', "/MountDir:$mount", '/Commit')
    $mounted = $false

    # A WinPE-only ISO is useful for an independent VM smoke test and for ADK's
    # documented /bootex selection. It is never the full Windows installation ISO.
    $iso = Join-Path $work 'PocketInstall-WinPE.iso'
    @"
@echo off
call "$environment"
if errorlevel 1 exit /b 1
call "$media" /ISO "$work" "$iso" $(if ($UseBootEx) { '/bootex' })
exit /b %errorlevel%
"@ | Set-Content -LiteralPath $bootstrap -Encoding ASCII
    Invoke-Checked $env:ComSpec @('/d', '/c', $bootstrap)
    New-Item -ItemType Directory -Path $output | Out-Null
    $files = [ordered]@{
        'boot.wim' = (Join-Path $work 'media\sources\boot.wim')
        'boot.sdi' = (Join-Path $work 'media\Boot\boot.sdi')
        'BCD' = (Join-Path $work 'media\Boot\BCD')
        'bootmgfw.efi' = (Join-Path $work 'media\EFI\Boot\bootx64.efi')
    }
    $manifest = @()
    foreach ($name in $files.Keys) {
        $source = $files[$name]
        if (!(Test-Path $source)) { throw "Expected ADK media file missing: $source" }
        $destination = Join-Path $output $name
        Copy-Item -LiteralPath $source -Destination $destination
        $manifest += [ordered]@{ name = $name; bytes = (Get-Item $destination).Length; sha256 = (Get-FileHash $destination -Algorithm SHA256).Hash.ToLowerInvariant() }
    }
    [ordered]@{
        kind = 'pocketinstall-winpe-proof'; architecture = 'x64'; installsWindows = $false
        withPowerShell = [bool]$WithPowerShell; requestedBootEx = [bool]$UseBootEx
        bootManagerSignature = (Get-AuthenticodeSignature (Join-Path $output 'bootmgfw.efi')).Status.ToString()
        resources = $manifest
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $output 'manifest.json') -Encoding UTF8
    Write-Host "Bundle: $output"
    Write-Host "Independent WinPE VM ISO: $iso"
    Write-Host 'This does not certify firmware trust, iPXE, wimboot or Secure Boot compatibility.'
} finally {
    if ($mounted) {
        & $dism '/Unmount-Image' "/MountDir:$(Join-Path $work 'mount')" '/Discard'
        if ($LASTEXITCODE -ne 0) { Write-Warning 'Unmount failed. Inspect DISM mounted images before rebuilding.' }
    }
    Remove-Item -LiteralPath $bootstrap -ErrorAction SilentlyContinue
}

