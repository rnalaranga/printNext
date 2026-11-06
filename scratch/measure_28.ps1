$sw = [System.Diagnostics.Stopwatch]::StartNew()
$body = @{ printerIp = '192.168.1.11'; mode = 'COPY'; username = 'chiran'; switchScreen = 'false' } | ConvertTo-Json
$res = Invoke-RestMethod -Uri 'http://localhost:8080/api/station/device-functions' -Method Post -Body $body -ContentType 'application/json'
$sw.Stop()
Write-Output "Unlock took $($sw.ElapsedMilliseconds)ms: $($res | ConvertTo-Json -Compress)"

Start-Sleep -Seconds 1

$sw2 = [System.Diagnostics.Stopwatch]::StartNew()
$lockBody = @{ printerIp = '192.168.1.11'; reason = 'SPEED_TEST' } | ConvertTo-Json
$res2 = Invoke-RestMethod -Uri 'http://localhost:8080/api/station/auto-lock' -Method Post -Body $lockBody -ContentType 'application/json'
$sw2.Stop()
Write-Output "Lock took $($sw2.ElapsedMilliseconds)ms: $($res2 | ConvertTo-Json -Compress)"
