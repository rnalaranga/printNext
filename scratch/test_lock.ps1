$printers = @("192.168.1.15", "192.168.1.10")

foreach ($ip in $printers) {
    try {
        $hs = Invoke-WebRequest -Uri "http://$ip/" -UseBasicParsing -TimeoutSec 3
        $cookie = ($hs.Headers["Set-Cookie"] -split ";")[0]
        $sessVal = $cookie -replace "Session=",""

        $loginXml = @"
<DeviceInformationModel>
<GetValue><Authentication><UserCredential></UserCredential></Authentication></GetValue>
<SetValue><Authentication><UserCredential>
<userName>admin</userName>
<passwd>123456</passwd>
<ipaddress>192.168.1.100</ipaddress>
<DepartmentManagement isEnable='false'><requireDepartment>false</requireDepartment></DepartmentManagement>
<domainName></domainName><applicationType>TOP_ACCESS</applicationType>
</UserCredential></SetValue>
<Command><Login><commandNode>Authentication/UserCredential</commandNode><Params><appName>TOPACCESS</appName></Params></Login></Command>
</DeviceInformationModel>
"@

        $loginResp = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $loginXml -ContentType "text/xml; charset=UTF-8" -Headers @{ "Cookie" = $cookie; "csrfpId" = $sessVal; "Referer" = "http://$ip/admin/login.htm" } -TimeoutSec 3

        # Send Copy Restriction Lock XML
        $lockXml = @"
<DeviceInformationModel>
<SetValue overrideDelta='false'>
<Payload><path>Authentication/AuthenticationSettings/openAccess</path><value>1</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/functionwiseAuthentication</path><value>true</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/requireTrustedUser</path><value>true</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/unauthorizedPrintJob</path><value>Delete</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/allowGuestUser</path><value>false</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>true</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/scan</path><value>true</value></Payload>
<Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/EWB</path><value>false</value></Payload>
</SetValue>
<Command>
<AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>
</Command>
</DeviceInformationModel>
"@

        $lockResp = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $lockXml -ContentType "text/plain; charset=utf-8" -Headers @{ "Cookie" = $cookie; "csrfpId" = $sessVal; "Referer" = "http://$ip/Administration/Security/Authentication.html" } -TimeoutSec 3
        Write-Output "Printer $ip Lock Response: $($lockResp.Content)"

    } catch {
        Write-Output "Error on ${ip}: " + $_.Exception.Message
    }
}
