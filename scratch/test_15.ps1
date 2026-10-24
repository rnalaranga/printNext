$ip = "192.168.1.15"
try {
    $hs = Invoke-WebRequest -Uri "http://$ip/" -UseBasicParsing -TimeoutSec 3
    Write-Output "192.168.1.15 HTTP: $($hs.StatusCode)"
} catch {
    Write-Output "192.168.1.15 HTTP Error: $_"
}
