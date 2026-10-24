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

# Candidate CWS commands to switch panel screen
$testCommands = @(
    "<Command><ChangeScreen><commandNode>Panel</commandNode><Params><screen>Copy</screen></Params></ChangeScreen></Command>",
    "<Command><FunctionClear><commandNode>Panel</commandNode></FunctionClear></Command>",
    "<Command><Reset><commandNode>Panel</commandNode></Reset></Command>",
    "<Command><AutoClear><commandNode>Panel</commandNode></AutoClear></Command>",
    "<Command><ScreenChange><commandNode>Controller</commandNode><Params><target>Copy</target></Params></ScreenChange></Command>"
)

foreach ($cmd in $testCommands) {
    $xml = "<DeviceInformationModel>$cmd</DeviceInformationModel>"
    try {
        $r = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $xml -ContentType "text/xml; charset=UTF-8" -Headers @{ "Cookie" = $cookie; "csrfpId" = $sessVal } -TimeoutSec 2
        Write-Output "Cmd: $cmd -> Status: $($r.StatusCode) Body: $($r.Content)"
    } catch {
        Write-Output "Cmd: $cmd -> Error: $_"
    }
}
