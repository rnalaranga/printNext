$text = [System.IO.File]::ReadAllText("$PSScriptRoot\diag_388.json")
$matches = [regex]::Matches($text, "(/[A-Za-z0-9_/.-]+\.html?)")
$unique = $matches | ForEach-Object { $_.Value } | Select-Object -Unique
Write-Host "Found HTML pages in TopAccess:"
$unique | ForEach-Object { Write-Host "  $_" }
