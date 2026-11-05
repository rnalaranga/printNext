. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$ip = '192.168.1.11'
$auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'

$sw = [System.Diagnostics.Stopwatch]::StartNew()
Set-ToshibaKioskLock -ip $ip -authContext $auth -lock $true
$sw.Stop()
Write-Host "Lock with existing session took: $($sw.ElapsedMilliseconds) ms"

$sw.Restart()
Set-ToshibaKioskLock -ip $ip -authContext $auth -lock $false
$sw.Stop()
Write-Host "Unlock with existing session took: $($sw.ElapsedMilliseconds) ms"
