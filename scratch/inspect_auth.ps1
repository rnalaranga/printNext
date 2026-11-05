. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly

foreach ($ip in @('192.168.1.10', '192.168.1.11')) {
    Write-Host "`n=== $ip AuthenticationSettings ==="
    $auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
    $headers = @{ "csrfpId" = $auth.Cookie; "Cache-Control" = "no-cache" }

    $qXml = @"
<DeviceInformationModel>
<Command>
  <GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings>
</Command>
<GetValue>
  <Authentication><AuthenticationSettings></AuthenticationSettings></Authentication>
</GetValue>
</DeviceInformationModel>
"@

    $res = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session -TimeoutSec 10
    Write-Host $res.Content
}
