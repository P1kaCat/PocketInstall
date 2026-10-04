$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$tokens=$null; $errors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile("$PSScriptRoot/../winpe/Install-Windows.ps1",[ref]$tokens,[ref]$errors)
if($errors.Count) { $errors | Out-Host; throw 'Installer syntax invalid.' }
# Evaluate function definitions only. Never execute the installer or touch a real disk.
# Load definitions in this script scope (pipeline scopes would otherwise discard them).
foreach($node in $ast.FindAll({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst]},$false)) { . ([scriptblock]::Create($node.Extent.Text)) }
function Assert($ok,[string]$message) { if(!$ok) { throw $message } }
function Reject([scriptblock]$action,[string]$message) { $rejected=$false; try { & $action | Out-Null } catch { $rejected=$true }; Assert $rejected $message }
$script:parts=@{}
$script:mounts=@{}
$script:volumes=@{}
foreach($spec in @(Get-LayoutSpec 'SPLIT')) {
    $guid='\\?\Volume{'+('00000000-0000-0000-0000-{0:d12}' -f $spec.number)+'}\'
    $script:parts[$spec.number]=[pscustomobject]@{DiskNumber=0;PartitionNumber=$spec.number;GptType=$spec.type;AccessPaths=@($guid);DriveLetter=if($spec.number -eq 1){$null}else{$spec.letter};Size=128GB;Offset=$spec.number*1MB;Guid=('10000000-0000-0000-0000-{0:d12}' -f $spec.number)}
    $script:mounts["$($spec.letter):\"]=$guid
    $script:volumes[$guid]=[pscustomobject]@{FileSystem=$spec.fs}
}
$script:parts[2]=[pscustomobject]@{DiskNumber=0;PartitionNumber=2;GptType='e3c9e316-0b5c-4db8-817d-f92df00215ae';Size=16MB}
function Get-Partition { param([int]$DiskNumber,[int]$PartitionNumber=0); if($PartitionNumber) { return $script:parts[$PartitionNumber] }; return @($script:parts.Values) }
function Get-Volume { param([string]$Path); return $script:volumes[$Path] }
function Test-Path { param([string]$LiteralPath); return $script:mounts.ContainsKey($LiteralPath) }
function mountvol.exe { param([string]$path,[string]$option); $global:LASTEXITCODE=0; Assert ($option -eq '/L') 'Only read-only mountvol permitted.'; return $script:mounts[$path] }
function Update-HostStorageCache {}
Assert-Layout 0 'SPLIT'
Assert ($null -eq $script:parts[1].DriveLetter) 'Regression fixture must not expose an EFI DriveLetter.'
Write-Host 'PASS: accessible EFI S: validated by GUID without Storage DriveLetter.'
$saved=$script:mounts['S:\']; $script:mounts['S:\']='\\?\Volume{ffffffff-ffff-ffff-ffff-ffffffffffff}\'
Reject {Get-VerifiedPartition 0 1 S 'c12a7328-f81f-11d2-ba4b-00a0c93ec93b' FAT32} 'Wrong EFI volume was accepted.'
$script:mounts['S:\']=$saved
$script:parts[1].DiskNumber=1
Reject {Assert-Layout 0 'SPLIT'} 'Wrong target disk was accepted.'
$script:parts[1].DiskNumber=0
$script:volumes[$saved].FileSystem='NTFS'
Reject {Assert-Layout 0 'SPLIT'} 'Wrong EFI filesystem was accepted.'
$script:volumes[$saved].FileSystem='FAT32'
$data=$script:parts[5]; $script:parts.Remove(5)
Reject {Assert-Layout 0 'SPLIT'} 'Missing data partition was accepted.'
Assert-Layout 0 'SINGLE'
$script:parts[5]=$data
Write-Host 'PASS: wrong disk, volume, filesystem and missing data rejected.'
$split=New-PartitionScript 0 SPLIT 128
$single=New-PartitionScript 0 SINGLE 128
Assert ($split -match 'create partition primary size=131072') 'Windows size not respected.'
Assert ($split.IndexOf('label=Windows') -lt $split.IndexOf('label=Recovery') -and $split.IndexOf('label=Recovery') -lt $split.IndexOf('label=MesFichiers')) 'Recovery must immediately follow Windows, before data.'
Assert ($split -match 'assign letter=U' -and $single -notmatch 'label=MesFichiers' -and $single -match 'shrink minimum=2048') 'Layout compatibility invalid.'
Reject {New-PartitionScript 0 SPLIT 1} 'Unsafe Windows size accepted.'
Reject {New-PartitionScript -1 SPLIT 128} 'Invalid disk accepted.'
Write-Host 'PASS: single/split layout, sizing and recovery order.'
Assert ((Get-MinimumSystemGiB WINDOWS_11 20GB 5GB) -eq 64) 'Windows 11 storage floor not preserved.'
Assert ((Get-MinimumSystemGiB WINDOWS_10 20GB 5GB) -eq 48) 'Windows 10 should not reserve 128 GiB.'
Assert ((Get-MinimumSystemGiB WINDOWS_10 35GB 6GB) -eq 64) 'Edition size and margins not respected.'
Assert ((Get-MinimumSystemGiB WINDOWS_11 50GB 16GB) -eq 76) 'Peak and steady requirements not respected.'
Reject {Get-MinimumSystemGiB WINDOWS_11 0 5GB} 'Absent expanded size accepted.'
Reject {Get-MinimumSystemGiB WINDOWS_11 512GB 5GB} 'Oversized edition accepted.'
Reject {New-PartitionScript 0 SPLIT 51} 'Unaligned automatic size accepted.'
Assert ((New-PartitionScript 0 SPLIT 48) -match 'create partition primary size=49152') 'Computed size was not used in DiskPart.'
Write-Host 'PASS: automatic edition sizes, Microsoft floors and transfer headroom.'
$testDir=Join-Path ([IO.Path]::GetTempPath()) ('pocketinstall-storage-'+[guid]::NewGuid())
[IO.Directory]::CreateDirectory($testDir) | Out-Null
New-PSDrive -Name X -PSProvider FileSystem -Root $testDir | Out-Null
function Invoke-Checked { param([string]$file,[string[]]$arguments)
    Assert ($file -eq 'diskpart.exe') 'Unexpected executable in resume.'
    $script:resumeCommands=Get-Content -LiteralPath $arguments[1] -Raw
    Assert ($script:resumeCommands -notmatch '(?im)^\s*(clean|format|create|shrink|delete|convert)\b') 'Resume can repartition or format.'
}
try {
    $script:resumeCommands=''
    $script:mounts.Remove('U:\')
    # Simulate the mount created by the mocked DiskPart execution.
    function Invoke-Checked { param([string]$file,[string[]]$arguments)
        Assert ($file -eq 'diskpart.exe') 'Unexpected executable in resume.'
        $script:resumeCommands=Get-Content -LiteralPath $arguments[1] -Raw
        Assert ($script:resumeCommands -notmatch '(?im)^\s*(clean|format|create|shrink|delete|convert)\b') 'Resume can repartition or format.'
        $script:mounts['U:\']=$data.AccessPaths[0]
    }
    Connect-ResumeLetters 0 SPLIT
    Assert ($script:resumeCommands -match 'select partition 5\s+assign letter=U') 'Resume failed to mount existing data partition.'
    Write-Host 'PASS: resume mounts existing partitions without erase or format.'
} finally { Remove-PSDrive X; [IO.Directory]::Delete($testDir,$true) }
# Parse the generated specialize program separately (it is a here-string in the installer).
$source=[IO.File]::ReadAllText("$PSScriptRoot/../winpe/Install-Windows.ps1")
$startup=[regex]::Match($source,'(?s)\$bootScript=@''\r?\n(.*?)\r?\n''@').Groups[1].Value
Assert ($startup.Length -gt 100) 'Generated startup script absent.'
$tokens=$null; $errors=$null
[System.Management.Automation.Language.Parser]::ParseInput($startup,[ref]$tokens,[ref]$errors) | Out-Null
if($errors.Count) { $errors | Out-Host; throw 'Startup script syntax invalid.' }
Assert ($startup -notmatch 'NoViewOnDrive|Remove-Partition|Clear-Disk|Format-Volume') 'Startup must never restrict C access or repartition.'
Write-Host 'PASS: generated Windows startup syntax and non-destructive invariants.'
