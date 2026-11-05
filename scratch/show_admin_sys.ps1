$j = Get-Content 'diagnose_2528.json' -Raw | ConvertFrom-Json
$adminSys = $j.AdminSystemSettings
[regex]::Matches($adminSys, '<[^>]+>') | Select-Object -First 50 | ForEach-Object { $_.Value }
