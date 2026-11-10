. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$ip = '192.168.1.11'
$auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
$headers = @{ "csrfpId" = $auth.Cookie; "Cache-Control" = "no-cache" }

$qUi = @"
<DeviceInformationModel>
<Command>
<Get>
<Controller>
<commandNode>Controller/Settings/UI</commandNode>
</Controller>
</Get>
</Command>
</DeviceInformationModel>
"@

$resUi = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qUi -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session -TimeoutSec 10
[xml]$doc = $resUi.Content
Write-Host "Current UI Settings on ${ip}:"
Write-Host "DefaultScreen: $($doc.DeviceInformationModel.GetValue.Controller.Settings.UI.DefaultScreen)"
$doc.DeviceInformationModel.GetValue.Controller.Settings.UI.ChildNodes | ForEach-Object {
    Write-Host "$($_.Name): $($_.InnerText)"
}
