$b = @{
    printerCode = 'PCT0002'
    mode = 'COPY'
    switchScreen = 'true'
    username = 'chiran'
} | ConvertTo-Json

$resp = Invoke-RestMethod -Uri "http://localhost:8080/api/station/device-functions" -Method Post -Body $b -ContentType "application/json"
$resp | ConvertTo-Json
