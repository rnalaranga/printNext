Add-Type -AssemblyName System.Net.Http

$handler = [System.Net.Http.HttpClientHandler]::new()
$cookieContainer = [System.Net.CookieContainer]::new()
$handler.CookieContainer = $cookieContainer
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds(15)

$ip = "192.168.1.10"

# Handshake & Login
$hs = $client.GetAsync("http://$ip/").Result
$sessionVal = ($cookieContainer.GetCookies([Uri]"http://$ip/") | Where-Object { $_.Name -eq "Session" }).Value
$clientIp = "192.168.1.100"
if ($sessionVal -match "^(\d+\.\d+\.\d+\.\d+)") { $clientIp = $matches[1] }

$loginXml = @"
<DeviceInformationModel>
<GetValue><Authentication><UserCredential></UserCredential></Authentication></GetValue>
<SetValue><Authentication><UserCredential>
<userName>admin</userName><passwd>123456</passwd><ipaddress>$clientIp</ipaddress>
<DepartmentManagement isEnable='false'><requireDepartment>false</requireDepartment></DepartmentManagement>
<domainName></domainName><applicationType>TOP_ACCESS</applicationType>
</UserCredential></Authentication></SetValue>
<Command><Login><commandNode>Authentication/UserCredential</commandNode><Params><appName>TOPACCESS</appName></Params></Login></Command>
<SaveSessionInformation><SessionInfo><Information><type>LoginPassword</type><data>123456</data></Information><Information><type>LoginUser</type><data>admin</data></Information></SessionInfo></SaveSessionInformation>
</DeviceInformationModel>
"@

$req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "http://$ip/contentwebserver")
$req.Content = [System.Net.Http.StringContent]::new($loginXml, [System.Text.Encoding]::UTF8, "text/xml")
$req.Headers.Add("csrfpId", $sessionVal)
$req.Headers.Add("Referer", "http://$ip/Administration/AdminLogin.html")
$loginResp = $client.SendAsync($req).Result
$authSession = ($cookieContainer.GetCookies([Uri]"http://$ip/") | Where-Object { $_.Name -eq "Session" }).Value
if (-not $authSession) { $authSession = $sessionVal }

$queryXml = @"
<DeviceInformationModel>
<Command>
<GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings>
</Command>
<GetValue>
<Authentication><AuthenticationSettings/></Authentication>
<Controller><Settings><AdminSystemSettings><Authentication></Authentication></AdminSystemSettings></Settings></Controller>
<Network><Services><DirectoryServices/></Services></Network>
</GetValue>
</DeviceInformationModel>
"@

$qReq = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "http://$ip/contentwebserver")
$qReq.Content = [System.Net.Http.StringContent]::new($queryXml, [System.Text.Encoding]::UTF8, "text/xml")
$qReq.Headers.Add("csrfpId", $authSession)
$qReq.Headers.Add("Referer", "http://$ip/Administration/Security/Authentication.html")

$qResp = $client.SendAsync($qReq).Result
$qBody = $qResp.Content.ReadAsStringAsync().Result
[System.IO.File]::WriteAllText("dump-10.xml", $qBody)
Write-Host "SAVED dump-10.xml, length: $($qBody.Length)"
