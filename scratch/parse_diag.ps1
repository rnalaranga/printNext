$j = Get-Content 'diagnose_2528.json' -Raw | ConvertFrom-Json
Write-Host "=== EWBSettings ==="
$j.EWBSettings
Write-Host "`n=== UI ==="
$j.UI
Write-Host "`n=== Panel ==="
$j.Panel
