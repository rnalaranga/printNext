$ip = "192.168.1.10"
try {
    $hs = Invoke-WebRequest -Uri "http://$ip/" -UseBasicParsing -TimeoutSec 3
    Write-Output "HTTP status: $($hs.StatusCode)"
} catch {
    Write-Output "HTTP error: $_"
}
