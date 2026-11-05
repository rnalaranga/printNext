. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly

$printers = @('192.168.1.10', '192.168.1.11', '192.168.1.12', '192.168.1.13')
foreach ($ip in $printers) {
    Write-Host "=== Querying $ip ==="
    try {
        $auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
        $headers = @{ "csrfpId" = $auth.Cookie; "Cache-Control" = "no-cache" }
        
        # Query UI DefaultScreen
        $uiXml = "<DeviceInformationModel><GetValue><Controller><Settings><UI><DefaultScreen/></UI></Settings></Controller></GetValue></DeviceInformationModel>"
        $resUi = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $uiXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session -TimeoutSec 5
        [xml]$docUi = $resUi.Content
        $defScreen = $docUi.DeviceInformationModel.GetValue.Controller.Settings.UI.DefaultScreen
        Write-Host "DefaultScreen: $defScreen"

        # Query Auth
        $authXml = "<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings></AuthenticationSettings></Authentication></GetValue></DeviceInformationModel>"
        $resAuth = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $authXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session -TimeoutSec 5
        [xml]$docAuth = $resAuth.Content
        $target = $docAuth.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings.AuthenticatedTarget
        Write-Host "Auth copy: $($target.copy), scan: $($target.scan), openAccess: $($docAuth.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings.openAccess)"

        # Query EWBSettings
        $ewbXml = "<DeviceInformationModel><Command><GetEWBConfiguration><commandNode>EWBSettings</commandNode></GetEWBConfiguration></Command><GetValue><EWBSettings/></GetValue></DeviceInformationModel>"
        try {
            $resEwb = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $ewbXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session -TimeoutSec 5
            [xml]$docEwb = $resEwb.Content
            $ewbSet = $docEwb.DeviceInformationModel.GetValue.EWBSettings
            Write-Host "EWBSettings: DefaultScreen=$($ewbSet.DefaultScreen), AutoScreenChange=$($ewbSet.AutoScreenChange), AutoClearTimer=$($ewbSet.AutoClearTimer)"
        } catch {
            Write-Host "EWBSettings: not supported or error: $_"
        }
    } catch {
        Write-Host "Error on $ip : $_"
    }
}
