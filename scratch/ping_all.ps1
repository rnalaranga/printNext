foreach ($ip in @('192.168.1.10', '192.168.1.11', '192.168.1.12', '192.168.1.13')) {
    $p = Test-Connection -ComputerName $ip -Count 1 -Quiet
    Write-Output "$ip : $p"
}
