. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly

$ip = '192.168.1.12'
$auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
$headers = @{ "csrfpId" = $auth.Cookie; "Cache-Control" = "no-cache" }

Write-Host "Provisioning User Authentication (openAccess=1, functionwise=true, copy/scan locked) on $ip..."

$setXml = @"
<DeviceInformationModel>
  <SetValue overrideDelta="false">
    <Payload><path>Authentication/AuthenticationSettings/openAccess</path><value>1</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/functionwiseAuthentication</path><value>true</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>true</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/scan</path><value>true</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/fax</path><value>true</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/print</path><value>false</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/list</path><value>true</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/userFunctions</path><value>true</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/EWB</path><value>false</value></Payload>
  </SetValue>
  <Command>
    <AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>
  </Command>
</DeviceInformationModel>
"@

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$res = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $setXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session -TimeoutSec 15
$sw.Stop()

Write-Host "Provision result: $($res.StatusCode), Time: $($sw.ElapsedMilliseconds)ms, Body: $($res.Content)"

# Query again to verify
Start-Sleep -Seconds 2
$qXml = "<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings></AuthenticationSettings></Authentication></GetValue></DeviceInformationModel>"
$resQ = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session -TimeoutSec 10
[xml]$docQ = $resQ.Content
$s = $docQ.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings
Write-Host "openAccess is now: $($s.openAccess)"
Write-Host "functionwiseAuthentication: $($s.functionwiseAuthentication)"
Write-Host "AuthenticatedTarget:"
$s.AuthenticatedTarget | Format-List
