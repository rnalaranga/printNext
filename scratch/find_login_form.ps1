$url = "http://192.168.1.11/topaccess.e49ee3eefb40b1c28871.bundle.js"
$content = (Invoke-WebRequest -Uri $url -UseBasicParsing).Content

$matches = [regex]::Matches($content, ".{0,150}login_password.{0,150}")
foreach ($m in $matches) {
    Write-Host "---------------- MATCH ----------------"
    Write-Host $m.Value
}
