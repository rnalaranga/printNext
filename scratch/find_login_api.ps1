$url = "http://192.168.1.11/topaccess.e49ee3eefb40b1c28871.bundle.js"
$content = (Invoke-WebRequest -Uri $url -UseBasicParsing).Content

$matches = [regex]::Matches($content, ".{0,250}SET_LOGIN_PASSWORD_REPONSE.{0,250}")
foreach ($m in $matches) {
    Write-Host "---------------- MATCH ----------------"
    Write-Host $m.Value
}

$matches2 = [regex]::Matches($content, ".{0,250}fnHandleGetLoginPassword.{0,250}")
foreach ($m in $matches2) {
    Write-Host "---------------- MATCH 2 ----------------"
    Write-Host $m.Value
}
