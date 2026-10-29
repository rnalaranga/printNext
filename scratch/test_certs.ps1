function Get-CertDetails {
    param([string]$HostName, [int]$Port)
    Write-Host "Connecting to $HostName : $Port..."
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient($HostName, $Port)
        $ssl = New-Object System.Net.Security.SslStream($tcp.GetStream(), $false, {
            param($s, $c, $chain, $errors)
            Write-Host "  Subject: $($c.Subject)"
            Write-Host "  Issuer: $($c.Issuer)"
            Write-Host "  Errors: $errors"
            return $true
        })
        $protocols = [System.Security.Authentication.SslProtocols]::Tls12
        $ssl.AuthenticateAsClient($HostName, $null, $protocols, $false)
        Write-Host "  SSL Negotiated successfully! Protocol: $($ssl.SslProtocol)"
        $tcp.Close()
    } catch {
        Write-Host "  SSL Handshake Failed: $($_.Exception.Message)"
        if ($_.Exception.InnerException) {
            Write-Host "  Inner: $($_.Exception.InnerException.Message)"
        }
    }
}

Write-Host "=== PORT 465 (SMTPS) ==="
Get-CertDetails -HostName "qoa.lk" -Port 465
Get-CertDetails -HostName "mail.qoa.lk" -Port 465
Get-CertDetails -HostName "premium76.web-hosting.com" -Port 465

Write-Host "`n=== PORT 993 (IMAPS) ==="
Get-CertDetails -HostName "qoa.lk" -Port 993
Get-CertDetails -HostName "mail.qoa.lk" -Port 993
Get-CertDetails -HostName "premium76.web-hosting.com" -Port 993

Write-Host "`n=== PORT 995 (POP3S) ==="
Get-CertDetails -HostName "qoa.lk" -Port 995
Get-CertDetails -HostName "mail.qoa.lk" -Port 995
