. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$ip = '192.168.1.11'
$auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
$headers = @{ 
    "csrfpId" = $auth.Cookie
    "Content-Type" = "text/xml; charset=UTF-8"
    "Referer" = "http://$ip/Administration/Setup/General.html"
}

$setEwbXml = @"
<DeviceInformationModel>
<Command>
<Set>
<commandNode>Controller</commandNode>
<SetValue>
<Controller>
<Settings>
<UI>
<DefaultScreen>EWB</DefaultScreen>
</UI>
<Functions><ProgramableKey><Key id='1'><function></function><option><Address></Address><AutoScreenChange>false</AutoScreenChange></option></Key></ProgramableKey></Functions>
</Settings>
</Controller>
</SetValue>
</Set>
</Command>
</DeviceInformationModel>
"@

Write-Host "Sending Set DefaultScreen=EWB to $ip..."
$res = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $setEwbXml -Headers $headers -WebSession $auth.Session -TimeoutSec 10
Write-Host "StatusCode: $($res.StatusCode)"

$qScreen = '<DeviceInformationModel><GetValue><Controller><Settings><UI><DefaultScreen/></UI></Settings></Controller></GetValue></DeviceInformationModel>'
$rScreen = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qScreen -ContentType "text/plain; charset=utf-8" -Headers @{ "csrfpId" = $auth.Cookie } -WebSession $auth.Session -TimeoutSec 10
Write-Host "Query Result: $($rScreen.Content)"
