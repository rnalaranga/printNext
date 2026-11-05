# 1. Trigger device-functions (mode=COPY)
$b = @{
    printerCode = 'PCT0002'
    mode = 'COPY'
    switchScreen = 'true'
    username = 'rr'
} | ConvertTo-Json

Write-Host "Triggering device-functions (mode=COPY)..."
$res = Invoke-RestMethod -Uri "http://localhost:8080/api/station/device-functions" -Method Post -Body $b -ContentType "application/json"
$res | ConvertTo-Json

Write-Host "`nWaiting 8 seconds for DefaultScreen to pre-restore to EWB..."
Start-Sleep -Seconds 8

. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$auth = Invoke-TopAccessLogin -ip '192.168.1.11' -user 'admin' -pass '123456'
$status = Get-ToshibaAuthStatus -ip '192.168.1.11' -authContext $auth
Write-Host "`nStatus on 192.168.1.11 after 8s:"
$status | Format-List
