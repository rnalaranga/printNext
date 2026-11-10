. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly

function Get-FullAuth($ip) {
    $auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
    $h = @{ "csrfpId" = $auth.Cookie; "Cache-Control" = "no-cache" }
    
    $qAuth = '<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings></AuthenticationSettings></Authentication></GetValue></DeviceInformationModel>'
    $rAuth = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qAuth -ContentType "text/plain; charset=utf-8" -Headers $h -WebSession $auth.Session -TimeoutSec 8
    
    $qUi = '<DeviceInformationModel><GetValue><Controller><Settings><UI></UI></Settings></Controller></GetValue></DeviceInformationModel>'
    $rUi = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qUi -ContentType "text/plain; charset=utf-8" -Headers $h -WebSession $auth.Session -TimeoutSec 8

    return @{
        Auth = $rAuth.Content
        UI   = $rUi.Content
    }
}

Write-Host "Fetching 18-Series (192.168.1.15)..."
$s18 = Get-FullAuth -ip '192.168.1.15'

Write-Host "Fetching 28-Series (192.168.1.11)..."
$s28 = Get-FullAuth -ip '192.168.1.11'

Write-Host "`n================ 18-SERIES AUTH (192.168.1.15) ================"
[xml]$x18 = $s18.Auth
$t18 = $x18.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings
Write-Host "openAccess: $($t18.openAccess)"
Write-Host "functionwise: $($t18.functionwiseAuthentication)"
Write-Host "requireTrustedUser: $($t18.requireTrustedUser)"
Write-Host "allowGuestUser: $($t18.allowGuestUser)"
Write-Host "AuthType: $($t18.AuthenticationType.authenticationName)"
$t18.AuthenticatedTarget | Format-List

Write-Host "`n================ 28-SERIES AUTH (192.168.1.11) ================"
[xml]$x28 = $s28.Auth
$t28 = $x28.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings
Write-Host "openAccess: $($t28.openAccess)"
Write-Host "functionwise: $($t28.functionwiseAuthentication)"
Write-Host "requireTrustedUser: $($t28.requireTrustedUser)"
Write-Host "allowGuestUser: $($t28.allowGuestUser)"
Write-Host "AuthType: $($t28.AuthenticationType.authenticationName)"
$t28.AuthenticatedTarget | Format-List
