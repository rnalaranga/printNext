Add-Type -AssemblyName System.Net.Http

$handler = New-Object System.Net.Http.HttpClientHandler
$cookieContainer = New-Object System.Net.CookieContainer
$handler.CookieContainer = $cookieContainer
$client = New-Object System.Net.Http.HttpClient($handler)

$pairs = New-Object 'System.Collections.Generic.List[System.Collections.Generic.KeyValuePair[string,string]]'
$pairs.Add((New-Object 'System.Collections.Generic.KeyValuePair[string,string]'('login_user_name', 'admin')))
$pairs.Add((New-Object 'System.Collections.Generic.KeyValuePair[string,string]'('login_password', '123456')))
$content = New-Object System.Net.Http.FormUrlEncodedContent($pairs)

$loginResp = $client.PostAsync('http://192.168.1.11/Login.html', $content).Result
Write-Host "Login Status: $($loginResp.StatusCode)"

$csrf = ""
$uri = New-Object System.Uri('http://192.168.1.11/')
foreach ($c in $cookieContainer.GetCookies($uri)) {
    Write-Host "Cookie: $($c.Name)=$($c.Value)"
    if ($c.Name -eq 'csrfpId') { $csrf = $c.Value }
}

$q = @"
<DeviceInformationModel>
<Command>
<GetSettings>
<commandNode>Authentication/AuthenticationSettings</commandNode>
</GetSettings>
</Command>
<GetValue>
<Authentication>
<AuthenticationSettings/>
</Authentication>
</GetValue>
</DeviceInformationModel>
"@

$req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, 'http://192.168.1.11/contentwebserver')
$req.Content = New-Object System.Net.Http.StringContent($q, [System.Text.Encoding]::UTF8, 'text/plain')
if ($csrf) {
    $req.Headers.Add('csrfpId', $csrf)
}

$cwsResp = $client.SendAsync($req).Result
$cwsBody = $cwsResp.Content.ReadAsStringAsync().Result

Write-Host "=== CWS AUTH SETTINGS ==="
[xml]$x = $cwsBody
$targets = $x.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings.AuthenticatedTarget
Write-Host "openAccess: $($x.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings.openAccess)"
Write-Host "functionwiseAuthentication: $($x.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings.functionwiseAuthentication)"
Write-Host "Target Copy: $($targets.copy)"
Write-Host "Target Scan: $($targets.scan)"
Write-Host "Target Fax: $($targets.fax)"
Write-Host "Target List: $($targets.list)"
Write-Host "Target UserFunctions: $($targets.userFunctions)"
Write-Host "Target Print: $($targets.print)"
Write-Host "Target EWB: $($targets.EWB)"

$qUi = @"
<DeviceInformationModel>
<Command>
<Get>
<Controller>
<commandNode>Controller/Settings/UI</commandNode>
</Controller>
</Get>
</Command>
</DeviceInformationModel>
"@

$reqUi = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, 'http://192.168.1.11/contentwebserver')
$reqUi.Content = New-Object System.Net.Http.StringContent($qUi, [System.Text.Encoding]::UTF8, 'text/plain')
if ($csrf) {
    $reqUi.Headers.Add('csrfpId', $csrf)
}
$cwsUiResp = $client.SendAsync($reqUi).Result
$cwsUiBody = $cwsUiResp.Content.ReadAsStringAsync().Result
Write-Host "=== CWS UI SETTINGS ==="
Write-Host $cwsUiBody
