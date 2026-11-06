$ips = @('192.168.1.13', '192.168.1.11')
foreach ($ip in $ips) {
    Write-Output "--- Testing unlock on $ip ---"
    $body = @{ printerIp = $ip; mode = 'COPY'; username = 'chiran'; switchScreen = 'false' } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri 'http://localhost:8080/api/station/device-functions' -Method Post -Body $body -ContentType 'application/json'
    Write-Output ($res | ConvertTo-Json)
}
