$body = @{ printerCode = 'PCT0002'; printerIp = '192.168.1.11'; reason = 'USER_LOGOUT' } | ConvertTo-Json
$res = Invoke-RestMethod -Uri 'http://localhost:8080/api/station/auto-lock' -Method Post -Body $body -ContentType 'application/json'
Write-Host "Auto-lock response: $($res | ConvertTo-Json)"

Start-Sleep -Seconds 3
. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$auth = Invoke-TopAccessLogin -ip '192.168.1.11' -user 'admin' -pass '123456'
$status = Get-ToshibaAuthStatus -ip '192.168.1.11' -authContext $auth
Write-Host "`nStatus after lock on 192.168.1.11:"
$status | Format-List
