. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$auth = Invoke-TopAccessLogin -ip '192.168.1.11' -user 'admin' -pass '123456'
$headers = @{ 'csrfpId' = $auth.Cookie; 'Cache-Control' = 'no-cache' }

$xml = @"
<DeviceInformationModel>
  <Command>
    <Set>
      <commandNode>Controller</commandNode>
      <SetValue>
        <Controller><Settings><UI><DefaultScreen>EWB</DefaultScreen></UI></Settings></Controller>
      </SetValue>
    </Set>
  </Command>
</DeviceInformationModel>
"@

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$res = Invoke-WebRequest -Uri 'http://192.168.1.11/contentwebserver' -Method Post -Body $xml -ContentType 'text/plain; charset=utf-8' -Headers $headers -WebSession $auth.Session -TimeoutSec 5
$sw.Stop()
Write-Host "Set DefaultScreen to EWB on 192.168.1.11 took: $($sw.ElapsedMilliseconds)ms, Result: $($res.Content -match 'Normal')"
