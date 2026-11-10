$json = Get-Content "$PSScriptRoot\diag_388.json" -Raw | ConvertFrom-Json
Write-Host "=== UI ==="
if ($json.UI) { Write-Host $json.UI.Substring(0, [Math]::Min(1500, $json.UI.Length)) }
Write-Host "=== Functions ==="
if ($json.Functions) { Write-Host $json.Functions.Substring(0, [Math]::Min(1500, $json.Functions.Length)) }
