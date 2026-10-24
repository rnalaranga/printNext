$ip = "192.168.1.15"
$adminUser = "admin"
$adminPass = "123456"

$hs = Invoke-WebRequest -Uri "http://$ip/" -UseBasicParsing -TimeoutSec 3
$cookie = ($hs.Headers["Set-Cookie"] -split ";")[0]
$sessVal = $cookie -replace "Session=",""

$loginXml = @"
<DeviceInformationModel>
<GetValue><Authentication><UserCredential></UserCredential></Authentication></GetValue>
<SetValue><Authentication><UserCredential>
<userName>$adminUser</userName>
<passwd>$adminPass</passwd>
<ipaddress>192.168.1.100</ipaddress>
<DepartmentManagement isEnable='false'><requireDepartment>false</requireDepartment></DepartmentManagement>
<domainName></domainName><applicationType>TOP_ACCESS</applicationType>
</UserCredential></SetValue>
<Command><Login><commandNode>Authentication/UserCredential</commandNode><Params><appName>TOPACCESS</appName></Params></Login></Command>
</DeviceInformationModel>
"@

$loginResp = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $loginXml -ContentType "text/xml; charset=UTF-8" -Headers @{ "Cookie" = $cookie; "csrfpId" = $sessVal; "Referer" = "http://$ip/admin/login.htm" } -TimeoutSec 3

$queryXml = @"
<DeviceInformationModel>
<GetValue>
<Controller><Settings/></Controller>
</GetValue>
</DeviceInformationModel>
"@

$qResp = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $queryXml -ContentType "text/xml; charset=UTF-8" -Headers @{ "Cookie" = $cookie; "csrfpId" = $sessVal } -TimeoutSec 3
Write-Output $qResp.Content
