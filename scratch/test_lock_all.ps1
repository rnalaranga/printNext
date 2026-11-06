$ips = @('192.168.1.10', '192.168.1.11', '192.168.1.12', '192.168.1.13')
foreach ($ip in $ips) {
    Write-Output "--- Testing lock on $ip ---"
    $body = @{ printerIp = $ip; reason = 'AUTO_LOCK_VERIFY' } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri 'http://localhost:8080/api/station/auto-lock' -Method Post -Body $body -ContentType 'application/json'
    Write-Output ($res | ConvertTo-Json)
}
