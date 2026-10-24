$printers = @("192.168.1.10", "192.168.1.15")

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

        # Set DefaultScreen = EWB
        $setUiXml = @"
<DeviceInformationModel>
<Command><Set><commandNode>Controller</commandNode>
<SetValue><Controller><Settings><UI><DefaultScreen>EWB</DefaultScreen></UI></Settings></Controller></SetValue>
</Set></Command>
</DeviceInformationModel>
"@
        $uiResp = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $setUiXml -ContentType "text/xml; charset=UTF-8" -Headers @{ "Cookie" = $cookie; "csrfpId" = $sessVal; "Referer" = "http://$ip/Administration/Setup/General.html" } -TimeoutSec 3

        Write-Output "Printer $ip reset to DefaultScreen = EWB: Status $($uiResp.StatusCode)"
    } catch {
        Write-Output "Error resetting: $_"
    }
}
