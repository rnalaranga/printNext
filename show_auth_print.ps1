$auth = Get-Content -Path Authentication.html
$idx = 0
for ($i = 0; $i -lt $auth.Length; $i++) {
    if ($auth[$i] -match "SELAUTHPRINT") {
        $start = [Math]::Max(0, $i - 10)
        $end = [Math]::Min($auth.Length - 1, $i + 15)
        Write-Host "=== Found at line $i ==="
        for ($j = $start; $j -le $end; $j++) {
            Write-Host "$j : $($auth[$j])"
        }
    }
}
