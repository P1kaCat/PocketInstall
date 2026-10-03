#requires -RunAsAdministrator
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$installers = @(
    @{ Name = 'adksetup.exe'; Url = 'https://download.microsoft.com/download/8e0c0f5a-abb5-4358-a51b-168eb40b1590/adk/adksetup.exe'; Feature = 'OptionId.DeploymentTools' },
    @{ Name = 'adkwinpesetup.exe'; Url = 'https://download.microsoft.com/download/a4a79e7a-f085-41c4-aebf-2538fd000790/adkwinpeaddons/adkwinpesetup.exe'; Feature = 'OptionId.WindowsPreinstallationEnvironment' }
)
# Version 10.1.26100.9457, linked by the official Microsoft ADK page (September 2026).
foreach ($installer in $installers) {
    $file = Join-Path $env:RUNNER_TEMP $installer.Name
    Invoke-WebRequest -Uri $installer.Url -OutFile $file
    $signature = Get-AuthenticodeSignature -LiteralPath $file
    if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'O=Microsoft Corporation') {
        throw "Invalid Microsoft installer signature: $file"
    }
    Write-Host "$($installer.Name) SHA-256: $((Get-FileHash $file -Algorithm SHA256).Hash)"
    $log = Join-Path $env:RUNNER_TEMP ($installer.Name + '.log')
    $process = Start-Process -FilePath $file -ArgumentList @('/quiet', '/norestart', '/ceip', 'off', '/features', $installer.Feature, '/log', $log) -Wait -PassThru
    if ($process.ExitCode -notin @(0, 3010)) {
        Get-Content $log -Tail 80 -ErrorAction SilentlyContinue
        throw "ADK installation failed: $($process.ExitCode)"
    }
}
