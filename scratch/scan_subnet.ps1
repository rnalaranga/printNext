1..20 | ForEach-Object {
    $ip = "192.168.1.$_"
    $ping = Test-Connection -ComputerName $ip -Count 1 -Quiet
    if ($ping) { Write-Output "ONLINE: $ip" }
}
