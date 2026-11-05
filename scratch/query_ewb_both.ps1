. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly

$ips = @('192.168.1.10', '192.168.1.11')

foreach ($ip in $ips) {
    Write-Host "`n==============================================="
    Write-Host "Querying MFP: $ip"
    Write-Host "==============================================="
    try {
        $auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
        $headers = @{ "csrfpId" = $auth.Cookie; "Cache-Control" = "no-cache" }
        
        # 1. Query EWBSettings
        $ewbXml = "<DeviceInformationModel><GetValue><EWBSettings/></GetValue></DeviceInformationModel>"
        $resEwb = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $ewbXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session -TimeoutSec 5
        [xml]$docEwb = $resEwb.Content
        $ewbSettings = $docEwb.DeviceInformationModel.GetValue.EWBSettings
        Write-Host "EWBSettings:"
        $ewbSettings | Format-List UrlAddress, DefaultScreen, AutoScreenChange, AutoClearTimer

        # 2. Query Controller UI Settings
        $uiXml = "<DeviceInformationModel><GetValue><Controller><Settings><UI/></Settings></Controller></GetValue></DeviceInformationModel>"
        $resUi = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $uiXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session -TimeoutSec 5
        [xml]$docUi = $resUi.Content
        $uiSettings = $docUi.DeviceInformationModel.GetValue.Controller.Settings.UI
        Write-Host "Controller UI Settings:"
        $uiSettings | Format-List DefaultScreen, Language, DisplayLanguage
    } catch {
        Write-Host "Error querying $ip : $_"
    }
}
