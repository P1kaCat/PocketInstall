[CmdletBinding()]
param([Parameter(Mandatory)][string]$BaseUrl)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$endpoint = [Uri]$BaseUrl
if ($endpoint.Scheme -ne 'http' -or $endpoint.Host -notmatch '^\d+\.\d+\.\d+\.\d+$' -or $endpoint.AbsolutePath -notmatch '^/[a-f0-9]{32}$') { throw 'Invalid session URL.' }

function Send-Report([string]$stage, $hardware = $null, [string]$message = '') {
    try {
        $body = [Text.Encoding]::UTF8.GetBytes((@{ stage=$stage; hardware=$hardware; message=$message.Substring(0,[Math]::Min(500,$message.Length)) } | ConvertTo-Json -Depth 6 -Compress))
        $request = [Net.HttpWebRequest]::Create("$BaseUrl/install/report")
        $request.Proxy=$null; $request.Method='POST'; $request.ContentType='application/json'; $request.ContentLength=$body.Length
        $request.Timeout=5000; $request.ReadWriteTimeout=5000
        $stream=$request.GetRequestStream(); try { $stream.Write($body,0,$body.Length) } finally { $stream.Dispose() }
        $response=$request.GetResponse(); $response.Dispose()
    } catch { Write-Host 'Statut non transmis au telephone; la console reste disponible.' }
}
function Invoke-Checked([string]$file, [string[]]$arguments) {
    & $file @arguments
    if ($LASTEXITCODE -ne 0) { throw "$file failed ($LASTEXITCODE). Installation interrupted; see console and logs." }
}
function Get-Sha256([string]$path) { (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Get-Hardware {
    $cpu = @(Get-CimInstance Win32_Processor)
    $system = Get-CimInstance Win32_ComputerSystem
    $tpm = 'unknown'; $secureBoot = 'unknown'
    try { $chip=Get-CimInstance -Namespace root/cimv2/security/microsofttpm -ClassName Win32_Tpm; if($chip) { $tpm=[string]$chip.SpecVersion } else { $tpm='absent' } } catch {}
    try { $secureBoot=[string](Confirm-SecureBootUEFI) } catch {}
    $disks = @(Get-Disk | Select-Object Number,FriendlyName,SerialNumber,UniqueId,Size,BusType,IsReadOnly,IsOffline)
    @{
        model=[string]$system.Model; cpu=($cpu.Name -join ', '); cores=[int](($cpu | Measure-Object NumberOfCores -Sum).Sum)
        ramBytes=[long]$system.TotalPhysicalMemory; gpu=(@(Get-CimInstance Win32_VideoController -ErrorAction SilentlyContinue).Name -join ', ')
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
    } finally { $output.Dispose() }
    if((Get-Sha256 $partial) -ne $sha256) { throw 'Image hash mismatch. Windows has not been applied.' }
    Move-Item -LiteralPath $partial -Destination $path -Force
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
    if($plan.schema -ne 1 -or $plan.index -lt 1 -or $plan.index -gt 64 -or $plan.bytes -lt 208 -or $plan.bytes -gt 16GB -or $plan.sha256 -notmatch '^[a-f0-9]{64}$' -or $plan.version -notin @('WINDOWS_10','WINDOWS_11') -or $plan.editionId -notin @('Core','Professional') -or $plan.debloat -notin @('NONE','LIGHT','CUSTOM','AUTO')) { throw 'Invalid installation plan.' }
    & wpeutil.exe UpdateBootInfo | Out-Null
    if((Get-ItemProperty HKLM:\SYSTEM\CurrentControlSet\Control).PEFirmwareType -ne 2) { throw 'UEFI boot required.' }
    if($plan.version -eq 'WINDOWS_11') {
        if($hardware.ramBytes -lt 4GB -or $hardware.cores -lt 2 -or $hardware.tpm -eq 'absent' -or ($hardware.tpm -ne 'unknown' -and $hardware.tpm -notmatch '2\.0')) { throw 'Windows 11: exigences RAM/CPU/TPM non satisfaites. Aucun disque modifie.' }
        Write-Host 'Windows 11: verifier aussi le processeur dans la liste Microsoft et la capacite Secure Boot. Aucun contournement applique.'
    }
    $profile=[string]$plan.debloat
    if($profile -eq 'AUTO') { $profile=if($hardware.ramBytes -lt 8GB -or $hardware.cores -le 2) { 'LIGHT' } else { 'NONE' } }
    Write-Host "Selection: $($plan.version) $($plan.editionId), image index $($plan.index), debloat $profile"
    Write-Host 'Installation neuve uniquement: toutes les partitions du disque choisi seront effacees. Sauvegarde tes fichiers avant de continuer.'
    $candidates=@(Get-Disk | Where-Object { !$_.IsReadOnly -and !$_.IsOffline -and $_.BusType -notin @('USB','SD','MMC') -and $_.Size -ge 64GB })
    if(!$candidates.Count) { throw 'Aucun disque interne accessible de 64 Go minimum. Aucun disque modifie.' }
    $candidates | Format-Table Number,FriendlyName,SerialNumber,@{Label='Go';Expression={[Math]::Round($_.Size/1GB,1)}} -AutoSize | Out-Host
    $numberText=Read-Host 'Numero du disque cible (vide pour annuler)'
    if($numberText -notmatch '^\d{1,4}$') { Write-Host 'Installation annulee. Aucun disque modifie.'; return }
    $disk=$candidates | Where-Object Number -eq ([int]$numberText) | Select-Object -First 1
    if(!$disk -or [string]::IsNullOrWhiteSpace([string]$disk.UniqueId)) { throw 'Disque non eligible ou identite non disponible.' }
    $needed=[Math]::Max(64GB,[long]$plan.bytes + 40GB + 18GB)
    if($disk.Size -lt $needed) { throw 'Espace insuffisant pour Windows et le fichier de transfert temporaire.' }
    # Never overwrite a drive-letter mapping belonging to a different disk.
    foreach($letter in @('S','W','R')) { if(Get-Volume -DriveLetter $letter -ErrorAction SilentlyContinue) { throw "La lettre $letter est deja utilisee. Aucun disque modifie." } }
    $identity=@{ number=[int]$disk.Number; uniqueId=[string]$disk.UniqueId; serial=[string]$disk.SerialNumber; size=[long]$disk.Size; model=[string]$disk.FriendlyName }
    Write-Host "EFFACEMENT: disque $($identity.number), $($identity.model), serie $($identity.serial), $([Math]::Round($identity.size/1GB,1)) Go."
    Write-Host 'Le transfert Windows commence apres le partitionnement. En cas de panne reseau, ce disque restera efface; la console et les journaux resteront disponibles.'
    $phrase="EFFACER $($identity.number)"
    if((Read-Host "Tape exactement '$phrase' pour confirmer") -cne $phrase) { Write-Host 'Annule. Aucun disque modifie.'; return }
    $check=Get-Disk -Number $identity.number
    if([string]$check.UniqueId -cne $identity.uniqueId -or [long]$check.Size -ne $identity.size -or [string]$check.SerialNumber -cne $identity.serial -or $check.IsReadOnly -or $check.IsOffline) { throw 'Identite du disque modifiee. Aucun disque modifie.' }
    # Verify the source server is still serving the same file before the first write.
    $probe=[Net.HttpWebRequest]::Create("$BaseUrl/install/image.wim"); $probe.Proxy=$null; $probe.Method='HEAD'; $probe.Timeout=5000
    $answer=$probe.GetResponse(); try { if($answer.ContentLength -ne [long]$plan.bytes) { throw 'Image source changed.' } } finally { $answer.Dispose() }
    Send-Report 'partitioning' $null "Disque $($identity.number): $($identity.model)"
    $layout=@"
select disk $($identity.number)
clean
convert gpt
create partition efi size=300
format quick fs=fat32 label=System
assign letter=S
create partition msr size=16
create partition primary
shrink minimum=2048
format quick fs=ntfs label=Windows
assign letter=W
create partition primary
format quick fs=ntfs label=Recovery
assign letter=R
set id=de94bba4-06d1-4d40-a16a-bfd50179d6ac
gpt attributes=0x8000000000000001
exit
"@
    $layout | Set-Content -LiteralPath X:\PocketInstall-layout.txt -Encoding ASCII
    Invoke-Checked diskpart.exe @('/s','X:\PocketInstall-layout.txt')
    foreach($letter in @('S','W','R')) { $partition=Get-Partition -DriveLetter $letter; if($partition.DiskNumber -ne $identity.number) { throw 'Unexpected partition mapping.' } }
    $work='W:\PocketInstall'; New-Item -ItemType Directory -Path $work | Out-Null
    Start-Transcript -Path "$work/install.log" -Force | Out-Null
    Send-Report 'downloading'
    $image="$work/install.wim"; Receive-Image $image ([long]$plan.bytes) ([string]$plan.sha256)
    Send-Report 'verifying'
    $info=Get-WindowsImage -ImagePath $image -Index ([int]$plan.index)
    if([int]$info.Architecture -ne 9 -or [string]$info.EditionId -cne [string]$plan.editionId -or ($plan.version -eq 'WINDOWS_11' -and $info.Version.Build -lt 22000) -or ($plan.version -eq 'WINDOWS_10' -and ($info.Version.Build -lt 10240 -or $info.Version.Build -ge 22000))) { throw 'Edition/version/architecture does not match the selected image.' }
    New-Item -ItemType Directory -Path "$work/scratch" | Out-Null
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
    Remove-Item -LiteralPath $image -Force
    Stop-Transcript | Out-Null
    Send-Report 'prepared' $null 'Windows applique, boot UEFI et WinRE prepares. Premier demarrage Windows a confirmer sur le PC.'
    Write-Host 'Windows est installe sur le disque. Le premier demarrage et OOBE restent a verifier.'
    Write-Host 'Redemarre sur le disque interne (pas PXE). Termine la configuration Windows et active avec ta licence.'
    if((Read-Host 'Appuie sur Entree pour redemarrer, ou tape RESTER pour garder la console') -eq '') { & wpeutil.exe Reboot }
} catch {
    Write-Host "Installation arretee: $($_.Exception.Message)" -ForegroundColor Red
    Send-Report 'error' $null $_.Exception.Message
    Write-Host 'Ne relance pas un effacement sans verifier le disque. Journaux: X:\Windows\Logs\DISM et W:\PocketInstall si disponible.'
    try { Stop-Transcript | Out-Null } catch {}
}
