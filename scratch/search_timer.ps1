$j = Get-Content 'diagnose_2528.json' -Raw | ConvertFrom-Json
foreach ($prop in $j.PSObject.Properties) {
    $val = $prop.Value
    if ($val -match 'Timer') {
        Write-Host "=== Prop: $($prop.Name) ==="
        [regex]::Matches($val, '<[^>]*Timer[^>]*>[^<]*<[^>]*>') | ForEach-Object { Write-Host "   $($_.Value)" }
    }
}
