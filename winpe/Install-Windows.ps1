[CmdletBinding()]
param([Parameter(Mandatory)][string]$BaseUrl)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$endpoint = [Uri]$BaseUrl
if ($endpoint.Scheme -ne 'http' -or $endpoint.Host -notmatch '^\d+\.\d+\.\d+\.\d+$' -or $endpoint.AbsolutePath -notmatch '^/[a-f0-9]{32}$') { throw 'Invalid session URL.' }

$script:ConsoleStage=''
$script:ReportWarningShown=$false
function Show-InstallStage([string]$stage, [string]$message='') {
    if($script:ConsoleStage -eq $stage) { return }
    $script:ConsoleStage=$stage
    try { Clear-Host } catch {}
    Write-Host ''
    Write-Host '  POCKETINSTALL / INSTALLATION WINDOWS' -ForegroundColor Cyan
    Write-Host '  -----------------------------------' -ForegroundColor DarkCyan
    $titles=@('Detection du PC','Choix du disque','Preparation du disque','Transfert de Windows','Verification de l image','Installation de Windows','Personnalisation','Pret a redemarrer')
    $positions=@{inventory=0;'awaiting-disk'=1;'awaiting-confirmation'=1;partitioning=2;downloading=3;verifying=4;applying=5;configuring=6;prepared=7;error=-1}
    $position=if($positions.ContainsKey($stage)) {$positions[$stage]} else {-1}
    for($i=0;$i -lt $titles.Count;$i++) {
        $mark=if($i -eq $position){' > '}elseif($position -gt $i){'OK '}else{' . '}
        $color=if($i -eq $position){'Cyan'}elseif($position -gt $i){'Green'}else{'Gray'}
        Write-Host ('  {0} {1}' -f $mark,$titles[$i]) -ForegroundColor $color
    }
    Write-Host ''
    if($message) { Write-Host ('  '+$message) }
    Write-Host '  Details : X:\PocketInstall-native.log et W:\PocketInstall' -ForegroundColor DarkGray
    Write-Host ''
}
function Save-NativeLog {
    if(Test-Path -LiteralPath 'W:\PocketInstall') {
        Copy-Item -LiteralPath 'X:\PocketInstall-native.log' -Destination 'W:\PocketInstall\native.log' -Force -ErrorAction SilentlyContinue
    }
}
function Send-Report([string]$stage, $hardware = $null, [string]$message = '') {
    Show-InstallStage $stage $message
    try {
        $body = [Text.Encoding]::UTF8.GetBytes((@{ stage=$stage; hardware=$hardware; message=$message.Substring(0,[Math]::Min(500,$message.Length)) } | ConvertTo-Json -Depth 6 -Compress))
        $request = [Net.HttpWebRequest]::Create("$BaseUrl/install/report")
        $request.Proxy=$null; $request.Method='POST'; $request.ContentType='application/json'; $request.ContentLength=$body.Length
        $request.Timeout=5000; $request.ReadWriteTimeout=5000
        $stream=$request.GetRequestStream(); try { $stream.Write($body,0,$body.Length) } finally { $stream.Dispose() }
        $response=$request.GetResponse(); $response.Dispose()
    } catch {
        if(!$script:ReportWarningShown) { Write-Host '  Statut telephone indisponible. L installation continue sur ce PC.' -ForegroundColor Yellow; $script:ReportWarningShown=$true }
    }
}
function Invoke-Checked([string]$file, [string[]]$arguments) {
    if($file -ieq 'diskpart.exe') {
        $previousPolicy=$ErrorActionPreference
        try {
            $ErrorActionPreference='Continue'
            $output=& $file @arguments 2>&1
            $exitCode=$LASTEXITCODE
        } finally { $ErrorActionPreference=$previousPolicy }
        $output | Out-File -LiteralPath 'X:\PocketInstall-native.log' -Append -Encoding UTF8
        if($exitCode -ne 0) { $output | Select-Object -Last 12 | Out-Host; throw "$file failed ($exitCode). See X:\PocketInstall-native.log." }
        return
    }
    & $file @arguments
    if ($LASTEXITCODE -ne 0) { throw "$file failed ($LASTEXITCODE). Installation interrupted; see console and logs." }
}
function Get-Sha256([string]$path) { (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Assert-WindowsImageMatchesPlan($info, $plan) {
    # DISM returns Version as text in WinPE, not necessarily as System.Version.
    # Normalize before reading Build; never guess an OS from an invalid version.
    foreach($name in @('Version','Architecture','EditionId')) {
        if($null -eq $info -or $null -eq $info.PSObject.Properties[$name]) { throw "DISM image metadata missing: $name." }
    }
    $version=$null
    if(![version]::TryParse([string]$info.Version,[ref]$version) -or $version.Major -ne 10 -or $version.Minor -ne 0 -or $version.Build -lt 10240) { throw 'DISM image version invalid or unsupported.' }
    $matchesVersion=($plan.version -eq 'WINDOWS_11' -and $version.Build -ge 22000) -or ($plan.version -eq 'WINDOWS_10' -and $version.Build -lt 22000)
    if([int]$info.Architecture -ne 9 -or [string]$info.EditionId -cne [string]$plan.editionId -or !$matchesVersion) { throw 'Edition/version/architecture does not match the selected image.' }
}
function Get-Hardware {
    $cpu = @(Get-CimInstance Win32_Processor)
    $system = Get-CimInstance Win32_ComputerSystem
    $ram=[long]$system.TotalPhysicalMemory; $ramSource='usable'
    try {
        $installed=[long]((Get-CimInstance Win32_PhysicalMemory | Measure-Object Capacity -Sum).Sum)
        if($installed -gt 0) { $ram=$installed; $ramSource='SMBIOS' }
    } catch {}
    $tpm = 'unknown'; $secureBoot = 'unknown'
    # Older imported WinPE bundles lack the optional TPM driver/provider. Missing support is not proof of missing hardware.
    if(Test-Path "$env:SystemRoot/System32/tbs.dll") {
        try { $chip=Get-CimInstance -Namespace root/cimv2/security/microsofttpm -ClassName Win32_Tpm; if($chip) { $tpm=[string]$chip.SpecVersion } else { $tpm='absent' } } catch {}
    }
    try { $secureBoot=[string](Confirm-SecureBootUEFI) } catch {}
    $disks = @(Get-Disk | Select-Object Number,FriendlyName,SerialNumber,UniqueId,Size,BusType,IsReadOnly,IsOffline)
    @{
        model=[string]$system.Model; cpu=($cpu.Name -join ', '); cores=[int](($cpu | Measure-Object NumberOfCores -Sum).Sum)
        ramBytes=$ram; ramSource=$ramSource; gpu=(@(Get-CimInstance Win32_VideoController -ErrorAction SilentlyContinue | ForEach-Object { $_.Name }) -join ', ')
        tpm=$tpm; secureBoot=$secureBoot; disks=$disks
    }
}
function Receive-Image([string]$path, [long]$length, [string]$sha256) {
    $partial="$path.part"
    $output=[IO.File]::Open($partial,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try {
        if($output.Length -gt $length) { $output.SetLength(0) }
        $output.Position=$output.Length
        while($output.Position -lt $length) {
            $offset=$output.Position; $end=[Math]::Min($offset + 8MB - 1,$length - 1); $ok=$false
            for($attempt=0; $attempt -lt 3 -and !$ok; $attempt++) {
                $response=$null; $source=$null
                try {
                    $request=[Net.HttpWebRequest]::Create("$BaseUrl/install/image.wim")
                    $request.Proxy=$null; $request.Timeout=15000; $request.ReadWriteTimeout=30000
                    $request.AddRange($offset,$end)
                    $response=$request.GetResponse()
                    if([int]$response.StatusCode -ne 206 -or $response.Headers['Content-Range'] -ne "bytes $offset-$end/$length" -or $response.ContentLength -ne ($end-$offset+1)) { throw 'Invalid image range.' }
                    $source=$response.GetResponseStream(); $remaining=$end-$offset+1; $buffer=New-Object byte[] 1048576
                    while($remaining -gt 0) {
                        $count=$source.Read($buffer,0,[int][Math]::Min($buffer.Length,$remaining))
                        if($count -le 0) { throw 'Truncated image download.' }
                        $output.Write($buffer,0,$count); $remaining-=$count
                    }
                    $output.Flush(); $ok=$true
                } catch {
                    $output.SetLength($offset); $output.Position=$offset
                    if($attempt -eq 2) { throw }; Start-Sleep -Seconds 2
                } finally { if($source) { $source.Dispose() }; if($response) { $response.Dispose() } }
            }
            Write-Progress -Activity 'Transfert de Windows depuis le telephone' -PercentComplete ([int](100*$output.Position/$length))
        }
    } finally { $output.Dispose(); Write-Progress -Activity 'Transfert de Windows depuis le telephone' -Completed }
    if((Get-Sha256 $partial) -ne $sha256) { throw 'Image hash mismatch. Windows has not been applied.' }
    Move-Item -LiteralPath $partial -Destination $path -Force
}

# WinPE can expose S: while the Storage provider reports no DriveLetter for EFI.
# Resolve the mount point to a volume GUID, then compare it to the expected GPT partition.
function Get-VerifiedPartition([int]$diskNumber, [int]$partitionNumber, [string]$letter, [string]$gptType, [string]$fileSystem) {
    $partition=Get-Partition -DiskNumber $diskNumber -PartitionNumber $partitionNumber
    if($partition.DiskNumber -ne $diskNumber -or $partition.PartitionNumber -ne $partitionNumber) { throw 'Unexpected partition identity.' }
    if(([string]$partition.GptType).Trim('{}') -ine $gptType.Trim('{}')) { throw "Unexpected GPT type for partition $partitionNumber." }
    if(!(Test-Path -LiteralPath "${letter}:\")) { throw "Drive $letter is not accessible." }
    $mount=(& mountvol.exe "${letter}:\" /L | Out-String).Trim()
    if($LASTEXITCODE -ne 0 -or $mount -notmatch '^\\\\\?\\Volume\{[a-fA-F0-9-]{36}\}\\$') { throw "Cannot resolve drive $letter." }
    if(!(@($partition.AccessPaths) | Where-Object { [string]$_ -ieq $mount })) { throw "Drive $letter does not map to disk $diskNumber partition $partitionNumber." }
    $volume=Get-Volume -Path $mount
    if([string]$volume.FileSystem -ine $fileSystem) { throw "Unexpected filesystem for drive $letter." }
    return $partition
}
function Get-LayoutSpec([string]$storageLayout) {
    $items=@(
        @{number=1;letter='S';type='c12a7328-f81f-11d2-ba4b-00a0c93ec93b';fs='FAT32'},
        @{number=3;letter='W';type='ebd0a0a2-b9e5-4433-87c0-68b6b72699c7';fs='NTFS'},
        @{number=4;letter='R';type='de94bba4-06d1-4d40-a16a-bfd50179d6ac';fs='NTFS'}
    )
    if($storageLayout -eq 'SPLIT') { $items+=@{number=5;letter='U';type='ebd0a0a2-b9e5-4433-87c0-68b6b72699c7';fs='NTFS'} }
    return $items
}
function Assert-Layout([int]$diskNumber, [string]$storageLayout) {
    $parts=@(Get-Partition -DiskNumber $diskNumber)
    $count=if($storageLayout -eq 'SPLIT') {5} else {4}
    if($parts.Count -ne $count) { throw 'Unexpected partition count.' }
    $reserved=Get-Partition -DiskNumber $diskNumber -PartitionNumber 2
    if(([string]$reserved.GptType).Trim('{}') -ine 'e3c9e316-0b5c-4db8-817d-f92df00215ae' -or $reserved.Size -ne 16MB) { throw 'MSR partition missing or invalid.' }
    foreach($item in @(Get-LayoutSpec $storageLayout)) { Get-VerifiedPartition $diskNumber $item.number $item.letter $item.type $item.fs | Out-Null }
}
function Get-MinimumSystemGiB([string]$version, [long]$expandedBytes, [long]$transferBytes) {
    if($version -notin @('WINDOWS_10','WINDOWS_11') -or $expandedBytes -lt 1 -or $expandedBytes -gt 512GB -or $transferBytes -lt 208 -or $transferBytes -gt 16GB) { throw 'Invalid Windows size metadata.' }
    $floor=if($version -eq 'WINDOWS_11') {64GB} else {32GB}
    $steady=$expandedBytes+10GB+16GB
    $peak=$expandedBytes+$transferBytes+2GB
    $needed=[Math]::Max($floor,[Math]::Max($steady,$peak))
    $rounded=[int]([Math]::Ceiling($needed/4GB)*4)
    if($rounded -gt 512) { throw 'Windows edition exceeds supported capacity.' }
    return $rounded
}
function New-PartitionScript([int]$diskNumber, [string]$storageLayout, [int]$systemGiB) {
    if($diskNumber -lt 0 -or $storageLayout -notin @('SINGLE','SPLIT') -or ($systemGiB -lt 32 -or $systemGiB -gt 512 -or $systemGiB % 4 -ne 0)) { throw 'Invalid disk layout.' }
    $windows=if($storageLayout -eq 'SPLIT') { "create partition primary size=$($systemGiB*1024)" } else { "create partition primary`r`nshrink minimum=2048" }
    $recovery=if($storageLayout -eq 'SPLIT') {'create partition primary size=2048'} else {'create partition primary'}
    $data=if($storageLayout -eq 'SPLIT') { "create partition primary`r`nformat quick fs=ntfs label=MesFichiers`r`nassign letter=U" } else {''}
    return @"
select disk $diskNumber
clean
convert gpt
create partition efi size=300
format quick fs=fat32 label=System
assign letter=S
create partition msr size=16
$windows
format quick fs=ntfs label=Windows
assign letter=W
$recovery
format quick fs=ntfs label=Recovery
assign letter=R
set id=de94bba4-06d1-4d40-a16a-bfd50179d6ac
gpt attributes=0x8000000000000001
$data
exit
"@
}
function Save-PartitionCheckpoint($identity, $plan, [string]$storageLayout) {
    $record=@{schema=1;stage='partitioned';disk=$identity;sha256=[string]$plan.sha256;index=[int]$plan.index;storageLayout=$storageLayout;partitions=@(Get-Partition -DiskNumber $identity.number | Select-Object PartitionNumber,Guid,Offset,Size)}
    $record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath 'W:\PocketInstall\resume.json' -Encoding UTF8
}
function Get-ResumeCheckpoint([int]$diskNumber, $plan) {
    $partition=Get-Partition -DiskNumber $diskNumber -PartitionNumber 3
    $path=@($partition.AccessPaths | Where-Object { $_ -match '^\\\\\?\\Volume\{' }) | Select-Object -First 1
    if(!$path) { throw 'Cannot locate the installation checkpoint.' }
    $record=[IO.File]::ReadAllText("${path}PocketInstall\resume.json") | ConvertFrom-Json
    if($record.schema -ne 1 -or $record.stage -ne 'partitioned' -or $record.sha256 -cne [string]$plan.sha256 -or $record.index -ne [int]$plan.index -or $record.disk.number -ne $diskNumber -or $record.storageLayout -notin @('SINGLE','SPLIT')) { throw 'Reprise indisponible: image differente, checkpoint absent, ou application Windows deja commencee.' }
    $disk=Get-Disk -Number $diskNumber
    if([string]$disk.UniqueId -cne $record.disk.uniqueId -or [string]$disk.SerialNumber -cne $record.disk.serial -or [long]$disk.Size -ne $record.disk.size) { throw 'Checkpoint disk identity mismatch.' }
    $parts=@(Get-Partition -DiskNumber $diskNumber)
    if($parts.Count -ne @($record.partitions).Count) { throw 'Checkpoint layout changed.' }
    foreach($saved in $record.partitions) {
        $current=Get-Partition -DiskNumber $diskNumber -PartitionNumber $saved.PartitionNumber
        if([string]$current.Guid -ine [string]$saved.Guid -or $current.Offset -ne $saved.Offset -or $current.Size -ne $saved.Size) { throw 'Checkpoint partition changed.' }
    }
    return $record
}
function Connect-ResumeLetters([int]$diskNumber, [string]$storageLayout) {
    $commands=@("select disk $diskNumber")
    foreach($item in @(Get-LayoutSpec $storageLayout)) {
        if(Test-Path -LiteralPath "$($item.letter):\") { Get-VerifiedPartition $diskNumber $item.number $item.letter $item.type $item.fs | Out-Null }
        else { $commands+="select partition $($item.number)"; $commands+="assign letter=$($item.letter)" }
    }
    # This path only mounts existing partitions: it cannot clean, create, shrink or format.
    $commands+='exit'; $commands | Set-Content -LiteralPath X:\PocketInstall-resume-letters.txt -Encoding ASCII
    Invoke-Checked diskpart.exe @('/s','X:\PocketInstall-resume-letters.txt')
    Update-HostStorageCache
    Assert-Layout $diskNumber $storageLayout
}

try {
    $hardware=Get-Hardware
    Send-Report 'inventory' $hardware
    Write-Host "PC: $($hardware.model) | $($hardware.cpu) | RAM $([Math]::Round($hardware.ramBytes/1GB,1)) Go"
    $plan = Get-Content -LiteralPath "$PSScriptRoot/install-plan.json" -Raw | ConvertFrom-Json
    if (!$plan.enabled) {
        Write-Host 'WinPE est pret. Importe une image Windows dans PocketInstall et choisis Windows > Edition avant le prochain demarrage PXE.'
        return
    }
    if($plan.schema -notin @(1,2,3) -or $plan.index -lt 1 -or $plan.index -gt 64 -or $plan.bytes -lt 208 -or $plan.bytes -gt 16GB -or $plan.sha256 -notmatch '^[a-f0-9]{64}$' -or $plan.version -notin @('WINDOWS_10','WINDOWS_11') -or $plan.editionId -notin @('Core','Professional') -or $plan.debloat -notin @('NONE','LIGHT','CUSTOM','AUTO')) { throw 'Invalid installation plan.' }
    & wpeutil.exe UpdateBootInfo | Out-Null
    if((Get-ItemProperty HKLM:\SYSTEM\CurrentControlSet\Control).PEFirmwareType -ne 2) { throw 'UEFI boot required.' }
    if($plan.version -eq 'WINDOWS_11') {
        if($hardware.ramBytes -lt 4GB -or $hardware.cores -lt 2 -or $hardware.tpm -eq 'absent' -or ($hardware.tpm -ne 'unknown' -and $hardware.tpm -notmatch '2\.0')) { throw 'Windows 11: exigences RAM/CPU/TPM non satisfaites. Aucun disque modifie.' }
        Write-Host 'Windows 11: verifier aussi le processeur dans la liste Microsoft et la capacite Secure Boot. Aucun contournement applique.'
    }
    $profile=[string]$plan.debloat
    if($profile -eq 'AUTO') { $profile=if($hardware.ramBytes -lt 8GB -or $hardware.cores -le 2) { 'LIGHT' } else { 'NONE' } }
    Write-Host "Selection: $($plan.version) $($plan.editionId), image index $($plan.index), debloat $profile"
    $storageLayout='SINGLE'; $systemGiB=128; $hideSystemDrive=$false
    if($plan.schema -ge 2) {
        if($plan.storageLayout -notin @('SINGLE','SPLIT') -or ($plan.systemGiB -lt 32 -or $plan.systemGiB -gt 512 -or $plan.systemGiB % 4 -ne 0) -or $plan.hideSystemDrive -isnot [bool]) { throw 'Invalid storage plan.' }
        $storageLayout=[string]$plan.storageLayout; $systemGiB=[int]$plan.systemGiB
        $hideSystemDrive=$storageLayout -eq 'SPLIT' -and $plan.hideSystemDrive
    }
    $minimumSystemGiB=[int][Math]::Ceiling([Math]::Max(64GB,[long]$plan.bytes+58GB)/1GB)
    if($plan.schema -eq 3) {
        if($plan.autoSystemSize -isnot [bool] -or $plan.expandedBytes -lt 0 -or $plan.expandedBytes -gt 512GB) { throw 'Invalid automatic sizing plan.' }
        if($plan.expandedBytes -gt 0) { $minimumSystemGiB=Get-MinimumSystemGiB ([string]$plan.version) ([long]$plan.expandedBytes) ([long]$plan.bytes) }
        if($plan.autoSystemSize -and ($plan.expandedBytes -le 0 -or $systemGiB -ne $minimumSystemGiB)) { throw 'Automatic Windows size inconsistent. No disk modified.' }
    }
    Write-Host 'Installation neuve: toutes les partitions du disque choisi seront effacees, apres confirmation.'
    $candidates=@(Get-Disk | Where-Object { !$_.IsReadOnly -and !$_.IsOffline -and $_.BusType -notin @('USB','SD','MMC') -and $_.Size -ge 64GB })
    if(!$candidates.Count) { throw 'Aucun disque interne accessible de 64 Go minimum. Aucun disque modifie.' }
    Send-Report 'awaiting-disk'
    $candidates | Format-Table Number,FriendlyName,SerialNumber,@{Label='Go';Expression={[Math]::Round($_.Size/1GB,1)}} -AutoSize | Out-Host
    $numberText=Read-Host 'Numero du disque (vide pour annuler; REPRENDRE N pour une installation interrompue avant application de Windows)'
    $resume=$numberText -cmatch '^REPRENDRE (\d{1,4})$'
    if($resume) { $numberText=$Matches[1] }
    if($numberText -notmatch '^\d{1,4}$') { Write-Host 'Installation annulee. Aucun disque modifie.'; return }
    $disk=$candidates | Where-Object Number -eq ([int]$numberText) | Select-Object -First 1
    if(!$disk -or [string]::IsNullOrWhiteSpace([string]$disk.UniqueId)) { throw 'Disque non eligible ou identite non disponible.' }
    $identity=@{ number=[int]$disk.Number; uniqueId=[string]$disk.UniqueId; serial=[string]$disk.SerialNumber; size=[long]$disk.Size; model=[string]$disk.FriendlyName }
    if($resume) {
        $checkpoint=Get-ResumeCheckpoint $identity.number $plan
        if($checkpoint.storageLayout -cne $storageLayout) { throw 'Choisis le meme agencement que celui du checkpoint pour reprendre.' }
        Write-Host "REPRISE SANS EFFACEMENT: disque $($identity.number), $($identity.model), serie $($identity.serial)."
        if((Read-Host "Tape 'REPRENDRE $($identity.number)' pour poursuivre le transfert") -cne "REPRENDRE $($identity.number)") { return }
        Connect-ResumeLetters $identity.number $storageLayout
    } else {
        $needed=$minimumSystemGiB*1GB+300MB+16MB+2048MB+2MB
        if($storageLayout -eq 'SPLIT') {
            if($systemGiB -lt $minimumSystemGiB) { throw 'Partition Windows trop petite pour Windows et le transfert temporaire.' }
            $needed=$systemGiB*1GB + 16GB + 300MB + 16MB + 2048MB + 2MB
            Write-Host "C: Windows $systemGiB Gio | D: Mes fichiers: reste du disque | EFI, MSR et recuperation masques."
        }
        if($disk.Size -lt $needed) { throw 'Disque trop petit pour la taille Windows choisie et au moins 16 Gio de fichiers. Aucun disque modifie.' }
        foreach($item in @(Get-LayoutSpec $storageLayout)) { if(Test-Path -LiteralPath "$($item.letter):\") { throw "La lettre $($item.letter) est deja utilisee. Aucun disque modifie." } }
        Send-Report 'awaiting-confirmation' $null "Disque $($identity.number), $($identity.model), serie $($identity.serial)"
        Write-Host "EFFACEMENT: disque $($identity.number), $($identity.model), serie $($identity.serial), $([Math]::Round($identity.size/1GB,1)) Go."
        Write-Host 'Le transfert commence apres le partitionnement. En cas de panne reseau, ce disque restera efface.'
        $phrase="EFFACER $($identity.number)"
        if((Read-Host "Tape exactement '$phrase' pour confirmer") -cne $phrase) { Write-Host 'Annule. Aucun disque modifie.'; return }
        $check=Get-Disk -Number $identity.number
        if([string]$check.UniqueId -cne $identity.uniqueId -or [long]$check.Size -ne $identity.size -or [string]$check.SerialNumber -cne $identity.serial -or $check.IsReadOnly -or $check.IsOffline) { throw 'Identite du disque modifiee. Aucun disque modifie.' }
        $probe=[Net.HttpWebRequest]::Create("$BaseUrl/install/image.wim"); $probe.Proxy=$null; $probe.Method='HEAD'; $probe.Timeout=5000
        $answer=$probe.GetResponse(); try { if($answer.ContentLength -ne [long]$plan.bytes) { throw 'Image source changed.' } } finally { $answer.Dispose() }
        Send-Report 'partitioning' $null "Disque $($identity.number): $($identity.model)"
        New-PartitionScript $identity.number $storageLayout $systemGiB | Set-Content -LiteralPath X:\PocketInstall-layout.txt -Encoding ASCII
        Invoke-Checked diskpart.exe @('/s','X:\PocketInstall-layout.txt')
        $mapped=$false
        for($attempt=0; $attempt -lt 5; $attempt++) {
            try { Update-HostStorageCache; Assert-Layout $identity.number $storageLayout; $mapped=$true; break }
            catch { if($attempt -eq 4) { throw }; Start-Sleep -Seconds 1 }
        }
        if(!$mapped) { throw 'Partition mapping unavailable.' }
    }
    $windowsPartition=Get-Partition -DiskNumber $identity.number -PartitionNumber 3
    if($windowsPartition.Size+1MB -lt $minimumSystemGiB*1GB) { throw 'Partition Windows trop petite.' }
    if($storageLayout -eq 'SPLIT' -and [Math]::Abs($windowsPartition.Size-($systemGiB*1GB)) -gt 1MB) { throw 'La taille Windows ne correspond pas au checkpoint: reprends avec la taille initiale.' }
    if(Test-Path -LiteralPath W:\Windows) { throw 'Windows existe deja: cette reprise ne reapplique pas une image sur un systeme existant.' }
    $work='W:\PocketInstall'; New-Item -ItemType Directory -Path $work -Force | Out-Null
    # Save the post-GPT identity (clean/convert can change disk identifiers).
    $post=Get-Disk -Number $identity.number; $identity.uniqueId=[string]$post.UniqueId
    Save-PartitionCheckpoint $identity $plan $storageLayout
    Start-Transcript -Path "$work/install.log" -Append | Out-Null
    Send-Report 'downloading'
    $image="$work/install.wim"; Receive-Image $image ([long]$plan.bytes) ([string]$plan.sha256)
    Send-Report 'verifying'
    $info=Get-WindowsImage -ImagePath $image -Index ([int]$plan.index)
    Assert-WindowsImageMatchesPlan $info $plan
    if($plan.schema -eq 3) {
        $actualMinimum=Get-MinimumSystemGiB ([string]$plan.version) ([long]$info.ImageSize) ([long]$plan.bytes)
        if($windowsPartition.Size+1MB -lt $actualMinimum*1GB) { throw 'DISM signale une edition plus grande que les metadonnees: aucun fichier Windows applique. Choisis une taille manuelle suffisante.' }
    }
    New-Item -ItemType Directory -Path "$work/scratch" -Force | Out-Null
    $checkpoint=Get-Content -LiteralPath "$work/resume.json" -Raw | ConvertFrom-Json
    $checkpoint.stage='applying'; $checkpoint | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath "$work/resume.json" -Encoding UTF8
    Send-Report 'applying'
    Invoke-Checked dism.exe @('/Apply-Image',"/ImageFile:$image","/Index:$($plan.index)",'/ApplyDir:W:\','/CheckIntegrity',"/ScratchDir:$work/scratch")
    Send-Report 'configuring'
    $remove=@()
    if($profile -eq 'LIGHT') { $remove=@('Clipchamp.Clipchamp','Microsoft.MicrosoftSolitaireCollection','Microsoft.BingNews','Microsoft.BingWeather') }
    if($profile -eq 'CUSTOM') {
        if($plan.removeClipchamp) { $remove+='Clipchamp.Clipchamp' }; if($plan.removeSolitaire) { $remove+='Microsoft.MicrosoftSolitaireCollection' }
        if($plan.removeNews) { $remove+='Microsoft.BingNews' }; if($plan.removeWeather) { $remove+='Microsoft.BingWeather' }
    }
    $audit=@()
    foreach($package in @(Get-AppxProvisionedPackage -Path W:\)) {
        if($package.DisplayName -in $remove) {
            Remove-AppxProvisionedPackage -Path W:\ -PackageName $package.PackageName | Out-Null
            $audit+=@{ name=$package.DisplayName; package=$package.PackageName; action='remove-provisioned'; restore='Reinstaller depuis Microsoft Store' }
        }
    }
    $audit | ConvertTo-Json -Depth 4 | Set-Content "$work/debloat.json" -Encoding UTF8
    $plan | ConvertTo-Json -Depth 4 | Set-Content "$work/selection.json" -Encoding UTF8
    Invoke-Checked bcdboot.exe @('W:\Windows','/s','S:','/f','UEFI')
    $winre='W:\Windows\System32\Recovery\Winre.wim'
    if(!(Test-Path -LiteralPath $winre)) { throw 'Windows applied, but WinRE missing. Boot/recovery must be checked before reboot.' }
    if((Get-Item $winre).Length -gt 1700MB) { throw 'WinRE too large for the recovery partition.' }
    New-Item -ItemType Directory -Path R:\Recovery\WindowsRE | Out-Null
    Copy-Item -LiteralPath $winre -Destination R:\Recovery\WindowsRE\Winre.wim
    Invoke-Checked W:\Windows\System32\reagentc.exe @('/SetReImage','/Path','R:\Recovery\WindowsRE','/Target','W:\Windows')
    # Specialize runs only after the installed OS starts. No OOBE/account/license settings are bypassed.
    $panther='W:\Windows\Panther'; New-Item -ItemType Directory -Path $panther -Force | Out-Null
    if(Test-Path "$panther/unattend.xml") { throw 'An existing answer file must be reviewed before adding the startup signal.' }
    $bootScript=@'
$ErrorActionPreference='Stop'
if(Test-Path HKLM:\SYSTEM\CurrentControlSet\Control\MiniNT) { exit 1 }
$storageMessage=''
try {
    if('__DATA_GUID__' -ne '') {
        $system=Get-Partition -DriveLetter ($env:SystemDrive.TrimEnd(':'))
        $data=@(Get-Partition | Where-Object { ([string]$_.Guid).Trim('{}') -ieq '__DATA_GUID__' })
        if($data.Count -ne 1 -or $data[0].DiskNumber -ne $system.DiskNumber -or ([string]$data[0].GptType).Trim('{}') -ine 'ebd0a0a2-b9e5-4433-87c0-68b6b72699c7') { throw 'Partition de fichiers introuvable sur le disque Windows.' }
        if($data[0].DriveLetter -ne 'D') {
            if(Get-Volume -DriveLetter D -ErrorAction SilentlyContinue) { throw 'D: est deja utilise. C: reste visible.' }
            Set-Partition -DiskNumber $data[0].DiskNumber -PartitionNumber $data[0].PartitionNumber -NewDriveLetter D
        }
        Set-Volume -DriveLetter D -NewFileSystemLabel 'Mes fichiers'
        if('__HIDE_C__' -eq 'true') {
            & reg.exe load HKU\PocketInstallDefault "$env:SystemDrive\Users\Default\NTUSER.DAT"
            if($LASTEXITCODE -ne 0) { throw 'Profil par defaut non accessible; C: reste visible.' }
            try {
                # Cosmetic only: keep C: mounted and accessible to users and applications.
                & reg.exe add 'HKU\PocketInstallDefault\Software\Microsoft\Windows\CurrentVersion\Policies\Explorer' /v NoDrives /t REG_DWORD /d 4 /f
                if($LASTEXITCODE -ne 0) { throw 'Masquage de C: non applique.' }
            } finally {
                & reg.exe unload HKU\PocketInstallDefault
                if($LASTEXITCODE -ne 0) { throw 'Profil par defaut non decharge.' }
            }
            @(
                '@echo off',
                'reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Policies\Explorer" /v NoDrives /f',
                'echo Ferme puis rouvre ta session pour afficher C: dans Explorateur.',
                'pause'
            ) | Set-Content -LiteralPath 'D:\Afficher Windows.cmd' -Encoding ASCII
        }
        $storageMessage=' C: Windows; D: Mes fichiers.'
    }
} catch {
    $storageMessage=" Agencement a verifier: $($_.Exception.Message)"
}
$storageMessage | Set-Content -LiteralPath "$env:SystemDrive\PocketInstall\storage-status.txt" -Encoding UTF8
$success=$false
for($attempt=0; $attempt -lt 12; $attempt++) {
    try {
        $body=[Text.Encoding]::UTF8.GetBytes((@{stage='windows-started';message=('Signal du Windows installe pendant specialize; OOBE reste a terminer.'+$storageMessage)} | ConvertTo-Json -Compress))
        $request=[Net.HttpWebRequest]::Create('__BASE__/install/report')
        $request.Proxy=$null; $request.Method='POST'; $request.ContentType='application/json'; $request.ContentLength=$body.Length
        $request.Timeout=3000; $request.ReadWriteTimeout=3000
        $stream=$request.GetRequestStream(); try { $stream.Write($body,0,$body.Length) } finally { $stream.Dispose() }
        $response=$request.GetResponse(); $response.Dispose(); $success=$true; break
    } catch { Start-Sleep -Seconds 2 }
}
if($success) { Remove-Item -LiteralPath $PSCommandPath -Force }
exit 0
'@
    $dataGuid=if($storageLayout -eq 'SPLIT') { ([string](Get-Partition -DiskNumber $identity.number -PartitionNumber 5).Guid).Trim('{}') } else {''}
    if($storageLayout -eq 'SPLIT' -and $dataGuid -notmatch '^[a-fA-F0-9-]{36}$') { throw 'Invalid data partition identity.' }
    $bootScript.Replace('__BASE__',$BaseUrl).Replace('__DATA_GUID__',$dataGuid).Replace('__HIDE_C__',([string]$hideSystemDrive).ToLowerInvariant()) | Set-Content "$work/Windows-Started.ps1" -Encoding UTF8
    @'
<?xml version="1.0" encoding="utf-8"?>
<unattend xmlns="urn:schemas-microsoft-com:unattend" xmlns:wcm="http://schemas.microsoft.com/WMIConfig/2002/State">
  <settings pass="specialize">
    <component name="Microsoft-Windows-Deployment" processorArchitecture="amd64" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS">
      <RunSynchronous><RunSynchronousCommand wcm:action="add"><Order>1</Order><Description>PocketInstall startup signal</Description><Path>cmd.exe /c powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%SystemDrive%\PocketInstall\Windows-Started.ps1"</Path></RunSynchronousCommand></RunSynchronous>
    </component>
  </settings>
</unattend>
'@ | Set-Content "$panther/unattend.xml" -Encoding UTF8
    Remove-Item -LiteralPath $image -Force
    Save-NativeLog
    Stop-Transcript | Out-Null
    Send-Report 'prepared' $null 'Windows applique, boot UEFI et WinRE prepares. Premier demarrage Windows a confirmer sur le PC.'
    if($storageLayout -eq 'SPLIT') { Write-Host 'Au demarrage Windows: C: pour Windows, D: Mes fichiers. Dossiers personnels sur C:; choisis D: pour tes fichiers et jeux.' }
    Write-Host 'Windows est installe sur le disque. Le premier demarrage et OOBE restent a verifier.'
    Write-Host 'Redemarre sur le disque interne (pas PXE). Termine la configuration Windows et active avec ta licence.'
    if((Read-Host 'Appuie sur Entree pour redemarrer, ou tape RESTER pour garder la console') -eq '') { & wpeutil.exe Reboot }
} catch {
    $failure=$_.Exception.Message
    Send-Report 'error' $null $failure
    Write-Host "Installation arretee: $failure" -ForegroundColor Red
    Save-NativeLog
    Write-Host 'Ne relance pas un effacement sans verifier le disque. Journaux: X:\Windows\Logs\DISM et W:\PocketInstall si disponible.'
    try { Stop-Transcript | Out-Null } catch {}
}
