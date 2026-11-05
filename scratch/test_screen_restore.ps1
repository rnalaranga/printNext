. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly

$ip = '192.168.1.10'
$auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'

Write-Host "1. Setting DefaultScreen to Copy on $ip..."
$setCopyXml = @"
<DeviceInformationModel>
  <Command><Set><commandNode>Controller</commandNode><SetValue>
    <Controller><Settings><UI><DefaultScreen>Copy</DefaultScreen></UI></Settings></Controller>
  </SetValue></Set></Command>
</DeviceInformationModel>
"@
$headers = @{ "csrfpId" = $auth.Cookie; "Cache-Control" = "no-cache" }
$res1 = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $setCopyXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session -TimeoutSec 10
Write-Host "Result 1: $($res1.StatusCode)"

Write-Host "Waiting 5 seconds..."
Start-Sleep -Seconds 5

Write-Host "2. Setting DefaultScreen back to EWB on $ip..."
$setEwbXml = @"
<DeviceInformationModel>
  <Command><Set><commandNode>Controller</commandNode><SetValue>
    <Controller><Settings><UI><DefaultScreen>EWB</DefaultScreen></UI></Settings></Controller>
  </SetValue></Set></Command>
</DeviceInformationModel>
"@
$res2 = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $setEwbXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session -TimeoutSec 10
Write-Host "Result 2: $($res2.StatusCode)"
