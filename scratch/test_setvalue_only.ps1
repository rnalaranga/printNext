. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$auth = Invoke-TopAccessLogin -ip '192.168.1.11' -user 'admin' -pass '123456'
$headers = @{ 'csrfpId' = $auth.Cookie; 'Cache-Control' = 'no-cache' }

$xmlNoCmd = @"
<DeviceInformationModel>
  <SetValue overrideDelta='false'>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>false</value></Payload>
  </SetValue>
</DeviceInformationModel>
"@

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$res = Invoke-WebRequest -Uri 'http://192.168.1.11/contentwebserver' -Method Post -Body $xmlNoCmd -ContentType 'text/plain; charset=utf-8' -Headers $headers -WebSession $auth.Session -TimeoutSec 10
$sw.Stop()
Write-Host "SetValue only time: $($sw.ElapsedMilliseconds)ms, Body: $($res.Content)"

$q = '<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings><AuthenticatedTarget></AuthenticatedTarget></AuthenticationSettings></Authentication></GetValue></DeviceInformationModel>'
$chk = Invoke-WebRequest -Uri 'http://192.168.1.11/contentwebserver' -Method Post -Body $q -ContentType 'text/plain; charset=utf-8' -Headers $headers -WebSession $auth.Session -TimeoutSec 10
Write-Host "Check Target: $($chk.Content)"
