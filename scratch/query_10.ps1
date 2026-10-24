$ip = '192.168.1.10'
$adminUser = 'admin'
$adminPass = '123456'

try {
    $hs = Invoke-WebRequest -Uri "http://$ip/" -UseBasicParsing -TimeoutSec 3
    $cookie = ($hs.Headers["Set-Cookie"] -split ";")[0]
    $sessVal = $cookie -replace "Session=",""
    $clientIp = ($sessVal -split "~")[0]
    if (!$clientIp) { $clientIp = "192.168.1.100" }

    $loginXml = @"
<DeviceInformationModel>
<GetValue><Authentication><UserCredential></UserCredential></Authentication></GetValue>
<GetValue><Panel><DiagnosticMode><Mode_08><Code_8913></Code_8913></Mode_08></DiagnosticMode></Panel></GetValue>
<SetValue><Authentication><UserCredential>
<userName>$adminUser</userName>
<passwd>$adminPass</passwd>
<ipaddress>$clientIp</ipaddress>
<DepartmentManagement isEnable='false'><requireDepartment>false</requireDepartment></DepartmentManagement>
<domainName></domainName><applicationType>TOP_ACCESS</applicationType>
</UserCredential></SetValue>
<Command><Login><commandNode>Authentication/UserCredential</commandNode><Params><appName>TOPACCESS</appName></Params></Login></Command>
<SaveSessionInformation><SessionInfo>
<Information><type>LoginPassword</type><data>$adminPass</data></Information>
<Information><type>LoginUser</type><data>$adminUser</data></Information>
</SessionInfo></SaveSessionInformation>
</DeviceInformationModel>
"@

    $loginResp = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $loginXml -ContentType "text/xml; charset=UTF-8" -Headers @{ "Cookie" = $cookie; "csrfpId" = $sessVal; "Referer" = "http://$ip/Administration/AdminLogin.html" } -TimeoutSec 3

    $qXml = @"
<DeviceInformationModel>
<GetValue>
<Authentication><AuthenticationSettings></AuthenticationSettings></Authentication>
</GetValue>
</DeviceInformationModel>
"@

    $qResp = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qXml -ContentType "text/xml; charset=UTF-8" -Headers @{ "Cookie" = $cookie; "csrfpId" = $sessVal } -TimeoutSec 3
    Write-Output $qResp.Content
} catch {
    Write-Output "Error: $($_.Exception.Message)"
}
