. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$auth = Invoke-TopAccessLogin -ip '192.168.1.10' -user 'admin' -pass '123456'
$headers = @{ 'csrfpId' = $auth.Cookie; 'Cache-Control' = 'no-cache' }

$cmds = @(
    '<Command><ChangeScreen><commandNode>Panel</commandNode><Params><screen>EWB</screen></Params></ChangeScreen></Command>',
    '<Command><AutoClear><commandNode>Panel</commandNode></AutoClear></Command>',
    '<Command><FunctionClear><commandNode>Panel</commandNode></FunctionClear></Command>',
    '<Command><Reset><commandNode>Panel</commandNode></Reset></Command>',
    '<Command><Set><commandNode>Controller</commandNode><SetValue><Controller><Settings><UI><DefaultScreen>EWB</DefaultScreen></UI></Settings></Controller></SetValue></Set></Command>'
)

foreach ($c in $cmds) {
    $xml = "<DeviceInformationModel>$c</DeviceInformationModel>"
    try {
        $r = Invoke-WebRequest -Uri 'http://192.168.1.10/contentwebserver' -Method Post -Body $xml -ContentType 'text/plain; charset=utf-8' -Headers $headers -WebSession $auth.Session -TimeoutSec 3
        Write-Host "Cmd: $($c.Substring(0, 30)) -> Status: $($r.StatusCode) Body: $($r.Content)"
    } catch {
        Write-Host "Cmd: $($c.Substring(0, 30)) -> Error: $_"
    }
}
