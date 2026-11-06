# Study 2528A vs 08-Series
$ip28 = '192.168.1.11'
$ip08 = '192.168.1.10'

function Get-TopAccessSettings($ip) {
    Write-Host "================== IP: $ip =================="
    $session = [System.Net.Http.HttpClient]::new()
    $loginContent = New-Object System.Net.Http.FormUrlEncodedContent(
        [System.Collections.Generic.List[System.Collections.Generic.KeyValuePair[string, string]]]@(
            [System.Collections.Generic.KeyValuePair[string, string]]::new("login_user_name", "admin"),
            [System.Collections.Generic.KeyValuePair[string, string]]::new("login_password", "123456")
        )
    )
    $resp = $session.PostAsync("http://$ip/Login.html", $loginContent).Result
    $csrf = ""
    if ($resp.Headers.Contains("Set-Cookie")) {
        foreach ($c in $resp.Headers.GetValues("Set-Cookie")) {
            if ($c -match "csrfpId=([^;]+)") { $csrf = $matches[1] }
        }
    }
    Write-Host "CSRF: $csrf"

    # Query Authentication Settings
    $queryXml = @"
<DeviceInformationModel>
<Command>
<Get>
<AuthSettings>
<commandNode>Authentication/AuthenticationSettings</commandNode>
</AuthSettings>
</Get>
</Command>
</DeviceInformationModel>
"@
    $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "http://$ip/contentwebserver")
    $req.Content = [System.Net.Http.StringContent]::new($queryXml, [System.Text.Encoding]::UTF8, "text/plain")
    if ($csrf) { $req.Headers.Add("csrfpId", $csrf) }
    $res = $session.SendAsync($req).Result
    $body = $res.Content.ReadAsStringAsync().Result
    Write-Host "AUTH SETTINGS:"
    Write-Host $body

    # Query Controller UI Settings
    $queryUiXml = @"
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
    $req2 = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "http://$ip/contentwebserver")
    $req2.Content = [System.Net.Http.StringContent]::new($queryUiXml, [System.Text.Encoding]::UTF8, "text/plain")
    if ($csrf) { $req2.Headers.Add("csrfpId", $csrf) }
    $res2 = $session.SendAsync($req2).Result
    $body2 = $res2.Content.ReadAsStringAsync().Result
    Write-Host "UI SETTINGS:"
    Write-Host $body2
}

Get-TopAccessSettings -ip $ip28
Get-TopAccessSettings -ip $ip08
