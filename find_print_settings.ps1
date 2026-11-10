$json = Get-Content "$PSScriptRoot\diag_388.json" -Raw | ConvertFrom-Json
$keys = @("AuthSettings", "UI", "EWBSettings", "Functions", "Panel", "Network", "AdminSystemSettings")
foreach ($k in $keys) {
    $val = $json.$k
    if ($val) {
        $matches = [regex]::Matches($val, "<[^>]*?(?:Print|USB|Storage|Direct)[^>]*?>.*?</[^>]*?>")
        Write-Host "=== $k ($($matches.Count) matches) ==="
        foreach ($m in $matches) {
            if ($m.Value.Length -lt 200 -and $m.Value -notmatch "xmlns") {
                Write-Host "  $($m.Value)"
            }
        }
    }
}
