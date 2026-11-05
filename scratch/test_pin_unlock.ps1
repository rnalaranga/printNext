# Simulate PIN login for chiran on PCT0002 (192.168.1.11)
$body = @{
    pin = '1234'
    printerCode = 'PCT0002'
    printerIp = '192.168.1.11'
} | ConvertTo-Json

Write-Host "Sending PIN login for PCT0002..."
$res = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body $body -ContentType "application/json"
$res | ConvertTo-Json -Depth 3

# Wait 3 seconds for async unlock to settle
Start-Sleep -Seconds 3

# Check if TopAccess on 192.168.1.11 is now unlocked (CopyRestricted: False)
. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$auth = Invoke-TopAccessLogin -ip '192.168.1.11' -user 'admin' -pass '123456'
$status = Get-ToshibaAuthStatus -ip '192.168.1.11' -authContext $auth
Write-Host "`nPost-Login Status on 192.168.1.11:"
$status | Format-List
