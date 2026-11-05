. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$ip = '192.168.1.11'
$auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
$headers = @{ "csrfpId" = $auth.Cookie; "Cache-Control" = "no-cache" }

# Test 1: SetValue only, no Command
$xml1 = @"
<DeviceInformationModel>
  <SetValue overrideDelta="false">
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>false</value></Payload>
  </SetValue>
</DeviceInformationModel>
"@

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$res1 = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $xml1 -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session -TimeoutSec 10
$sw.Stop()
Write-Host "Test 1 (SetValue only): $($sw.ElapsedMilliseconds)ms -> $($res1.Content)"

# Check if it actually took effect:
$qXml = "<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings></AuthenticationSettings></Authentication></GetValue></DeviceInformationModel>"
$resQ = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session -TimeoutSec 10
[xml]$docQ = $resQ.Content
Write-Host "Copy target is now: $($docQ.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings.AuthenticatedTarget.copy)"
