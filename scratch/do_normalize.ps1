$p = 'C:\Users\Administrator\Documents\GitHub\printNext\rr-secure-print-web\js\ewb-app.js'
$t = [System.IO.File]::ReadAllText($p)
$n = $t.Replace("`r`n", "`n")
[System.IO.File]::WriteAllText($p, $n, [System.Text.Encoding]::UTF8)
Write-Output "Normalized ewb-app.js to LF"
