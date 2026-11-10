. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$ip = '192.168.1.11'
$auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
$headers = @{ 
    "csrfpId" = $auth.Cookie
    "Content-Type" = "text/xml; charset=UTF-8"
    "Referer" = "http://$ip/Administration/Setup/General.html"
}

$setUiXml = @"
<DeviceInformationModel>
<Command>
<Set>
<commandNode>Controller</commandNode>
<SetValue>
<Controller>
<Settings>
<UI>
<DefaultScreen>Copy</DefaultScreen>
</UI>
</Settings>
</Controller>
</SetValue>
</Set>
</Command>
</DeviceInformationModel>
"@

Write-Host "Sending Set DefaultScreen=Copy to $ip..."
$res = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $setUiXml -Headers $headers -WebSession $auth.Session -TimeoutSec 10
Write-Host "StatusCode: $($res.StatusCode)"
Write-Host "Content: $($res.Content)"

$qScreen = '<DeviceInformationModel><GetValue><Controller><Settings><UI><DefaultScreen/></UI></Settings></Controller></GetValue></DeviceInformationModel>'
$rScreen = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qScreen -ContentType "text/plain; charset=utf-8" -Headers @{ "csrfpId" = $auth.Cookie } -WebSession $auth.Session -TimeoutSec 10
Write-Host "Query Result: $($rScreen.Content)"
