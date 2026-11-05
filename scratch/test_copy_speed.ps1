. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$auth = Invoke-TopAccessLogin -ip '192.168.1.11' -user 'admin' -pass '123456'
$headers = @{ 'csrfpId' = $auth.Cookie; 'Cache-Control' = 'no-cache' }

function Test-OnlyCopy {
    param([bool]$lock)
    $val = if ($lock) { 'true' } else { 'false' }
    $xml = @"
<DeviceInformationModel>
  <SetValue overrideDelta='false'>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>$val</value></Payload>
  </SetValue>
  <Command>
    <AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>
  </Command>
</DeviceInformationModel>
"@
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $res = Invoke-WebRequest -Uri 'http://192.168.1.11/contentwebserver' -Method Post -Body $xml -ContentType 'text/plain; charset=utf-8' -Headers $headers -WebSession $auth.Session -TimeoutSec 15
    $sw.Stop()
    Write-Host "Lock($lock) Time: $($sw.ElapsedMilliseconds)ms, Result: $($res.Content -match 'STATUS_OK')"
}

Write-Host "Testing unlock copy only..."
Test-OnlyCopy -lock $false
Start-Sleep -Seconds 1
Write-Host "Testing lock copy only..."
Test-OnlyCopy -lock $true
