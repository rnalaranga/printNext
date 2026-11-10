$ip = "192.168.1.12"
$xml = @"
<DeviceInformationModel>
<SetValue overrideDelta='false'>
<Payload><path>Authentication/AuthenticationSettings/openAccess</path><value>1</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/functionwiseAuthentication</path><value>true</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/AuthenticationType/primaryLdapID</path><value>0</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/primaryCardServerID</path><value>0</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/CardAuthentication/autoRegistrationForCardAuth</path><value>false</value></Payload>
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

$resp = Invoke-RestMethod -Uri "http://$ip/contentwebserver" -Method Post -Body $xml -ContentType "text/plain; charset=utf-8"
Write-Host "Set response: $resp"

$getXml = @"
<DeviceInformationModel>
<Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command>
<GetValue><Authentication><AuthenticationSettings/></Authentication></GetValue>
</DeviceInformationModel>
"@

$getResp = Invoke-RestMethod -Uri "http://$ip/contentwebserver" -Method Post -Body $getXml -ContentType "text/plain; charset=utf-8"
$idx = $getResp.IndexOf("AuthenticatedTarget")
if ($idx -ge 0) {
    Write-Host "Current AuthenticatedTarget:"
    Write-Host ($getResp.Substring($idx, 200))
}
