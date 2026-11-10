$header = curl.exe -s -i http://192.168.1.12/
$cookie = ($header | Select-String "Set-Cookie:\s*(Session=[^;]+)").Matches.Groups[1].Value
$csrf = $cookie.Replace("Session=", "")
$clientIp = $csrf.Split(".")[0..3] -join "."

$loginXml = "<DeviceInformationModel><SetValue><Authentication><UserCredential><userName>admin</userName><passwd>123456</passwd><ipaddress>$clientIp</ipaddress><DepartmentManagement isEnable='false'><departmentCode/></DepartmentManagement></UserCredential></Authentication></SetValue><Command><Login><commandNode>Authentication/UserCredential</commandNode></Login></Command></DeviceInformationModel>"

$loginResp = curl.exe -s -i -X POST http://192.168.1.12/contentwebserver -H "Content-Type: text/xml; charset=UTF-8" -H "Cookie: $cookie" -H "csrfpId: $csrf" -d "$loginXml"
$cookie2 = ($loginResp | Select-String "Set-Cookie:\s*(Session=[^;]+)").Matches.Groups[1].Value
if ($cookie2) { $cookie = $cookie2; $csrf = $cookie2.Replace("Session=", "") }

Write-Host "Logged in. Testing clearLog..."

$clearXml = @"
<DeviceInformationModel>
<SetValue overrideDelta='false'>
<LogManager><LogOperation><StartTime>0</StartTime><EndTime>0</EndTime>
<LogTableInfo><LogType>ALL</LogTableInfo></LogTableInfo>
</LogManager>
</SetValue>
<Command>
<ClearLog><commandNode>LogManager</commandNode><Params><logDetails contentType='XPath'>LogManager/LogOperation</logDetails></Params></ClearLog>
</Command>
</DeviceInformationModel>
"@

$clearResp = curl.exe -s -X POST http://192.168.1.12/contentwebserver -H "Content-Type: text/xml; charset=UTF-8" -H "Cookie: $cookie" -H "csrfpId: $csrf" -d "$clearXml"
Write-Host "Clear response: $clearResp"
