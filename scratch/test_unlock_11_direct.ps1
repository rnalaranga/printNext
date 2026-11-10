. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$ip = '192.168.1.11'
$auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
$headers = @{ "csrfpId" = $auth.Cookie; "Cache-Control" = "no-cache" }

Write-Host "--- 1. Querying current targets on $ip ---"
$qXml = "<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings></AuthenticationSettings></Authentication></GetValue></DeviceInformationModel>"
$res1 = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session
[xml]$doc1 = $res1.Content
$targets1 = $doc1.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings.AuthenticatedTarget
Write-Host "Initial Targets on ${ip}:"
Write-Host "  copy: $($targets1.copy)"
Write-Host "  scan: $($targets1.scan)"
Write-Host "  openAccess: $($doc1.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings.openAccess)"

Write-Host "`n--- 2. Sending UNLOCK XML (copy=false, scan=false) to $ip ---"
$setXml = @"
<DeviceInformationModel>
<SetValue overrideDelta='false'>
<Payload><path>Authentication/AuthenticationSettings/openAccess</path><value>1</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/functionwiseAuthentication</path><value>true</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>false</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/scan</path><value>false</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/fax</path><value>false</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/print</path><value>false</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/list</path><value>false</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/userFunctions</path><value>false</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/EWB</path><value>false</value></Payload>
</SetValue>
<Command>
<AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>
</Command>
</DeviceInformationModel>
"@

$resSet = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $setXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session
Write-Host "SetValue Response: $($resSet.Content)"

Write-Host "`n--- 3. Verifying updated targets on $ip ---"
$res2 = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session
[xml]$doc2 = $res2.Content
$targets2 = $doc2.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings.AuthenticatedTarget
Write-Host "Updated Targets on ${ip}:"
Write-Host "  copy: $($targets2.copy)"
Write-Host "  scan: $($targets2.scan)"
Write-Host "  openAccess: $($doc2.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings.openAccess)"
