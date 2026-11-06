$sess = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login-pin' -Method Post -Body '{"pin":"9999"}' -ContentType 'application/json' -WebSession $sess | Out-Null
$printers = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/printers' -WebSession $sess
$printers | Format-Table id, printerCode, name, ipAddress, model, deviceGeneration, seriesName
