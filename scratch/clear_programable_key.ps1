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

        # Clear ProgramableKey so key [1] is NOT hijacked by EWB!
        $clearKeyXml = @"
<DeviceInformationModel>
<Command><Set><commandNode>Controller</commandNode>
<SetValue><Controller><Settings>
<Functions>
<ProgramableKey>
<Key id='1'>
<function></function>
<option><Address></Address><AutoScreenChange>false</AutoScreenChange></option>
</Key>
</ProgramableKey>
</Functions>
</Settings></Controller></SetValue>
</Set></Command>
</DeviceInformationModel>
"@
        $keyResp = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $clearKeyXml -ContentType "text/xml; charset=UTF-8" -Headers @{ "Cookie" = $cookie; "csrfpId" = $sessVal; "Referer" = "http://$ip/Administration/Setup/General.html" } -TimeoutSec 3
        Write-Output "Printer $ip ProgramableKey cleared: $($keyResp.StatusCode)"
    } catch {
        Write-Output "Error clearing: $_"
    }
}
