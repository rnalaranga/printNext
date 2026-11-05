$j = Get-Content 'diagnose_2528.json' -Raw | ConvertFrom-Json
foreach ($prop in $j.PSObject.Properties) {
    $val = $prop.Value
    if ($val -match 'AutoClear|AutoScreen|KeyMap|sleephook|EnergySaver') {
        Write-Host "=== Prop: $($prop.Name) ==="
        [regex]::Matches($val, '.{0,40}(AutoClear|AutoScreen|KeyMap|sleephook|EnergySaver).{0,40}') | ForEach-Object { Write-Host "   $($_.Value)" }
    }
}
