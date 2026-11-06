. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1"

function Get-ToshibaStatus {
    param($ip)
    $auth = Invoke-TopAccessLogin -ip $ip -user "admin" -pass "123456"
    $h = @{ "csrfpId" = $auth.Cookie; "Cache-Control" = "no-cache" }

    $qScreen = '<DeviceInformationModel><GetValue><Controller><Settings><UI><DefaultScreen/></UI></Settings></Controller></GetValue></DeviceInformationModel>'
    $rScreen = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qScreen -ContentType "text/plain; charset=utf-8" -Headers $h -WebSession $auth.Session -TimeoutSec 6
    [xml]$docScreen = $rScreen.Content
    $screen = $docScreen.DeviceInformationModel.GetValue.Controller.Settings.UI.DefaultScreen

    $qAuth = '<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings></AuthenticationSettings></Authentication></GetValue></DeviceInformationModel>'
    $rAuth = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qAuth -ContentType "text/plain; charset=utf-8" -Headers $h -WebSession $auth.Session -TimeoutSec 6
    [xml]$docAuth = $rAuth.Content
    $copy = $docAuth.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings.AuthenticatedTarget.copy

    return [PSCustomObject]@{
        DefaultScreen = $screen
        CopyPadlocked = ($copy -eq "true")
    }
}

$st = Get-ToshibaStatus -ip "192.168.1.11"
Write-Host "DefaultScreen: $($st.DefaultScreen)"
Write-Host "CopyPadlocked: $($st.CopyPadlocked)"
