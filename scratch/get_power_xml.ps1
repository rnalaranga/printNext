. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$ip = '192.168.1.11'
$auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
$headers = @{ "csrfpId" = $auth.Cookie; "Cache-Control" = "no-cache" }

$qXml = @"
<DeviceInformationModel>
  <GetValue>
    <Controller>
      <Settings>
        <AdminSystemSettings>
          <EnergySaver/>
          <AutoClear/>
        </AdminSystemSettings>
      </Settings>
    </Controller>
  </GetValue>
</DeviceInformationModel>
"@

$res = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $qXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $auth.Session -TimeoutSec 5
$res.Content
