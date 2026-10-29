$hostName = "qoa.lk"
$ports = @(25, 465, 587, 993, 995, 143, 110)
foreach ($p in $ports) {
    $t = Test-NetConnection -ComputerName $hostName -Port $p -WarningAction SilentlyContinue
    Write-Host "Port $p : $($t.TcpTestSucceeded)"
}
