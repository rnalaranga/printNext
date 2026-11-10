. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$ip = '192.168.1.11'
$apiUrl = 'http://localhost:8080'

Write-Host "=== 1. Checking Initial State on $ip ==="
$auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
$st0 = Get-ToshibaAuthStatus -ip $ip -authContext $auth
Write-Host "Initial CopyRestricted: $($st0.CopyRestricted)"

Write-Host "`n=== 2. Triggering /api/station/device-functions (COPY) ==="
$body = @{
    printerCode = 'PCT0002'
    printerIp = $ip
    mode = 'COPY'
    username = 'chiran'
    switchScreen = 'false'
} | ConvertTo-Json

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$res = Invoke-RestMethod -Uri "$apiUrl/api/station/device-functions" -Method Post -Body $body -ContentType 'application/json'
$sw.Stop()
Write-Host "API Time: $($sw.ElapsedMilliseconds)ms"
Write-Host "API Result: $($res | ConvertTo-Json)"

Write-Host "`n=== 3. Checking Hardware State immediately after API call ==="
$st1 = Get-ToshibaAuthStatus -ip $ip -authContext $auth
Write-Host "Hardware CopyRestricted immediately after API: $($st1.CopyRestricted)"

Write-Host "`n=== 4. Waiting 15 seconds... ==="
Start-Sleep -Seconds 15
$st2 = Get-ToshibaAuthStatus -ip $ip -authContext $auth
Write-Host "Hardware CopyRestricted at T+15s: $($st2.CopyRestricted)"

Write-Host "`n=== 5. Waiting another 15 seconds (T+30s)... ==="
Start-Sleep -Seconds 15
$st3 = Get-ToshibaAuthStatus -ip $ip -authContext $auth
Write-Host "Hardware CopyRestricted at T+30s: $($st3.CopyRestricted)"
