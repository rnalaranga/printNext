$ip = "192.168.1.10"
$loginUrl = "http://$ip/Login.html"
$r = Invoke-WebRequest -Uri $loginUrl -Method Post -Body "login_user_name=admin&login_password=123456" -ContentType "application/x-www-form-urlencoded" -SessionVariable sess -UseBasicParsing
Write-Output "Login status: $($r.StatusCode)"

$csrf = ""
foreach ($cookie in $sess.Cookies.GetCookies((New-Object System.Uri("http://$ip/")))) {
    if ($cookie.Name -eq "csrfpId") { $csrf = $cookie.Value }
}

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

$headers = @{}
if ($csrf) { $headers["csrfpId"] = $csrf }

$res = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $queryXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $sess -UseBasicParsing
Write-Output "=== LIVE AUTH SETTINGS ON 192.168.1.10 (08-Series) ==="
Write-Output $res.Content
