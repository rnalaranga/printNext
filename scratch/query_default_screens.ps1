$printers = @('192.168.1.10', '192.168.1.11', '192.168.1.13')
foreach ($ip in $printers) {
    try {
        $hs = Invoke-WebRequest -Uri "http://$ip/" -UseBasicParsing -TimeoutSec 3
        $cookie = ($hs.Headers['Set-Cookie'] -split ';')[0]
        $sessVal = $cookie -replace 'Session=',''
        $loginXml = @"
<DeviceInformationModel>
<SetValue><Authentication><UserCredential><userName>admin</userName><passwd>123456</passwd><ipaddress>192.168.1.100</ipaddress><DepartmentManagement isEnable='false'><requireDepartment>false</requireDepartment></DepartmentManagement><domainName></domainName><applicationType>TOP_ACCESS</applicationType></UserCredential></SetValue>
<Command><Login><commandNode>Authentication/UserCredential</commandNode><Params><appName>TOPACCESS</appName></Params></Login></Command>
</DeviceInformationModel>
"@
        $loginResp = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $loginXml -ContentType "text/xml; charset=UTF-8" -Headers @{ 'Cookie' = $cookie; 'csrfpId' = $sessVal; 'Referer' = "http://$ip/admin/login.htm" } -TimeoutSec 3
        
        $qXml = @"
<DeviceInformationModel>
<GetValue><Controller><Settings><UI><DefaultScreen/></UI></Settings></Controller></GetValue>
</DeviceInformationModel>
"@
        $qResp = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qXml -ContentType "text/xml; charset=UTF-8" -Headers @{ 'Cookie' = $cookie; 'csrfpId' = $sessVal } -TimeoutSec 3
        Write-Output "Printer $ip -> $($qResp.Content)"
    } catch {
        Write-Output "Printer $ip -> Error: $_"
    }
}
