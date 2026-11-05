. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly

$ip = '192.168.1.11'
$session = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
$dev = Get-TopAccessDeviceInfo -ip $ip -authContext $session
Write-Host "IP: $ip"
Write-Host "UserAuthEnabled:  $($dev.Authentication.UserAuthEnabled)"
Write-Host "FunctionWiseAuth: $($dev.Authentication.FunctionWiseAuth)"
Write-Host "CopyRestricted:   $($dev.Authentication.CopyRestricted)"
Write-Host "ScanRestricted:   $($dev.Authentication.ScanRestricted)"
Write-Host "PrintRestricted:  $($dev.Authentication.PrintRestricted)"
Write-Host "EWBRestricted:    $($dev.Authentication.EWBRestricted)"
Write-Host "OpenAccess:       $($dev.Authentication.OpenAccess)"
Write-Host "DefaultScreen:    $($dev.Authentication.DefaultScreen)"

$ewb = Get-TopAccessEwbSettings -ip $ip -authContext $session
Write-Host "`nEWB Settings:"
Write-Host "DefaultScreen:    $($ewb.DefaultScreen)"
Write-Host "AutoScreenChange: $($ewb.AutoScreenChange)"
Write-Host "AutoClearTimer:   $($ewb.AutoClearTimer)"
Write-Host "UrlAddress:       $($ewb.UrlAddress)"
