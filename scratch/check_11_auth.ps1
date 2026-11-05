. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$auth = Invoke-TopAccessLogin -ip '192.168.1.11' -user 'admin' -pass '123456'
$headers = @{ "csrfpId" = $auth.Cookie; "Cache-Control" = "no-cache" }
$qXml = "<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings></AuthenticationSettings></Authentication></GetValue></DeviceInformationModel>"
$res = Invoke-WebRequest -Uri "http://192.168.1.11/contentwebserver" -Method Post -Body $qXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session
[xml]$doc = $res.Content
$s = $doc.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings
Write-Host "openAccess: $($s.openAccess)"
Write-Host "functionwiseAuthentication: $($s.functionwiseAuthentication)"
Write-Host "authenticationName: $($s.AuthenticationType.authenticationName)"
Write-Host "AuthenticatedTarget:"
$s.AuthenticatedTarget | Format-List
