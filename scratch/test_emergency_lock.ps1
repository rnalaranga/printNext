$b = @{
    printerCode = 'PCT0002'
    reason = 'TEST_LOCK'
} | ConvertTo-Json

$resp = Invoke-RestMethod -Uri "http://localhost:8080/api/station/auto-lock" -Method Post -Body $b -ContentType "application/json"
$resp | ConvertTo-Json
