. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$ip = '192.168.1.11'
$auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
Write-Host 'Before unlock:'
$st1 = Get-ToshibaAuthStatus -ip $ip -authContext $auth
$st1 | Format-List

Write-Host 'Sending Unlock (lock=$false)...'
$res = Set-ToshibaKioskLock -ip $ip -authContext $auth -lock $false
Write-Host "Set-ToshibaKioskLock result: $res"

Write-Host 'After unlock:'
$st2 = Get-ToshibaAuthStatus -ip $ip -authContext $auth
$st2 | Format-List
