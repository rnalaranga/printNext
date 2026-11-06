$base = 'http://localhost:8080'
$body = '{"printerCode":"PCT0002","mode":"COPY","switchScreen":"false","username":"chiran"}'
$res = Invoke-RestMethod -Uri "$base/api/station/device-functions" -Method Post -ContentType 'application/json' -Body $body
Write-Output "Unlock result: $($res.success), message: $($res.message)"
